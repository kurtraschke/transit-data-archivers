package systems.choochoo.transit_data_archivers.core.utils

import com.clickhouse.client.api.Client
import java.util.concurrent.TimeUnit
import kotlin.use

fun getRowCount(client: Client, table: String): Int {
    val query = "SELECT COUNT(*) FROM {table_name:Identifier}"

    client
        .query(query, mapOf("table_name" to table))
        .get(3, TimeUnit.SECONDS)
        .use { response ->
            client.newBinaryFormatReader(response)
                .use {
                    it.next()
                    return it.getInteger(1)
                }
        }
}
