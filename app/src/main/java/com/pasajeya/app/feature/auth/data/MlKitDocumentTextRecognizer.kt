package com.pasajeya.app.feature.auth.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.pasajeya.app.feature.auth.domain.DocumentTextRecognizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** OCR local con ML Kit: la imagen nunca sale del telefono. */
@Singleton
class MlKitDocumentTextRecognizer @Inject constructor() : DocumentTextRecognizer {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(jpeg: ByteArray, rotationDegrees: Int): String =
        withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(maxOf(bounds.outWidth, bounds.outHeight))
            }
            val decoded = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
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
            recognizer.process(InputImage.fromBitmap(upright, 0)).await().text
        }

    private fun sampleSizeFor(longestSide: Int): Int {
        var sample = 1
        while (longestSide / sample > MAX_SIDE_PX * 2) sample *= 2
        return sample
    }

    private companion object {
        const val MAX_SIDE_PX = 1_600
    }
}
