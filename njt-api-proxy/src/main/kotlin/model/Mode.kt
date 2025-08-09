package systems.choochoo.transit_data_archivers.njt.model

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import systems.choochoo.transit_data_archivers.njt.model.Environment.PRODUCTION
import systems.choochoo.transit_data_archivers.njt.model.Environment.TEST
import systems.choochoo.transit_data_archivers.njt.services.BusRealtimeService
import systems.choochoo.transit_data_archivers.njt.services.RailRealtimeService
import systems.choochoo.transit_data_archivers.njt.services.RealtimeService
import kotlin.reflect.KClass

internal enum class Mode(val serviceClass: KClass<out RealtimeService>) {
    BUS(BusRealtimeService::class) {
        override fun baseUrlForEnvironment(environment: Environment): HttpUrl = when (environment) {
            TEST -> "https://testpcsdata.njtransit.com/".toHttpUrl()
            PRODUCTION -> "https://pcsdata.njtransit.com/".toHttpUrl()
        }
    },
    RAIL(RailRealtimeService::class) {
        override fun baseUrlForEnvironment(environment: Environment): HttpUrl = when(environment) {
            TEST -> "https://testraildata.njtransit.com/".toHttpUrl()
            PRODUCTION -> "https://raildata.njtransit.com/".toHttpUrl()
        }
    };

    abstract fun baseUrlForEnvironment(environment: Environment): HttpUrl
}