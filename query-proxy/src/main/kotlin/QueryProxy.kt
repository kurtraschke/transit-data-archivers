package systems.choochoo.transit_data_archivers.query_proxy

import com.clickhouse.client.api.Client
import com.clickhouse.client.api.query.QuerySettings
import dagger.BindsInstance
import dagger.Component
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.Javalin
import io.javalin.http.BadGatewayResponse
import io.javalin.http.Header.X_FORWARDED_FOR
import io.javalin.http.NotFoundResponse
import io.javalin.http.pathParamAsClass
import io.javalin.http.util.NaiveRateLimit
import io.javalin.micrometer.MicrometerPlugin
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.exporter.servlet.jakarta.PrometheusMetricsServlet
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.datetime.LocalDate
import org.eclipse.jetty.servlet.ServletHolder
import systems.choochoo.transit_data_archivers.common.configuration.ApplicationVersion
import systems.choochoo.transit_data_archivers.common.modules.ApplicationVersionModule
import systems.choochoo.transit_data_archivers.common.modules.ClickHouseClientModule
import systems.choochoo.transit_data_archivers.common.modules.MicrometerModule
import systems.choochoo.transit_data_archivers.query_proxy.modules.ConfigurationModule
import java.util.concurrent.CountDownLatch

private val log = KotlinLogging.logger {}

@Component(
    modules = [
        ApplicationVersionModule::class,
        ClickHouseClientModule::class,
        ConfigurationModule::class,
        MicrometerModule::class,
    ]
)
@Singleton
internal interface QueryProxyFactory {
    fun proxy(): QueryProxy
    fun appVersion(): ApplicationVersion
    fun prometheusMeterRegistry(): PrometheusMeterRegistry

    @Component.Builder
    interface Builder {
        @BindsInstance
        fun host(@Named("host") host: String): Builder

        @BindsInstance
        fun port(@Named("port") port: Int): Builder

        @BindsInstance
        fun configuration(configuration: Configuration): Builder

        @BindsInstance
        fun shutdownLatch(shutdownLatch: CountDownLatch): Builder

        fun build(): QueryProxyFactory
    }
}

internal class QueryProxy @Inject constructor(
    @param:Named("host") private val host: String,
    @param:Named("port") private val port: Int,
    configuration: Configuration,
    client: Client,
    shutdownLatch: CountDownLatch,
    prometheusMeterRegistry: PrometheusMeterRegistry,
) {
    val app: Javalin = Javalin
        .create { config ->
            config.http.generateEtags = true
            config.http.brotliAndGzipCompression()

            config.validation.register(Boolean::class.java) { it.toBooleanStrict() }
            config.validation.register(UByte::class.java) { it.toUByte() }
            config.validation.register(UShort::class.java) { it.toUShort() }
            config.validation.register(UInt::class.java) { it.toUInt() }
            config.validation.register(ULong::class.java) { it.toULong() }
            config.validation.register(Byte::class.java) { it.toByte() }
            config.validation.register(Short::class.java) { it.toShort() }
            config.validation.register(Int::class.java) { it.toInt() }
            config.validation.register(Long::class.java) { it.toLong() }
            config.validation.register(LocalDate::class.java) { LocalDate.parse(it, LocalDate.Formats.ISO) }

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
        .get("/query/{queryName}") { ctx ->
            NaiveRateLimit.requestPerTimeUnit(ctx, configuration.rateLimit.requests, configuration.rateLimit.unit)

            val queryName = ctx.pathParamAsClass<String>("queryName").get()

            val queryConfiguration = configuration.queries[queryName]

            if (queryConfiguration != null) {
                val parameters = queryConfiguration.parameters.entries
                    .associate { (k, v) -> k to ctx.queryParamAsClass(k, v.dataType.typeClass.java).get() }

                val settings = QuerySettings()
                settings.format = queryConfiguration.outputFormat.format
                settings.serverSetting("readonly", "1")
                settings.httpHeader(X_FORWARDED_FOR, ctx.header(X_FORWARDED_FOR)?.split(",")?.get(0) ?: ctx.ip())
                queryConfiguration.clientSettings.forEach {
                    settings.setOption(it.key, it.value)
                }
                queryConfiguration.serverSettings.forEach {
                    settings.serverSetting(it.key, it.value)
                }

                ctx.future {
                    client.query(
                        queryConfiguration.queryText,
                        parameters,
                        QuerySettings.merge(settings, queryConfiguration.outputFormat.settings)
                    )
                        .thenAccept { result ->
                            ctx
                                .contentType(queryConfiguration.outputFormat.contentType)
                                .result(result.inputStream)
                        }
                        .exceptionally { throwable ->
                            log.error(throwable) { "ClickHouse query failed" }
                            throw BadGatewayResponse()
                        }
                }
            } else {
                throw NotFoundResponse("Query not found")
            }
        }

    fun start() {
        app.start(host, port)
    }

    fun stop() {
        app.stop()
    }
}