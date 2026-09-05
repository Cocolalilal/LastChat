package me.rerere.common.platform.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import me.rerere.common.crypto.Pkcs8RsaPrivateKey
import me.rerere.common.platform.PlatformJwtSigner
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryRef
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyCreateWithData
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeRSA
import platform.Security.kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256
import platform.posix.memcpy
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** RS256 signer for Vertex service-account authentication. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalEncodingApi::class)
class IosPlatformJwtSigner : PlatformJwtSigner {
    override fun signRs256(data: ByteArray, pkcs8PrivateKeyPem: String): ByteArray {
        val pkcs8 = Base64.decode(
            pkcs8PrivateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .filterNot(Char::isWhitespace),
        )
        val rsaPrivateKey = Pkcs8RsaPrivateKey.extract(pkcs8).toNSData()
        val attributes = mapOf(
            kSecAttrKeyType to kSecAttrKeyTypeRSA,
            kSecAttrKeyClass to kSecAttrKeyClassPrivate,
        ) as CFDictionaryRef
        val key = SecKeyCreateWithData(
            keyData = rsaPrivateKey as CFDataRef,
            attributes = attributes,
            error = null,
        ) ?: error("iOS Security could not import the RSA private key")
        val signature = SecKeyCreateSignature(
            key = key,
            algorithm = kSecKeyAlgorithmRSASignatureMessagePKCS1v15SHA256,
            dataToSign = data.toNSData() as CFDataRef,
            error = null,
        ) ?: error("iOS Security could not create the RS256 signature")
        return (signature as NSData).toByteArray()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = if (isEmpty()) {
    NSData()
} else {
    usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    if (length == 0uL) return ByteArray(0)
    return ByteArray(length.toInt()).also { result ->
        result.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}
