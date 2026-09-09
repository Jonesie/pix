package nz.net.jonesie.pix.data.repo

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File

/** Wraps a file RequestBody to report write progress (0f..1f) — used for large video uploads. */
class ProgressRequestBody(
    private val file: File,
    private val contentType: MediaType?,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var written = 0L
        file.source().use { source ->
            val buf = okio.Buffer()
            var read: Long
            while (source.read(buf, 65536).also { read = it } != -1L) {
                sink.write(buf, read)
                written += read
                onProgress(if (total > 0) written.toFloat() / total.toFloat() else 0f)
            }
        }
    }
}
