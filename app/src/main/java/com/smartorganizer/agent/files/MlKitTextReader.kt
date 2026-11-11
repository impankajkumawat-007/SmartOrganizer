package com.smartorganizer.agent.files

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class MlKitTextReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun read(bitmap: Bitmap): String {
        return runCatching {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text.trim()
        }.getOrDefault("")
    }
}
