package systems.choochoo.transit_data_archivers.njt.modules

import dagger.Module
import dagger.Provides
import jakarta.inject.Named
import jakarta.inject.Singleton
import systems.choochoo.transit_data_archivers.common.configuration.HasCallTimeout
import systems.choochoo.transit_data_archivers.common.configuration.HasOperatorContact
import kotlin.time.Duration.Companion.seconds

@Module
internal class ConfigurationModule {
    companion object {
        @Provides
        @Singleton
        fun provideHasCallTimeout(): HasCallTimeout = object : HasCallTimeout {
            override val callTimeout = 15.seconds
        }

        @Provides
        @Singleton
        fun provideHasOperatorContact(@Named("operatorContact") operatorContact: String?): HasOperatorContact =
            object : HasOperatorContact {
                override val operatorContact = operatorContact
            }
    }
}