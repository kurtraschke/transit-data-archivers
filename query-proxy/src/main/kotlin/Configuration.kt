package systems.choochoo.transit_data_archivers.query_proxy

import systems.choochoo.transit_data_archivers.common.configuration.DatabaseConfiguration
import systems.choochoo.transit_data_archivers.common.configuration.HasDatabaseConfiguration
import systems.choochoo.transit_data_archivers.common.configuration.HasOperatorContact
import systems.choochoo.transit_data_archivers.query_proxy.model.DataTypes
import systems.choochoo.transit_data_archivers.query_proxy.model.OutputFormats

internal data class Configuration(
    override val database: DatabaseConfiguration = DatabaseConfiguration(),
    override val operatorContact: String?,
    val queries: Map<String, Query>
) : HasDatabaseConfiguration, HasOperatorContact

internal data class Query(
    val queryText: String,
    val outputFormat: OutputFormats,
    val clientSettings: Map<String, String> = emptyMap(),
    val serverSettings: Map<String, String> = emptyMap(),
    val parameters: Map<String, QueryParameter> = emptyMap()
)

internal data class QueryParameter(
    val dataType: DataTypes
)