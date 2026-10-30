package com.pasajeya.app.feature.benefit.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Foto del carné lista para subir (JPEG derecho y acotado) y el texto que ML Kit leyó. */
class CarneAnalysis(
    val jpegBytes: ByteArray,
    val recognizedText: String
)

/** Endereza la foto del carné, la reduce y lee su texto en el teléfono con ML Kit. */
@Singleton
class CarneImageAnalyzer @Inject constructor() {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun analyze(jpeg: ByteArray, rotationDegrees: Int): CarneAnalysis =
        withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight))
            }
            val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, decodeOptions)
                ?: throw IllegalArgumentException("No se pudo leer la foto.")
            val upright = if (rotationDegrees % 360 != 0) {
                Bitmap.createBitmap(
                    decoded,
                    0,
                    0,
                    decoded.width,
                    decoded.height,
                    Matrix().apply { postRotate(rotationDegrees.toFloat()) },
                    true
                )
            } else {
                decoded
            }

            val output = ByteArrayOutputStream()
            upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            val text = recognizer.process(InputImage.fromBitmap(upright, 0)).await().text
            CarneAnalysis(jpegBytes = output.toByteArray(), recognizedText = text)
        }

    private fun sampleSizeFor(longestSide: Int): Int {
        var sample = 1
        while (longestSide / sample > MAX_SIDE_PX * 2) sample *= 2
        return sample
    }

    private companion object {
        const val MAX_SIDE_PX = 1_600
        const val JPEG_QUALITY = 90
    }
}
