package com.portalcast.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val QuietZone = 10.dp

/**
 * A QR code of [content], encoded locally with ZXing (contract §2.3 says not to copy
 * the webui's external QR image service). Black modules on a white card so every
 * phone camera reads it; the card's padding plus ZXing's margin form the quiet zone.
 */
@Composable
fun QrCodeImage(content: String, modifier: Modifier = Modifier, size: Dp = 240.dp) {
    val innerPx = with(LocalDensity.current) { (size - QuietZone * 2).roundToPx() }.coerceAtLeast(1)
    val bitmap by produceState<ImageBitmap?>(initialValue = null, content, innerPx) {
        value = withContext(Dispatchers.Default) {
            runCatching { encodeQrBitmap(content, innerPx).asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier
            .size(size)
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(QuietZone),
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = "QR code for $content",
                modifier = Modifier.fillMaxSize(),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

/** Encodes [content] into a black-on-white [sizePx] square bitmap. Pure ZXing, no network. */
fun encodeQrBitmap(content: String, sizePx: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val row = y * w
        for (x in 0 until w) pixels[row + x] = if (matrix[x, y]) BLACK else WHITE
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
}

private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
