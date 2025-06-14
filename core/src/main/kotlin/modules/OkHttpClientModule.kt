package systems.choochoo.transit_data_archivers.core.modules

import com.google.common.net.HttpHeaders.USER_AGENT
import dagger.Module
import dagger.Provides
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Singleton
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import systems.choochoo.transit_data_archivers.core.configuration.ApplicationVersion
import systems.choochoo.transit_data_archivers.core.configuration.ConfigurationCore
import systems.choochoo.transit_data_archivers.core.utils.constructUserAgentString
import java.net.CookieManager
import java.net.CookiePolicy
import kotlin.time.toJavaDuration

private val log = KotlinLogging.logger {}

@Module
class OkHttpClientModule() {
    companion object {
        @Provides
        @Singleton
        fun provideClient(configuration: ConfigurationCore, appVersion: ApplicationVersion): OkHttpClient {
            val cookieManager = CookieManager()
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
            val jar = JavaNetCookieJar(cookieManager)

            val userAgentString = constructUserAgentString(appVersion, configuration.operatorContact, OkHttp.VERSION)

            log.info { "We will identify ourselves with the following User-Agent: $userAgentString" }

            return OkHttpClient.Builder()
                .cookieJar(jar)
                .callTimeout(configuration.callTimeout.toJavaDuration())
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