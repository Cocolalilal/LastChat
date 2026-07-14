package me.rerere.common.platform.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import me.rerere.common.platform.SecureSettingsStore
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/** Keychain-backed storage for provider keys and other LastChat secrets. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSecureSettingsStore(
    private val service: String = "lastchat.rikkafork.cocolal",
) : SecureSettingsStore {
    override suspend fun readString(key: String): String? = memScoped {
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(
            query = (query(key) + mapOf(
                kSecReturnData to kCFBooleanTrue,
                kSecMatchLimit to kSecMatchLimitOne,
            )) as CFDictionaryRef,
            result = result.ptr,
        )
        when (status) {
            errSecItemNotFound -> null
            errSecSuccess -> (result.value as? NSData)?.toByteArray()?.decodeToString()
            else -> error("Keychain read failed with status $status")
        }
    }

    override suspend fun writeString(key: String, value: String) {
        SecItemDelete(query(key) as CFDictionaryRef)
        val status = SecItemAdd(
            attributes = (query(key) + mapOf(
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                kSecValueData to value.encodeToByteArray().toNSData(),
            )) as CFDictionaryRef,
            result = null,
        )
        check(status == errSecSuccess) { "Keychain write failed with status $status" }
    }

    override suspend fun remove(key: String) {
        val status = SecItemDelete(query(key) as CFDictionaryRef)
        check(status == errSecSuccess || status == errSecItemNotFound) {
            "Keychain delete failed with status $status"
        }
    }

    private fun query(key: String): Map<Any?, Any?> = mapOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to service,
        kSecAttrAccount to key,
    )
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
