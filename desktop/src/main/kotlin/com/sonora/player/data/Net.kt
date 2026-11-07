package com.sonora.player.data

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/** Общий HTTP-клиент для API и обложек. Аудио JavaFX качает через HttpsURLConnection — его настраиваем тоже. */
object Net {
    @Volatile
    var client: OkHttpClient = build(trustAll = false)
        private set

    private val defaultFactory: SSLSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
    private val defaultVerifier: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

    /** Разрешить самоподписанные сертификаты (типично для домашнего NAS по HTTPS). */
    @Volatile
    var trustAllCerts: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                client = build(value)
                if (value) {
                    HttpsURLConnection.setDefaultSSLSocketFactory(trustAllContext.socketFactory)
                    HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
                } else {
                    HttpsURLConnection.setDefaultSSLSocketFactory(defaultFactory)
                    HttpsURLConnection.setDefaultHostnameVerifier(defaultVerifier)
                }
            }
        }

    /** Фабрика вызовов, всегда использующая актуальный клиент. */
    val callFactory: Call.Factory = object : Call.Factory {
        override fun newCall(request: Request): Call = client.newCall(request)
    }

    private val trustAllManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private val trustAllContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAllManager), SecureRandom()) }
    }

    private fun build(trustAll: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        if (trustAll) {
            builder.sslSocketFactory(trustAllContext.socketFactory, trustAllManager).hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }
}
