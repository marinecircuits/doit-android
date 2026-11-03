package com.marinecircuits.doit.share

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Metadata for an incoming PDF. The share provider controls the display name; never use it as a file path. */
data class SharedPdf(val filename: String, val title: String) {
    companion object {
        const val MAX_BYTES = 25 * 1024 * 1024

        fun fromDisplayName(displayName: String?): SharedPdf {
            val leaf = displayName.orEmpty().trim().replace('\\', '/').substringAfterLast('/').trim()
            val name = leaf.takeUnless {
                it.isBlank() || it == "." || it == ".." || it.equals(".pdf", ignoreCase = true)
            } ?: "document.pdf"
            val filename = if (name.endsWith(".pdf", ignoreCase = true)) name else name + ".pdf"
            return SharedPdf(filename, filename.dropLast(4).trim().ifEmpty { "document" })
        }

        /** Keep shared attachments bounded in memory; the repository currently queues PDFs as byte arrays. */
        fun read(stream: InputStream): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count == -1) break
                if (count == 0) continue
                if (output.size() > MAX_BYTES - count) throw PdfTooLargeException()
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }
}

class PdfTooLargeException : IOException("Shared PDF exceeds 25 MB")
