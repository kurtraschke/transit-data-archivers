package systems.choochoo.transit_data_archivers.njt.model

internal data class ValidTokenResponse(
    val validToken: Boolean?,
    val userID: String?,
    val errorMessage: String?
)
