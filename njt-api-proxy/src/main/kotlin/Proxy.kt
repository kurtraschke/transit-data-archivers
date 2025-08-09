package systems.choochoo.transit_data_archivers.njt


import com.fasterxml.jackson.core.exc.StreamReadException
import com.fasterxml.jackson.databind.DatabindException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.RuntimeJsonMappingException
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.net.MediaType
import dagger.BindsInstance
import dagger.Component
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.Javalin
import io.javalin.http.*
import io.javalin.json.JavalinJackson
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import systems.choochoo.transit_data_archivers.common.modules.ApplicationVersionModule
import systems.choochoo.transit_data_archivers.common.modules.CookieHandlerModule
import systems.choochoo.transit_data_archivers.common.modules.OkHttpClientModule
import systems.choochoo.transit_data_archivers.njt.model.*
import systems.choochoo.transit_data_archivers.njt.model.OutputFormat.*
import systems.choochoo.transit_data_archivers.njt.modules.ConfigurationModule
import systems.choochoo.transit_data_archivers.njt.modules.ObjectMapperModule
import systems.choochoo.transit_data_archivers.njt.services.MetaRealtimeService
import systems.choochoo.transit_data_archivers.njt.utils.filterInvalidEntities
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CountDownLatch

private val log = KotlinLogging.logger {}

@Component(
    modules = [
        ApplicationVersionModule::class,
        ConfigurationModule::class,
        CookieHandlerModule::class,
        ObjectMapperModule::class,
        OkHttpClientModule::class,
    ]
)
@Singleton
internal interface ProxyFactory {
    fun proxy(): Proxy

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

const val INVALID_TOKEN_ERROR = "Invalid token."

internal class Proxy @Inject constructor(
    @param:Named("host") private val host: String,
    @param:Named("port") private val port: Int,
    s: MetaRealtimeService,
    ptc: PersistentTokenCache,
    private val om: ObjectMapper,
    shutdownLatch: CountDownLatch,
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
            ctx.result(token.token)
        }
        .get("/{environment}/{mode}/proxy/gtfs") { ctx ->
            val environment = ctx.pathParamAsClass<Environment>("environment").get()
            val mode = ctx.pathParamAsClass<Mode>("mode").get()

            val token = ptc.get(TokenKey(environment, mode))

            val response = s.get(environment, mode).getGTFS(token.token).execute()

            if (response.isSuccessful) {
                val filename = "${environment}-${mode}-GTFS.zip"

                ctx.header("Content-Disposition", "attachment; filename=\"$filename\"")
                ctx.contentType(ContentType.APPLICATION_ZIP)
                ctx.result(response.body()!!.byteStream())

            } else {
                val errorMessage = parseErrorMessage(response.errorBody()?.bytes())

                if (errorMessage == "Invalid token.") {
                    log.warn { "Invalidating token for environment $environment and mode $mode due to upstream response." }
                    ptc.invalidate(TokenKey(environment, mode))
                }

                throw BadGatewayResponse(errorMessage ?: "")
            }
        }
        .get("/{environment}/{mode}/proxy/{feed}") { ctx ->
            val environment = ctx.pathParamAsClass<Environment>("environment").get()
            val mode = ctx.pathParamAsClass<Mode>("mode").get()
            val feed = ctx.pathParamAsClass<Feed>("feed").get()
            val format = ctx.queryParamAsClass<OutputFormat>("format").getOrDefault(PROTOBUF)
            val filterInvalidEntities = ctx.queryParamAsClass<Boolean>("filterInvalidEntities").getOrDefault(true)

            val token = ptc.get(TokenKey(environment, mode))

            val response = feed.requestFunction(s.get(environment, mode), token.token).execute()

            if (response.isSuccessful && response.body() != null) {
                val fm = response.body()!!
                    .let {
                        if (filterInvalidEntities) {
                            filterInvalidEntities(it)
                        } else {
                            it
                        }
                    }

                val filename = "${environment}-${mode}-${feed}.${format.extension}"

                ctx.header("Content-Disposition", "attachment; filename=\"$filename\"")

                when (format) {
                    PROTOBUF -> {
                        ctx.contentType(MediaType.PROTOBUF)
                        ctx.result(fm.toByteArray())
                    }

                    PBTEXT -> {
                        ctx.contentType(ContentType.TEXT_PLAIN)
                        ctx.result(fm.toString())
                    }

                    JSON -> {
                        ctx.json(fm)
                    }
                }

            } else {
                val errorMessage = parseErrorMessage(response.errorBody()?.bytes())

                if (errorMessage == INVALID_TOKEN_ERROR) {
                    log.warn { "Invalidating token for environment $environment and mode $mode due to upstream response." }
                    ptc.invalidate(TokenKey(environment, mode))
                }

                throw BadGatewayResponse(errorMessage ?: "")
            }
        }

    private fun parseErrorMessage(errorBody: ByteArray?): String? = errorBody?.let {
        try {
            om.readValue<ErrorMessage>(it)
        } catch (_: RuntimeJsonMappingException) {
            null
        } catch (_: IOException) {
            null
        } catch (_: StreamReadException) {
            null
        } catch (_: DatabindException) {
            null
        }?.errorMessage
    }

    fun start() {
        app.start(host, port)
    }

    fun stop() {
        app.stop()
    }

}

fun Context.contentType(mt: MediaType): Context {
    return this.contentType(mt.toString())
}