package org.mavriksc.overlay

import okhttp3.Request
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path

fun String.toRequest(): Request = Request.Builder().url(this).build()

fun String.writeToFile(text: String) {
    val file = File(this)
    file.parentFile?.mkdirs()
    file.writeText(text)
}

fun String.getText(): String? {
    return try {
        Files.readString(Path(this))
    } catch (_: Exception) {
        null
    }
}