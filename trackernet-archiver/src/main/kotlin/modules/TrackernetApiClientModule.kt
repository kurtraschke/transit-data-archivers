@file:Suppress("UnstableApiUsage")

package systems.choochoo.transit_data_archivers.trackernet.modules

import com.google.common.util.concurrent.RateLimiter
import dagger.Module
import dagger.Provides
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Singleton
import jakarta.xml.bind.JAXBContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.jaxb3.JaxbConverterFactory
import systems.choochoo.transit_data_archivers.trackernet.Configuration
import systems.choochoo.transit_data_archivers.trackernet.services.TrackernetService
import uk.co.lul.trackernet.predictiondetail.PredictionDetail
import uk.co.lul.trackernet.predictionsummary.PredictionSummary
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

@Module
internal class TrackernetApiClientModule() {
    companion object {
        @Provides
        @Singleton
        fun provideTrackernetApiClient(
            configuration: Configuration,
            okHttpClient: OkHttpClient
        ): TrackernetService {
            @Suppress("UnstableApiUsage")
            val rateLimiter =
                RateLimiter.create((configuration.maxRequestsPerMinute * configuration.derateFactor.v) / 1.minutes.inWholeSeconds)

            val jc = JAXBContext.newInstance(PredictionSummary::class.java, PredictionDetail::class.java)

            val r = Retrofit.Builder()
                .baseUrl(configuration.baseUrl)
                .client(
                    okHttpClient.newBuilder()
                        .addInterceptor { chain ->
                            val delayedBy = rateLimiter.acquire(1)
                            if (delayedBy > 0.0) {
                                logger.trace { "Request to ${chain.request().url} delayed by $delayedBy seconds for rate-limiting." }
                            }
                            chain.proceed(chain.request())
                        }
                        .addInterceptor { chain ->
                            chain.proceed(
                                chain.request()
                                    .newBuilder()
                                    .url(
                                        chain
                                            .request()
                                            .url
                                            .newBuilder()
                                            .addQueryParameter("app_key", configuration.appKey.value)
                                            .build()
                                    )
                                    .build()
                            )
                        }
                        .build()
                )
                .addConverterFactory(JaxbConverterFactory.create(jc))
                .build()

            return r.create(TrackernetService::class.java)
        }
    }
}