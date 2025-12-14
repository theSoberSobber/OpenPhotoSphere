package com.pavit.openphotosphere.opencv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class StitchResult(val bitmap: Bitmap, val savedPath: String, val debugLog: List<String>)

class StitchException(val code: Int?, val reason: String, val logs: List<String>) :
    Exception("Stitching failed ($reason)")

private data class DecodedFrame(val bitmap: Bitmap, val sampleSize: Int, val rotation: Float)

object PanoramaStitcher {
    suspend fun stitch(context: Context, photoPaths: List<String>): StitchResult = withContext(Dispatchers.Default) {
        require(photoPaths.size >= 2) { "Need at least two photos to stitch" }

        // Ensure OpenCV native libs are loaded before creating Mats.
        @Suppress("UNUSED_VARIABLE")
        val loader = NativeStitcher

        val mats = mutableListOf<Mat>()
        val logs = mutableListOf<String>()
        try {
            photoPaths.forEachIndexed { index, path ->
                val decoded = decodeOrientedBitmap(path)
                    ?: throw StitchException(null, "Failed to decode $path", logs.toList())

                logs += "Photo ${index + 1}: ${decoded.bitmap.width}x${decoded.bitmap.height}, sample=${decoded.sampleSize}, rotation=${decoded.rotation}"

                val mat = Mat()
                Utils.bitmapToMat(decoded.bitmap, mat)
                mats.add(mat)
            }

            val output = Mat()
            val status = NativeStitcher.stitch(
                mats.map { it.nativeObjAddr }.toLongArray(),
                output.nativeObjAddr
            )
            logs += "Native stitch status: $status (${statusMessage(status)})"

            if (status != 0 || output.empty()) {
                throw StitchException(status, statusMessage(status), logs.toList())
            }

            val stitched = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(output, stitched)

            logs += "Panorama size: ${stitched.width}x${stitched.height}"

            val dir = File(context.filesDir, "panoramas").apply { mkdirs() }
            val filename = "pano_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.png"
            val outFile = File(dir, filename)
            FileOutputStream(outFile).use { stitched.compress(Bitmap.CompressFormat.PNG, 100, it) }

            logs += "Saved to ${outFile.absolutePath}"

            StitchResult(stitched, outFile.absolutePath, logs.toList())
        } finally {
            mats.forEach { it.release() }
        }
    }

    private fun statusMessage(code: Int): String = when (code) {
        0 -> "OK"
        1 -> "Need more images"
        2 -> "Homography estimation failed"
        3 -> "Camera parameters adjustment failed"
        else -> "Unknown error"
    }

    private fun decodeOrientedBitmap(path: String, maxDimension: Int = 1600): DecodedFrame? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
        val (oriented, rotation) = applyExifRotation(path, bmp)
        val finalBmp = if (oriented.config == Bitmap.Config.ARGB_8888) oriented else oriented.copy(Bitmap.Config.ARGB_8888, false)
        return DecodedFrame(finalBmp, sample, rotation)
    }

    private fun applyExifRotation(path: String, bitmap: Bitmap): Pair<Bitmap, Float> {
        val exif = try { ExifInterface(path) } catch (_: Exception) { null }
        val rotation = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return bitmap to rotation
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated to rotation
    }
}
