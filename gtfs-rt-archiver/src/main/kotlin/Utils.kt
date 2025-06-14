@file:OptIn(ExperimentalHoplite::class)

package systems.choochoo.transit_data_archivers.gtfsrt

import com.sksamuel.hoplite.ExperimentalHoplite
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal fun OkHttpClient.Builder.ignoreAllTLSErrors(): OkHttpClient.Builder {
    val naiveTrustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
    }

    val insecureSocketFactory = SSLContext.getInstance("TLSv1.3").apply {
        val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
        init(null, trustAllCerts, SecureRandom())
    }.socketFactory

    sslSocketFactory(insecureSocketFactory, naiveTrustManager)
    hostnameVerifier { _, _ -> true }
    return this
}

internal fun formulateSubject(subjectPrefix: String, producer: String, feed: String): String =
    listOf(subjectPrefix, sanitizeForSubject(producer), sanitizeForSubject(feed)).joinToString(".")

internal fun sanitizeForSubject(s: String): String = s.replace(" ", "_").replace(Regex("""[*>.\\/]"""), "")