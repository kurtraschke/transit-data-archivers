package systems.choochoo.transit_data_archivers.common.modules

import dagger.Binds
import dagger.Module
import dagger.Provides
import jakarta.inject.Singleton
import java.net.CookieHandler
import java.net.CookieManager

@Module
abstract class CookieHandlerModule {
    @Binds
    abstract fun cookieHandler(cookieManager: CookieManager): CookieHandler

    companion object {
        @Provides
        @Singleton
        fun provideCookieManager() = CookieManager()
    }
}