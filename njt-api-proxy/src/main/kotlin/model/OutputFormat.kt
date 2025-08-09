package systems.choochoo.transit_data_archivers.njt.model

internal enum class OutputFormat(val extension: String) {
    PROTOBUF("pb"),
    PBTEXT("pbtext"),
    JSON("json")
}