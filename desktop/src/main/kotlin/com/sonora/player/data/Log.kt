package com.sonora.player.data

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Простой журнал в %APPDATA%\Sonora\sonora.log — помогает разобраться, если трек не играет. */
object Log {
    private val file = File(File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Sonora").apply { mkdirs() }, "sonora.log")
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    init {
        runCatching { if (file.length() > 512 * 1024) file.delete() }
    }

    @Synchronized
    fun d(message: String, e: Throwable? = null) {
        val line = "${LocalDateTime.now().format(fmt)} [${Thread.currentThread().name}] $message" +
            (e?.let { err ->
                " — ${err.javaClass.simpleName}: ${err.message}" +
                    err.stackTrace.take(12).joinToString("") { "\n    at $it" } +
                    (err.cause?.let { c -> "\n  caused by ${c.javaClass.simpleName}: ${c.message}" } ?: "")
            } ?: "")
        System.err.println(line)
        runCatching { file.appendText(line + System.lineSeparator()) }
    }
}
