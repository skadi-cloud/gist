package cloud.skadi.gist.mps.plugin

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import java.util.*

class MultiPartContent(private val parts: List<Part>) : OutgoingContent.WriteChannelContent() {
    private val uuid = UUID.randomUUID()
    private val boundary = "***ktor-$uuid-ktor-${System.currentTimeMillis()}***"

    data class Part(val name: String, val filename: String? = null, val headers: Headers = Headers.Empty, val writer: suspend ByteWriteChannel.() -> Unit)

    override suspend fun writeTo(channel: ByteWriteChannel) {
        for (part in parts) {
            channel.writeStringUtf8("--$boundary\r\n")
            val partHeaders = Headers.build {
                val fileNamePart = if (part.filename != null) "; filename=\"${part.filename}\"" else ""
                append("Content-Disposition", "form-data; name=\"${part.name}\"$fileNamePart")
                appendAll(part.headers)
            }
            for ((key, value) in partHeaders.flattenEntries()) {
                channel.writeStringUtf8("$key: $value\r\n")
            }
            channel.writeStringUtf8("\r\n")
            part.writer(channel)
            channel.writeStringUtf8("\r\n")
        }
        channel.writeStringUtf8("--$boundary--\r\n")
    }

    override val contentType = ContentType.MultiPart.FormData
        .withParameter("boundary", boundary)
        .withCharset(Charsets.UTF_8)

    class Builder {
        val parts = arrayListOf<Part>()

        fun add(part: Part) {
            parts += part
        }

        fun add(name: String, filename: String? = null, contentType: ContentType? = null, headers: Headers = Headers.Empty, writer: suspend ByteWriteChannel.() -> Unit) {
            val contentTypeHeaders: Headers = if (contentType != null) headersOf(HttpHeaders.ContentType, contentType.toString()) else headersOf()
            add(Part(name, filename, headers + contentTypeHeaders, writer))
        }

        fun add(name: String, text: String, contentType: ContentType? = null, filename: String? = null) {
            add(name, filename, contentType) { writeStringUtf8(text) }
        }

        fun add(name: String, data: ByteArray, contentType: ContentType? = ContentType.Application.OctetStream, filename: String? = null) {
            add(name, filename, contentType) { writeFully(data) }
        }

        internal suspend fun build(): MultiPartContent = MultiPartContent(parts.toList())
    }

    companion object {
        suspend fun build(callback: suspend Builder.() -> Unit): MultiPartContent {
            val builder = Builder()
            callback(builder)
            return builder.build()
        }
    }
}

operator fun Headers.plus(other: Headers): Headers = when {
    this.isEmpty() -> other
    other.isEmpty() -> this
    else -> Headers.build {
        appendAll(this@plus)
        appendAll(other)
    }
}