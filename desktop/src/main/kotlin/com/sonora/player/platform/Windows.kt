package com.sonora.player.platform

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.awt.Window
import kotlin.concurrent.thread

private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

private interface DwmApi : Library {
    fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: Pointer, size: Int): Int
}

/** Красит системный заголовок окна под тему приложения (Windows 10 2004+ / Windows 11). */
object TitleBar {
    private val dwm: DwmApi? by lazy {
        if (!isWindows) null else runCatching { Native.load("dwmapi", DwmApi::class.java) }.getOrNull()
    }

    fun apply(window: Window, dark: Boolean, captionArgb: Int, textArgb: Int) {
        val api = dwm ?: return
        runCatching {
            val hwnd = WinDef.HWND(Native.getWindowPointer(window))
            fun set(attr: Int, value: Int) {
                val mem = Memory(4)
                mem.setInt(0, value)
                api.DwmSetWindowAttribute(hwnd, attr, mem, 4)
            }
            set(DWMWA_USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)
            set(DWMWA_CAPTION_COLOR, argbToColorRef(captionArgb))
            set(DWMWA_TEXT_COLOR, argbToColorRef(textArgb))
        }
    }

    private fun argbToColorRef(argb: Int): Int {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36
}

enum class MediaKey { PlayPause, Next, Previous, Stop }

/**
 * Глобальные медиаклавиши клавиатуры (⏯ ⏭ ⏮ ⏹) — работают, даже когда окно свёрнуто.
 * Если клавиши уже заняты другим плеером, Windows откажет в регистрации — это не ошибка.
 */
object MediaKeys {
    @Volatile
    private var threadId = 0
    private var worker: Thread? = null

    fun start(onKey: (MediaKey) -> Unit) {
        if (!isWindows || worker != null) return
        worker = thread(isDaemon = true, name = "sonora-media-keys") {
            runCatching {
                val user32 = User32.INSTANCE
                threadId = Kernel32.INSTANCE.GetCurrentThreadId()
                val keys = mapOf(
                    1 to (0xB3 to MediaKey.PlayPause),
                    2 to (0xB0 to MediaKey.Next),
                    3 to (0xB1 to MediaKey.Previous),
                    4 to (0xB2 to MediaKey.Stop),
                )
                keys.forEach { (id, key) -> user32.RegisterHotKey(null, id, 0, key.first) }
                val msg = WinUser.MSG()
                while (user32.GetMessage(msg, null, 0, 0) > 0) {
                    if (msg.message == WM_HOTKEY) {
                        keys[msg.wParam.toInt()]?.second?.let(onKey)
                    }
                }
                keys.keys.forEach { user32.UnregisterHotKey(null, it) }
            }
        }
    }

    fun stop() {
        val id = threadId
        if (id != 0) {
            runCatching { User32.INSTANCE.PostThreadMessage(id, WinUser.WM_QUIT, null, null) }
        }
        threadId = 0
        worker = null
    }

    private const val WM_HOTKEY = 0x0312
}
