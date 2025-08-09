package systems.choochoo.transit_data_archivers.njt.services

import com.google.transit.realtime.GtfsRealtime.FeedMessage
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import systems.choochoo.transit_data_archivers.njt.model.AuthenticateUserResponse
import systems.choochoo.transit_data_archivers.njt.model.ValidTokenResponse

internal interface RailRealtimeService : RealtimeService {
    @FormUrlEncoded
    @POST("api/GTFSRT/getToken")
    override fun authenticateUser(
        @Field("username") username: String,
        @Field("password") password: String
    ): Call<AuthenticateUserResponse>

    @FormUrlEncoded
    @POST("api/GTFSRT/isValidToken")
    fun isValidToken(@Field("token") token: String): Call<ValidTokenResponse>

    @FormUrlEncoded
    @POST("api/GTFSRT/getGTFS")
    override fun getGTFS(@Field("token") token: String): Call<ResponseBody>

    @FormUrlEncoded
    @POST("api/GTFSRT/getAlerts")
    override fun getAlerts(@Field("token") token: String): Call<FeedMessage>

    @FormUrlEncoded
    @POST("api/GTFSRT/getTripUpdates")
    override fun getTripUpdates(@Field("token") token: String): Call<FeedMessage>

    @FormUrlEncoded
    @POST("api/GTFSRT/getVehiclePositions")
    override fun getVehiclePositions(@Field("token") token: String): Call<FeedMessage>
}
