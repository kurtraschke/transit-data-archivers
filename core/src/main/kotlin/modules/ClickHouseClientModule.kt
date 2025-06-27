package systems.choochoo.transit_data_archivers.core.modules

import com.clickhouse.client.api.Client
import dagger.Module
import dagger.Provides
import jakarta.inject.Singleton
import systems.choochoo.transit_data_archivers.core.configuration.ApplicationVersion
import systems.choochoo.transit_data_archivers.core.configuration.ConfigurationCore
import systems.choochoo.transit_data_archivers.core.utils.constructUserAgentString

@Module
class ClickHouseClientModule() {
    companion object {
        @Provides
        @Singleton
        fun provideClient(configuration: ConfigurationCore, appVersion: ApplicationVersion): Client {
            val userAgentString = constructUserAgentString(appVersion, configuration.operatorContact)
            val database = configuration.database

            val client = Client.Builder()
                .addEndpoint(database.url.toString())
                .setClientName(userAgentString)
                .setOptions(database.options)
                .serverSetting("async_insert", "1")
                .serverSetting("wait_for_async_insert", "1")
                .serverSetting("input_format_try_infer_datetimes", "0")
                .setUsername(database.username)
                .setPassword(database.password?.value ?: "")
                .build()

            if (!client.ping()) {
                throw RuntimeException("Could not ping ClickHouse server")
            }

            return client
        }
    }
}