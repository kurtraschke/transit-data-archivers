@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.njt


import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.net.MediaType
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import dagger.BindsInstance
import dagger.Component
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.Javalin
import io.javalin.http.BadGatewayResponse
import io.javalin.http.ContentType
import io.javalin.http.Header.CONTENT_DISPOSITION
import io.javalin.http.Header.EXPIRES
import io.javalin.http.Header.LAST_MODIFIED
import io.javalin.http.pathParamAsClass
import io.javalin.http.queryParamAsClass
import io.javalin.json.JavalinJackson
import io.javalin.micrometer.MicrometerPlugin
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.exporter.servlet.jakarta.PrometheusMetricsServlet
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.eclipse.jetty.servlet.ServletHolder
import retrofit2.HttpException
import systems.choochoo.transit_data_archivers.common.configuration.ApplicationVersion
import systems.choochoo.transit_data_archivers.common.modules.ApplicationVersionModule
import systems.choochoo.transit_data_archivers.common.modules.CookieHandlerModule
import systems.choochoo.transit_data_archivers.common.modules.MicrometerModule
import systems.choochoo.transit_data_archivers.common.modules.OkHttpClientModule
import systems.choochoo.transit_data_archivers.common.utils.toHttpDateString
import systems.choochoo.transit_data_archivers.njt.model.*
import systems.choochoo.transit_data_archivers.njt.model.OutputFormat.*
import systems.choochoo.transit_data_archivers.njt.modules.ConfigurationModule
import systems.choochoo.transit_data_archivers.njt.modules.ObjectMapperModule
import systems.choochoo.transit_data_archivers.njt.services.MetaRealtimeService
import systems.choochoo.transit_data_archivers.njt.utils.*
import java.nio.file.Path
import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import kotlin.time.ExperimentalTime

private val log = KotlinLogging.logger {}

@Component(
    modules = [
        ApplicationVersionModule::class,
        ConfigurationModule::class,
        CookieHandlerModule::class,
        MicrometerModule::class,
        ObjectMapperModule::class,
        OkHttpClientModule::class,
    ]
)
@Singleton
internal interface ProxyFactory {
    fun proxy(): Proxy
    fun appVersion(): ApplicationVersion
    fun prometheusMeterRegistry(): PrometheusMeterRegistry

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun host(@Named("host") host: String): Builder

        @BindsInstance
        fun port(@Named("port") port: Int): Builder

        @BindsInstance
        fun dataPath(@Named("dataPath") dataPath: Path): Builder

        @BindsInstance
        fun username(@Named("username") username: String): Builder

        @BindsInstance
        fun password(@Named("password") password: String): Builder

        @BindsInstance
        fun operatorContact(@Named("operatorContact") operatorContact: String?): Builder

        @BindsInstance
        fun shutdownLatch(shutdownLatch: CountDownLatch): Builder

        fun build(): ProxyFactory
    }
}

