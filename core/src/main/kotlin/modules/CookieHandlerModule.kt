package systems.choochoo.transit_data_archivers.core.modules

import dagger.Binds
import dagger.Module
import dagger.Provides
import java.net.CookieHandler
import java.net.CookieManager

@Module
abstract class CookieHandlerModule {
    @Binds
    abstract fun cookieHandler(cookieManager: CookieManager): CookieHandler

    companion object {
        @Provides
        fun provideCookieManager() = CookieManager()
    }
}