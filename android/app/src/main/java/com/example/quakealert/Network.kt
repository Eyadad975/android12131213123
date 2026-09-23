package com.example.quakealert

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object Network {
    private val trustAll = object : X509TrustManager {
        override fun getAcceptedIssuers() = arrayOf<java.security.cert.X509Certificate>()
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
    }
    private val sslFactory: SSLSocketFactory by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }.socketFactory
    }

    fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        if (connection is HttpsURLConnection && isLocalHost(Uri.parse(url).host)) {
            // Deliberately scoped to this client's configured local server; never changes
            // Android's global trust store or other app/network traffic.
            connection.sslSocketFactory = sslFactory
            connection.hostnameVerifier = HostnameVerifier { host, _ ->
                host == Uri.parse(url).host
            }
        }
        return connection
    }

    private fun isLocalHost(host: String?): Boolean {
        return host == "localhost" || host == "127.0.0.1" || host == "::1" ||
            host?.startsWith("192.168.") == true || host?.startsWith("10.") == true ||
            host?.matches(Regex("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) == true
    }
}
