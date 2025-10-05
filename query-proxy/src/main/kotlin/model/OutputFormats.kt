package systems.choochoo.transit_data_archivers.query_proxy.model

import com.clickhouse.client.api.query.QuerySettings
import com.clickhouse.data.ClickHouseFormat
import com.clickhouse.data.ClickHouseFormat.*
import io.javalin.http.ContentType

@Suppress("unused")
enum class OutputFormats(
    val format: ClickHouseFormat,
    val settings: QuerySettings = QuerySettings(),
    val contentType: String
) {
    JSON_LINES(
        JSONEachRow,
        QuerySettings()
            .serverSetting("output_format_json_quote_64bit_integers", "0"),
        "application/x-ndjson"
    ),
    JSON_ARRAY_OF_OBJECTS(
        JSONEachRow,
        QuerySettings()
            .serverSetting("output_format_json_array_of_rows", "1")
            .serverSetting("output_format_json_quote_64bit_integers", "0"),
        ContentType.JSON
    ),
    CSV(
        CSVWithNames,
        contentType = ContentType.TEXT_CSV.mimeType
    ),
    ARROW(
        Arrow,
        QuerySettings()
            .serverSetting("output_format_arrow_compression_method", "none"),
        "application/vnd.apache.arrow.file"
    ),
    TEXT(
        RawBLOB,
        QuerySettings().serverSetting("limit", "1"),
        ContentType.PLAIN
    )
}