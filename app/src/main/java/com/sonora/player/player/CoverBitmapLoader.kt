package com.sonora.player.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.sonora.player.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Загружает обложки для уведомления и экрана блокировки через Coil —
 * тем же HTTP-клиентом, что и приложение (сессия NAS, самоподписанные сертификаты, кэш).
 * Если обложки нет, рисует фирменную заглушку вместо стандартной иконки.
 */
@UnstableApi
class CoverBitmapLoader(private val context: Context) : BitmapLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun supportsMimeType(mimeType: String) = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = future {
        BitmapFactory.decodeByteArray(data, 0, data.size) ?: throw IOException("Не удалось декодировать обложку")
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = future {
        val request = ImageRequest.Builder(context)
            .data(uri.toString())
            .size(SIZE)
            .allowHardware(false)
            .build()
        val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
        drawable?.toBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888) ?: placeholder(uri.toString())
    }

    fun release() = scope.cancel()

    private fun future(block: suspend () -> Bitmap): ListenableFuture<Bitmap> {
        val result = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                result.set(block())
            } catch (e: Throwable) {
                result.setException(e)
            }
        }
        return result
    }

    /** Градиент с логотипом; оттенок зависит от трека, чтобы заглушки различались. */
    private fun placeholder(seed: String): Bitmap {
        val bitmap = createBitmap(SIZE, SIZE)
        val canvas = Canvas(bitmap)
        val hue = ((seed.hashCode() and 0x7fffffff) % 360).toFloat()
        val start = ColorUtils.HSLToColor(floatArrayOf(hue, 0.6f, 0.42f))
        val end = ColorUtils.HSLToColor(floatArrayOf((hue + 55f) % 360f, 0.65f, 0.2f))
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), start, end, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), paint)
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)?.let { logo ->
            logo.setBounds(0, 0, SIZE, SIZE)
            logo.draw(canvas)
        }
        return bitmap
    }

    private companion object {
        const val SIZE = 512
    }
}
