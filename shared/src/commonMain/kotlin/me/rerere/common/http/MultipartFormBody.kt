package me.rerere.common.http

data class MultipartFormPart(
    val name: String,
    val value: ByteArray,
    val filename: String? = null,
    val contentType: String? = null,
) {
    companion object {
        fun text(name: String, value: String): MultipartFormPart =
            MultipartFormPart(name = name, value = value.encodeToByteArray())

        fun file(
            name: String,
            filename: String,
            bytes: ByteArray,
            contentType: String,
        ): MultipartFormPart = MultipartFormPart(
            name = name,
            value = bytes,
            filename = filename,
            contentType = contentType,
        )
    }
}

data class EncodedMultipartBody(
    val body: ByteArray,
    val contentType: String,
)

object MultipartFormBody {
    fun encode(
        parts: List<MultipartFormPart>,
        boundary: String = "----LastChatForm${parts.hashCode().toUInt()}",
    ): EncodedMultipartBody {
        val chunks = mutableListOf<ByteArray>()
        parts.forEach { part ->
            val header = buildString {
                append("--")
                append(boundary)
                append("\r\n")
                append("Content-Disposition: form-data; name=\"")
                append(part.name)
                append('"')
                if (part.filename != null) {
                    append("; filename=\"")
                    append(part.filename)
                    append('"')
                }
                append("\r\n")
                part.contentType?.let { type ->
                    append("Content-Type: ")
                    append(type)
                    append("\r\n")
                }
                append("\r\n")
            }.encodeToByteArray()
            chunks += header
            chunks += part.value
            chunks += "\r\n".encodeToByteArray()
        }
        chunks += "--$boundary--\r\n".encodeToByteArray()
        val total = chunks.sumOf { it.size }
        val body = ByteArray(total)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(body, offset)
            offset += chunk.size
        }
        return EncodedMultipartBody(
            body = body,
            contentType = "multipart/form-data; boundary=$boundary",
        )
    }
}
