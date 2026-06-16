package me.rerere.common.platform.android

import me.rerere.common.platform.PlatformJwtSigner
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class AndroidPlatformJwtSigner : PlatformJwtSigner {
    override fun signRs256(data: ByteArray, pkcs8PrivateKeyPem: String): ByteArray {
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(parsePkcs8PrivateKey(pkcs8PrivateKeyPem))
        sig.update(data)
        return sig.sign()
    }

    private fun parsePkcs8PrivateKey(pem: String): PrivateKey {
        val normalized = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val der = Base64.Default.decode(normalized)
        val keySpec = PKCS8EncodedKeySpec(der)
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }
}
