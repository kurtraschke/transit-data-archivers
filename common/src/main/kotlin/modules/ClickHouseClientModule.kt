package systems.choochoo.transit_data_archivers.common.modules

import com.clickhouse.client.api.Client
import dagger.Module
import dagger.Provides
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.inject.Singleton
import systems.choochoo.transit_data_archivers.common.configuration.ApplicationVersion
import systems.choochoo.transit_data_archivers.common.configuration.HasDatabaseConfiguration
import systems.choochoo.transit_data_archivers.common.configuration.HasOperatorContact
import systems.choochoo.transit_data_archivers.common.utils.constructUserAgentString

private val log = KotlinLogging.logger {}

@Module
class ClickHouseClientModule() {
    companion object {
        @Provides
        @Singleton
        fun provideClient(hdc: HasDatabaseConfiguration, hoc: HasOperatorContact, appVersion: ApplicationVersion): Client {
            val userAgentString = constructUserAgentString(appVersion, hoc.operatorContact)
            val database = hdc.database

            val client = Client.Builder()
                .addEndpoint(database.url.toString())
                .setClientName(userAgentString)
                .setOptions(database.options)
                .serverSetting("async_insert", "1")
                .serverSetting("wait_for_async_insert", "1")
                .serverSetting("input_format_try_infer_dates", "0")
                .serverSetting("input_format_try_infer_datetimes", "0")
                .setUsername(database.username)
                .setPassword(database.password.value)
                .build()

            if (!client.ping()) {
                log.warn { "Could not ping ClickHouse server; writes may fail" }
            }

            return client
        }
    }
}