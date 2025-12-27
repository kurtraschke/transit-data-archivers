package systems.choochoo.transit_data_archivers.common.modules

import com.google.common.net.HttpHeaders.USER_AGENT
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import io.github.oshai.kotlinlogging.KotlinLogging
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.okhttp3.OkHttpMetricsEventListener
import jakarta.inject.Singleton
import okhttp3.CompressionInterceptor
import okhttp3.Gzip
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.brotli.Brotli
import okhttp3.java.net.cookiejar.JavaNetCookieJar
import okhttp3.zstd.Zstd
import systems.choochoo.transit_data_archivers.common.configuration.ApplicationVersion
import systems.choochoo.transit_data_archivers.common.configuration.HasCallTimeout
import systems.choochoo.transit_data_archivers.common.configuration.HasOperatorContact
import systems.choochoo.transit_data_archivers.common.utils.constructUserAgentString
import java.net.CookieHandler
import java.util.*


private val log = KotlinLogging.logger {}

@Module
abstract class OkHttpClientModule {
    @BindsOptionalOf
    abstract fun bindOptionalMeterRegistry(): MeterRegistry

    companion object {
        @Provides
        @Singleton
        fun provideClient(
            hoc: HasOperatorContact,
            hct: HasCallTimeout,
            appVersion: ApplicationVersion,
            cookieHandler: CookieHandler,
            meterRegistry: Optional<MeterRegistry>
        ): OkHttpClient {
            val jar = JavaNetCookieJar(cookieHandler)

            val userAgentString = constructUserAgentString(appVersion, hoc.operatorContact, OkHttp.VERSION)

            log.info { "We will identify ourselves with the following User-Agent: $userAgentString" }

            return OkHttpClient.Builder()
                .cookieJar(jar)
                .callTimeout(hct.callTimeout)
                .addInterceptor(CompressionInterceptor(Zstd, Brotli, Gzip))
                .apply {
                    if (meterRegistry.isPresent) {
                        eventListener(
                            OkHttpMetricsEventListener.builder(meterRegistry.get(), "okhttp.requests")
                                .uriMapper { req -> req.url.redact() }
                                .build()
                        )
                    }
                }
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request()
                            .newBuilder()
                            .header(USER_AGENT, userAgentString)
                            .build()
                    )
                }
                .build()
        }
    }
}