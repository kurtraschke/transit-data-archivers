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
import systems.choochoo.transit_data_archivers.njt.model.Token
import systems.choochoo.transit_data_archivers.njt.model.TokenKey
import systems.choochoo.transit_data_archivers.njt.services.MetaRealtimeService
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.isWritable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinInstant


private val TOKEN_LIFETIME = 24.hours

internal class PersistentTokenCache @Inject constructor(
    @Named("dataPath") dataPath: Path,
    @param:Named("username") private val username: String,
    @param:Named("password") private val password: String,
    private val s: MetaRealtimeService,
    om: ObjectMapper
) {
    private val ts = TokenStore(dataPath, om)

    private val locks = ConcurrentHashMap<TokenKey, ReadWriteLock>()

    private fun lock(k: TokenKey): ReadWriteLock {
        return locks.computeIfAbsent(k) { k -> ReentrantReadWriteLock() }
    }

    fun get(k: TokenKey): Token {
        val l = lock(k)

        l.readLock().lock()

        if (!existsAndIsValid(k)) {
            l.readLock().unlock()
            l.writeLock().withLock {
                if (!existsAndIsValid(k)) {
                    ts.put(k, getUncached(k))
                }
                l.readLock().lock()
            }
        }

        return try {
            ts.get(k)!!
        } finally {
            l.readLock().unlock()
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
            throw BadGatewayResponse()
        }
    }

    fun existsAndIsValid(k: TokenKey): Boolean {
        return lock(k).readLock().withLock {
            val t = ts.get(k)

            if (t == null) {
                false
            } else {
                Clock.System.now() - t.whenObtained < TOKEN_LIFETIME
            }
        }
    }

    fun invalidate(k: TokenKey) {
        lock(k).writeLock().withLock {
            ts.delete(k)
        }
    }

}

private class TokenStore(private val baseDir: Path, private val om: ObjectMapper) {
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