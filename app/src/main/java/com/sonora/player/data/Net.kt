package com.sonora.player.data

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Общий HTTP-клиент для API, обложек и потокового аудио. */
object Net {
    @Volatile
    var client: OkHttpClient = build(trustAll = false)
        private set

    /** Разрешить самоподписанные сертификаты (типично для домашнего NAS по HTTPS). */
    @Volatile
    var trustAllCerts: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                client = build(value)
            }
        }

    /** Фабрика вызовов, всегда использующая актуальный клиент. */
    val callFactory: Call.Factory = object : Call.Factory {
        override fun newCall(request: Request): Call = client.newCall(request)
    }

    private fun build(trustAll: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        if (trustAll) {
            val tm = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), SecureRandom()) }
            builder.sslSocketFactory(ssl.socketFactory, tm).hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }
}
