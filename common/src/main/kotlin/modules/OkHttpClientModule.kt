package systems.choochoo.transit_data_archivers.common.modules

import com.google.common.net.HttpHeaders.USER_AGENT
import dagger.Module
import dagger.Provides
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Singleton
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import okhttp3.java.net.cookiejar.JavaNetCookieJar
import systems.choochoo.transit_data_archivers.common.configuration.ApplicationVersion
import systems.choochoo.transit_data_archivers.common.configuration.HasCallTimeout
import systems.choochoo.transit_data_archivers.common.configuration.HasOperatorContact
import systems.choochoo.transit_data_archivers.common.utils.constructUserAgentString
import java.net.CookieHandler

private val log = KotlinLogging.logger {}

@Module
class OkHttpClientModule() {
    companion object {
        @Provides
        @Singleton
        fun provideClient(
            hoc: HasOperatorContact,
            hct: HasCallTimeout,
            appVersion: ApplicationVersion,
            cookieHandler: CookieHandler
        ): OkHttpClient {
            val jar = JavaNetCookieJar(cookieHandler)

            val userAgentString = constructUserAgentString(appVersion, hoc.operatorContact, OkHttp.VERSION)

            log.info { "We will identify ourselves with the following User-Agent: $userAgentString" }

            return OkHttpClient.Builder()
                .cookieJar(jar)
                .callTimeout(hct.callTimeout)
                .addInterceptor(BrotliInterceptor)
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