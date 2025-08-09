package systems.choochoo.transit_data_archivers.njt.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import jakarta.inject.Inject
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import systems.choochoo.transit_data_archivers.njt.model.Environment
import systems.choochoo.transit_data_archivers.njt.model.Mode
import systems.choochoo.transit_data_archivers.njt.utils.ProtoConverterFactory


internal class MetaRealtimeService @Inject constructor(
    client: OkHttpClient,
    om: ObjectMapper,
) {
    private val cache: LoadingCache<Pair<Environment, Mode>, RealtimeService> = CacheBuilder.newBuilder()
        .maximumSize(4)
        .build(CacheLoader.from { (environment, mode) ->
            val retrofit = Retrofit.Builder()
                .client(client)
                .addConverterFactory(ProtoConverterFactory.create())
                .addConverterFactory(JacksonConverterFactory.create(om))
                .baseUrl(mode.baseUrlForEnvironment(environment))
                .build()

            retrofit.create(mode.serviceClass.java)
        })

    fun get(env: Environment, mode: Mode): RealtimeService {
        return cache.get(Pair(env, mode))
    }
}