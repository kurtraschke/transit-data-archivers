@file:OptIn(ExperimentalTime::class)

package systems.choochoo.transit_data_archivers.njt

import com.fasterxml.jackson.core.exc.StreamReadException
import com.fasterxml.jackson.databind.DatabindException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.RuntimeJsonMappingException
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.common.net.HttpHeaders
import io.javalin.http.BadGatewayResponse
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import systems.choochoo.transit_data_archivers.njt.model.Token
import systems.choochoo.transit_data_archivers.njt.model.TokenKey
import systems.choochoo.transit_data_archivers.njt.services.MetaRealtimeService
import systems.choochoo.transit_data_archivers.njt.utils.TOKEN_LIFETIME
import systems.choochoo.transit_data_archivers.njt.utils.parseErrorMessage
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant


@Singleton
internal class PersistentTokenCache @Inject constructor(
    @param:Named("username") private val username: String,
    @param:Named("password") private val password: String,
    private val s: MetaRealtimeService,
    private val ts: TokenStore,
    private val om: ObjectMapper,
) {
    private val locks = ConcurrentHashMap<TokenKey, Lock>()

    private fun lock(k: TokenKey): Lock {
        return locks.computeIfAbsent(k) { _ -> ReentrantLock() }
    }

    fun get(k: TokenKey): Token {
        val l = lock(k)

        return l.withLock {
            if (!existsAndIsValid(k)) {
                ts.put(k, getUncached(k))
            }

            ts.get(k)!!
        }
    }

    private fun getUncached(k: TokenKey): Token {
        val rs = s.get(k.environment, k.mode)

        val call = rs.authenticateUser(username, password)
        val response = call.execute()

        if (response.isSuccessful && response.body()?.authenticated == true && response.body()?.token != null) {
            val t = Token(
                response.body()?.token!!,
                response.headers().getDate(HttpHeaders.DATE)?.toInstant()?.toKotlinInstant() ?: Clock.System.now()
            )

            return t
        } else {
            val errorMessage = response.body()?.errorMessage ?: parseErrorMessage(om, response.errorBody()?.bytes())

            if (errorMessage != null) {
                throw BadGatewayResponse(errorMessage)
            } else {
                throw BadGatewayResponse("Unable to obtain token.")
            }
        }
    }

    fun existsAndIsValid(k: TokenKey): Boolean {
        return lock(k).withLock {
            val t = ts.get(k)

            if (t == null) {
                false
            } else {
                (t.whenObtained + TOKEN_LIFETIME) < Clock.System.now()
            }
        }
    }

    fun invalidate(k: TokenKey) {
        lock(k).withLock {
            ts.delete(k)
        }
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

    private fun tokenPath(key: TokenKey) = baseDir.resolve(key.filenameForToken())

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

private fun TokenKey.filenameForToken() = "$environment-$mode.json"