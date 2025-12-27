package systems.choochoo.transit_data_archivers.gtfsrt.extensions

import com.google.protobuf.ExtensionRegistry
import com.google.transit.realtime.*

enum class GtfsRealtimeExtension {
    OBA {
        override fun registerExtension(registry: ExtensionRegistry) {
            GtfsRealtimeOneBusAway.registerAllExtensions(registry)
        }
    },
    NYCT {
        override fun registerExtension(registry: ExtensionRegistry) {
            GtfsRealtimeNYCT.registerAllExtensions(registry)
        }
    },
    LIRR {
        override fun registerExtension(registry: ExtensionRegistry) {
            GtfsRealtimeLIRR.registerAllExtensions(registry)
        }
    },
    MNR {
        override fun registerExtension(registry: ExtensionRegistry) {
            GtfsRealtimeMNR.registerAllExtensions(registry)
        }
    },
    MTARR {
        override fun registerExtension(registry: ExtensionRegistry) {
            GtfsRealtimeMTARR.registerAllExtensions(registry)
        }
    },
    LMM {
        override fun registerExtension(registry: ExtensionRegistry) {
            GtfsRealtimeServiceStatus.registerAllExtensions(registry)
        }
    },
    CROWDING {
        override fun registerExtension(registry: ExtensionRegistry) {
            GtfsRealtimeCrowding.registerAllExtensions(registry)
        }
    };

    abstract fun registerExtension(registry: ExtensionRegistry)
}

