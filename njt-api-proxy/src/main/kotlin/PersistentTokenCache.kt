package systems.choochoo.transit_data_archivers.njt

import com.fasterxml.jackson.core.exc.StreamReadException
import com.fasterxml.jackson.databind.DatabindException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.RuntimeJsonMappingException
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.net.HttpHeaders
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadGatewayResponse
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import systems.choochoo.transit_data_archivers.njt.model.Environment
import systems.choochoo.transit_data_archivers.njt.model.Mode
import systems.choochoo.transit_data_archivers.njt.model.Token
import systems.choochoo.transit_data_archivers.njt.model.TokenKey
import systems.choochoo.transit_data_archivers.njt.services.MetaRealtimeService
import systems.choochoo.transit_data_archivers.njt.utils.parseErrorMessage
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable
import kotlin.time.Clock
import kotlin.time.toKotlinInstant

private val log = KotlinLogging.logger {}

@Singleton
internal class PersistentTokenCache @Inject constructor(
    @param:Named("username") private val username: String,
    @param:Named("password") private val password: String,
    private val s: MetaRealtimeService,
    private val ts: TokenStore,
    private val om: ObjectMapper,
) {
    private val tokens = ConcurrentHashMap<TokenKey, Token>(Environment.entries.size * Mode.entries.size)

    fun get(k: TokenKey): Token {
        tokens.computeIfPresent(k) { k, t ->
            if (t.isValid) {
                t
            } else {
                log.debug { "Purging expired token for ${k.environment} ${k.mode}" }
                ts.delete(k)
                null
            }
        }

        return tokens.computeIfAbsent(k) { k ->
            val storedToken = ts.get(k)

            val storedTokenValid = storedToken?.isValid ?: false

            if (storedTokenValid) {
                log.debug { "Returning valid cached token for ${k.environment} ${k.mode}" }
                storedToken
            } else {
                log.debug { "No valid cached token for ${k.environment} ${k.mode}; requesting new token" }
                val rs = s.get(k.environment, k.mode)

                val call = rs.authenticateUser(username, password)
                val response = call.execute()

                if (response.isSuccessful && response.body()?.authenticated == true && response.body()?.token != null) {
                    log.debug { "New token obtained for ${k.environment} ${k.mode}" }
                    val t = Token(
                        response.body()?.token!!,
                        response.headers().getDate(HttpHeaders.DATE)?.toInstant()?.toKotlinInstant()
                            ?: Clock.System.now()
                    )

                    ts.put(k, t)

                    t
                } else {
                    val errorMessage =
                        response.body()?.errorMessage ?: parseErrorMessage(om, response.errorBody()?.bytes())

                    log.error { "Token request for ${k.environment} ${k.mode} failed: $errorMessage" }

                    throw BadGatewayResponse(errorMessage ?: "Unable to obtain token.")
                }
            }

        }
    }

    fun invalidate(k: TokenKey) {
        ts.delete(k)
        tokens.remove(k)
    }
}

@Singleton
internal class TokenStore @Inject constructor(
    @param:Named("dataPath") private val baseDir: Path,
    private val om: ObjectMapper
) {
    init {
        require(baseDir.isDirectory() && baseDir.isReadable() && baseDir.isWritable()) { "$baseDir must be a directory that is readable and writable" }
    }

    private fun tokenPath(key: TokenKey) = baseDir.resolve(key.filename)

    fun get(key: TokenKey): Token? {
        return try {
            om.readValue<Token>(tokenPath(key).toFile())
        } catch (_: RuntimeJsonMappingException) {
            null
        } catch (_: IOException) {
            null
        } catch (_: StreamReadException) {
            null
        } catch (_: DatabindException) {
            null
        }
    }

    fun put(key: TokenKey, value: Token) {
        om.writeValue(tokenPath(key).toFile(), value)
    }

    fun delete(key: TokenKey) {
        tokenPath(key).deleteIfExists()
    }
}