internal class Proxy @Inject constructor(
    @param:Named("host") private val host: String,
    @param:Named("port") private val port: Int,
    s: MetaRealtimeService,
    private val ptc: PersistentTokenCache,
    private val om: ObjectMapper,
    shutdownLatch: CountDownLatch,
    prometheusMeterRegistry: PrometheusMeterRegistry,
) {
    val app: Javalin = Javalin
        .create { config ->
            config.http.generateEtags = true
            config.http.brotliAndGzipCompression()

            config.validation.register(Environment::class.java) { Environment.valueOf(it.uppercase()) }
            config.validation.register(Mode::class.java) { Mode.valueOf(it.uppercase()) }
            config.validation.register(Feed::class.java) { Feed.valueOf(it.uppercase()) }
            config.validation.register(OutputFormat::class.java) { OutputFormat.valueOf(it.uppercase()) }

            config.jsonMapper(JavalinJackson(om))

            config.registerPlugin(MicrometerPlugin { micrometerPluginConfig ->
                micrometerPluginConfig.registry = prometheusMeterRegistry
            })

            config.jetty.modifyServletContextHandler { handler ->
                val prometheusMetricsServlet =
                    ServletHolder(PrometheusMetricsServlet(prometheusMeterRegistry.prometheusRegistry))

                handler.addServlet(prometheusMetricsServlet, "/metrics")
            }
        }
        .events { events ->
            events.serverStopped {
                shutdownLatch.countDown()
            }
        }
        .get("/{environment}/{mode}/token") { ctx ->
            val environment = ctx.pathParamAsClass<Environment>("environment").get()
            val mode = ctx.pathParamAsClass<Mode>("mode").get()

            val token = ptc.get(TokenKey(environment, mode))

            ctx.contentType(ContentType.TEXT_PLAIN)
            ctx.header(LAST_MODIFIED, token.whenObtained.toHttpDateString())
            ctx.header(EXPIRES, (token.whenObtained + TOKEN_LIFETIME).toHttpDateString())
            ctx.result(token.token)
        }
        .get("/{environment}/{mode}/proxy/gtfs") { ctx ->
            val environment = ctx.pathParamAsClass<Environment>("environment").get()
            val mode = ctx.pathParamAsClass<Mode>("mode").get()

            val token = ptc.get(TokenKey(environment, mode))

            ctx.future {
                s.get(environment, mode).getGTFS(token.token)
                    .thenAccept { response ->
                        val filename = "${environment}-${mode}-GTFS.zip"

                        ctx.header(CONTENT_DISPOSITION, """attachment; filename="$filename"""")
                            .contentType(ContentType.APPLICATION_ZIP)
                            .result(response.byteStream())
                    }
                    .exceptionally { throwable ->
                        handleException(throwable, environment, mode)
                    }
            }
        }
        .get("/{environment}/{mode}/proxy/{feed}") { ctx ->
            val environment = ctx.pathParamAsClass<Environment>("environment").get()
            val mode = ctx.pathParamAsClass<Mode>("mode").get()
            val feed = ctx.pathParamAsClass<Feed>("feed").get()
            val format = ctx.queryParamAsClass<OutputFormat>("format").getOrDefault(PROTOBUF)

            val token = ptc.get(TokenKey(environment, mode))

            ctx.future {
                feed.requestFunction(s.get(environment, mode), token.token)
                    .thenAccept { response ->
                        val filename = "${environment}-${mode}-${feed}.${format.extension}"

                        ctx.header(CONTENT_DISPOSITION, """attachment; filename="$filename"""")

                        when (format) {
                            PROTOBUF -> {
                                ctx.contentType(MediaType.PROTOBUF)
                                    .result(response.byteStream())
                            }

                            PBTEXT -> {
                                ctx.contentType(ContentType.TEXT_PLAIN)
                                    .result(FeedMessage.parser().parsePartialFrom(response.byteStream()).toString())
                            }

                            JSON -> {
                                ctx.jsonStream(FeedMessage.parser().parsePartialFrom(response.byteStream()))
                            }
                        }
                    }
                    .exceptionally { throwable ->
                        handleException(throwable, environment, mode)
                    }
            }

        }

    private fun handleException(throwable: Throwable, environment: Environment, mode: Mode): Nothing {
        log.error(throwable) { "Upstream HTTP error" }

        val errorMessage = parseErrorMessage(om, extractErrorBody(throwable))

        if (errorMessage == INVALID_TOKEN_ERROR) {
            log.warn { "Invalidating token for environment $environment and mode $mode due to upstream response." }
            ptc.invalidate(TokenKey(environment, mode))
        }

        if (errorMessage != null) {
            throw BadGatewayResponse(errorMessage)
        } else {
            throw BadGatewayResponse(throwable.stackTraceToString())
        }
    }

    private fun extractErrorBody(throwable: Throwable): ByteArray? {
        return ((throwable as? CompletionException)?.cause as? HttpException)?.response()?.errorBody()?.bytes()
    }

    fun start() {
        app.start(host, port)
    }

    fun stop() {
        app.stop()
    }
}