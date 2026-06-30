package me.rerere.common.platform

interface PlatformJwtSigner {
    fun signRs256(data: ByteArray, pkcs8PrivateKeyPem: String): ByteArray
}
