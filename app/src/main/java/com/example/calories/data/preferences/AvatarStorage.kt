package com.example.calories.data.preferences

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class AvatarStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun saveFromUri(userId: String, uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(uri) ?: error("Could not read image")
        val scaled = scaleDown(bitmap, MAX_SIZE_PX)
        try {
            val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
            val outFile = File(dir, "$userId.jpg")
            FileOutputStream(outFile).use { output ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Could not save image"
                }
            }
            outFile.absolutePath
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
    }

    fun delete(userId: String) {
        File(context.filesDir, "$DIR_NAME/$userId.jpg").delete()
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = false
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }
        }.getOrNull()
    }

    private fun scaleDown(bitmap: Bitmap, maxSize: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxSize) return bitmap
        val scale = maxSize.toFloat() / largest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private companion object {
        const val DIR_NAME = "avatars"
        const val MAX_SIZE_PX = 512
        const val JPEG_QUALITY = 85
    }
}
