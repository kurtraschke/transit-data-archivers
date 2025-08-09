package systems.choochoo.transit_data_archivers.njt.model

import com.fasterxml.jackson.annotation.JsonProperty

internal data class AuthenticateUserResponse(
    @param:JsonProperty("Authenticated")
    val authenticated: Boolean?,
    @param:JsonProperty("UserToken")
    val token: String?,
    val errorMessage: String?
)