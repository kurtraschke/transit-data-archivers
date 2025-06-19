package systems.choochoo.transit_data_archivers.core.utils

import okhttp3.OkHttpClient
import systems.choochoo.transit_data_archivers.core.configuration.ApplicationVersion
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

fun constructUserAgentString(
    applicationVersion: ApplicationVersion,
    operatorContact: String? = null,
    okHttpVersion: String? = null
): String {
    val userAgentString =
        "${applicationVersion.groupId}:${applicationVersion.artifactId}/${applicationVersion.version}" +
                " (${applicationVersion.commitId}; ${applicationVersion.branch})" +
                (okHttpVersion?.let { " (okhttp/${it})" } ?: "") +
                (operatorContact?.let { " ($it)" } ?: "")

    return userAgentString
}

fun OkHttpClient.Builder.ignoreAllTLSErrors(): OkHttpClient.Builder {
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