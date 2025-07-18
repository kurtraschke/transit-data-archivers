package systems.choochoo.transit_data_archivers.common.utils

import com.clickhouse.client.api.Client

fun getRowCount(client: Client, table: String): Int {
    val query = "SELECT COUNT(*) FROM {table_name:Identifier}"

    client
        .query(query, mapOf("table_name" to table))
        .get()
        .use { response ->
            client.newBinaryFormatReader(response)
                .use {
                    it.next()
                    return it.getInteger(1)
                }
        }
}

val clickhouseImageName = (System.getenv("CI_DEPENDENCY_PROXY_GROUP_IMAGE_PREFIX")?.let { "$it/" } ?: "") + "clickhouse/clickhouse-server"