package com.chai.aiskin.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.chai.aiskin.core.constants.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

class ImageProcessingException(message: String) : Exception(message)

/**
 * Turns the raw capture into the upload file: applies EXIF rotation, downsizes,
 * re-encodes as JPEG (which drops EXIF metadata such as device info), and
 * enforces the size limit. Files live in the app's private cache only.
 */
@Singleton
class CapturedImageProcessor @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir: File get() = File(context.cacheDir, AppConstants.SCAN_DIR).apply { mkdirs() }

    fun newCaptureFile(): File = File(dir, "raw_${UUID.randomUUID()}.jpg")

    data class Processed(val file: File, val bitmap: Bitmap)

    fun process(raw: File): Processed {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(raw.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ImageProcessingException("Foto tidak dapat dibaca. Silakan ambil ulang.")

            val longest = max(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / (sample * 2) >= AppConstants.UPLOAD_MAX_DIMENSION_PX) sample *= 2

            val decoded = BitmapFactory.decodeFile(raw.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw ImageProcessingException("Foto tidak dapat dibaca. Silakan ambil ulang.")

            val oriented = applyExifOrientation(decoded, raw)
            val scaled = scaleDown(oriented, AppConstants.UPLOAD_MAX_DIMENSION_PX)
            if (minOf(scaled.width, scaled.height) < AppConstants.MIN_IMAGE_DIMENSION_PX) {
                throw ImageProcessingException("Resolusi foto terlalu rendah.")
            }

            val out = File(dir, "scan_${UUID.randomUUID()}.jpg")
            var quality = AppConstants.UPLOAD_JPEG_QUALITY
            do {
                FileOutputStream(out).use { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                quality -= 5
            } while (out.length() > AppConstants.UPLOAD_MAX_BYTES && quality >= AppConstants.UPLOAD_MIN_JPEG_QUALITY)

            if (out.length() > AppConstants.UPLOAD_MAX_BYTES) {
                out.delete()
                throw ImageProcessingException("Ukuran foto terlalu besar.")
            }
            return Processed(out, scaled)
        } finally {
            raw.delete()
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val f = maxDim.toFloat() / longest
        val s = Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).roundToInt(), (bitmap.height * f).roundToInt(), true)
        if (s !== bitmap) bitmap.recycle()
        return s
    }

    /** Removes every local scan file (called after upload, on retake, and on account deletion). */
    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
