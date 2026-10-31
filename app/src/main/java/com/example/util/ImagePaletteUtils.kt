package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ImagePaletteUtils {

    suspend fun extractPaletteFromUri(context: Context, uri: Uri): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext emptyList()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (bitmap == null) return@withContext emptyList()
                extractDominantHexColors(bitmap)
            } catch (e: Exception) {
                emptyList()
            }
        }

    fun extractDominantHexColors(bitmap: Bitmap): List<String> {
        val scaled = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
        val bucketCounts = mutableMapOf<Int, Int>()
        val bucketSumsR = mutableMapOf<Int, Long>()
        val bucketSumsG = mutableMapOf<Int, Long>()
        val bucketSumsB = mutableMapOf<Int, Long>()

        for (y in 0 until scaled.height) {
            for (x in 0 until scaled.width) {
                val pixel = scaled.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                if (alpha < 128) continue
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Quantize into 4x4x4 RGB space
                val qR = r / 64
                val qG = g / 64
                val qB = b / 64
                val key = (qR shl 4) or (qG shl 2) or qB

                bucketCounts[key] = (bucketCounts[key] ?: 0) + 1
                bucketSumsR[key] = (bucketSumsR[key] ?: 0L) + r
                bucketSumsG[key] = (bucketSumsG[key] ?: 0L) + g
                bucketSumsB[key] = (bucketSumsB[key] ?: 0L) + b
            }
        }

        val sortedBuckets = bucketCounts.entries.sortedByDescending { it.value }.take(5)
        val result = sortedBuckets.map { (key, count) ->
            val avgR = ((bucketSumsR[key] ?: 56L) / count).toInt().coerceIn(0, 255)
            val avgG = ((bucketSumsG[key] ?: 189L) / count).toInt().coerceIn(0, 255)
            val avgB = ((bucketSumsB[key] ?: 248L) / count).toInt().coerceIn(0, 255)
            String.format("#%02X%02X%02X", avgR, avgG, avgB)
        }

        return if (result.size >= 3) {
            result
        } else {
            listOf("#38BDF8", "#A855F7", "#F472B6", "#0F172A", "#22D3EE")
        }
    }

    suspend fun encodeUriToCompactBase64(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
                val original = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (original == null) return@withContext null

                // Scale down to 96x96 icon thumbnail so Firestore payload stays tiny (<25KB)
                val scaled = Bitmap.createScaledBitmap(original, 96, 96, true)
                val output = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.PNG, 90, output)
                val bytes = output.toByteArray()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } catch (e: Exception) {
                null
            }
        }

    fun decodeBase64ToImageBitmap(base64: String?): ImageBitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bitmap?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}
