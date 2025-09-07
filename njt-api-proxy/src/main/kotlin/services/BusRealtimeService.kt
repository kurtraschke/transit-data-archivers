package systems.choochoo.transit_data_archivers.njt.services

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import systems.choochoo.transit_data_archivers.njt.model.AuthenticateUserResponse
import java.util.concurrent.CompletableFuture

internal interface BusRealtimeService : RealtimeService {
    @FormUrlEncoded
    @POST("api/GTFS/authenticateUser")
    override fun authenticateUser(
        @Field("username") username: String,
        @Field("password") password: String
    ): CompletableFuture<Response<AuthenticateUserResponse>>

    @FormUrlEncoded
    @POST("api/GTFS/getGTFS")
    override fun getGTFS(@Field("token") token: String): CompletableFuture<ResponseBody>

    @FormUrlEncoded
    @POST("api/GTFS/getAlerts")
    override fun getAlerts(@Field("token") token: String): CompletableFuture<ResponseBody>

    @FormUrlEncoded
    @POST("api/GTFS/getTripUpdates")
    override fun getTripUpdates(@Field("token") token: String): CompletableFuture<ResponseBody>

    @FormUrlEncoded
    @POST("api/GTFS/getVehiclePositions")
    override fun getVehiclePositions(@Field("token") token: String): CompletableFuture<ResponseBody>
}