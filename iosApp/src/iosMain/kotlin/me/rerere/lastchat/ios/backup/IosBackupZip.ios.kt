package me.rerere.lastchat.ios.backup

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.zlib.ZLIB_VERSION
import platform.zlib.Z_BUF_ERROR
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2_
import platform.zlib.z_stream

@OptIn(ExperimentalForeignApi::class)
internal actual fun inflateRawDeflate(data: ByteArray, maxBytes: Int): ByteArray? {
    memScoped {
        val stream = alloc<z_stream>()
        if (inflateInit2_(stream.ptr, -15, ZLIB_VERSION, sizeOf<z_stream>().toInt()) != Z_OK) {
            return null
        }
        try {
            data.usePinned { inputPin ->
                stream.next_in = inputPin.addressOf(0).reinterpret<UByteVar>()
                stream.avail_in = data.size.convert()
                val chunk = ByteArray(64 * 1024)
                chunk.usePinned { outputPin ->
                    val parts = mutableListOf<ByteArray>()
                    var total = 0
                    while (true) {
                        stream.next_out = outputPin.addressOf(0).reinterpret<UByteVar>()
                        stream.avail_out = chunk.size.convert()
                        val status = inflate(stream.ptr, Z_NO_FLUSH)
                        val produced = chunk.size - stream.avail_out.toInt()
                        if (produced > 0) {
                            if (total + produced > maxBytes) return null
                            parts += chunk.copyOf(produced)
                            total += produced
                        }
                        if (status == Z_STREAM_END) {
                            return when (parts.size) {
                                0 -> ByteArray(0)
                                1 -> parts[0]
                                else -> parts.reduce { acc, part -> acc + part }
                            }
                        }
                        if (status != Z_OK && status != Z_BUF_ERROR) return null
                        if (produced == 0 && stream.avail_in == 0u) return null
                    }
                }
            }
        } finally {
            inflateEnd(stream.ptr)
        }
    }
    return null
}
