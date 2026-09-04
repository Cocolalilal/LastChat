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
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
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
        val queryList = query(key) + listOf(
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val status = withCFDictionary(queryList) { cfQuery ->
            SecItemCopyMatching(
                query = cfQuery,
                result = result.ptr,
            )
        }
        println("[LastChat] Keychain read key=$key, status=$status")
        when (status) {
            errSecItemNotFound -> null
            errSecSuccess -> (CFBridgingRelease(result.value) as? NSData)?.toByteArray()?.decodeToString()
            else -> {
                println("[LastChat] Warning: Keychain read failed for key $key with status $status, returning null")
                null
            }
        }
    }

    override suspend fun writeString(key: String, value: String) {
        withCFDictionary(query(key)) { cfQuery ->
            SecItemDelete(cfQuery)
        }
        val attrList = query(key) + listOf(
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData to value.encodeToByteArray().toNSData(),
        )
        val status = withCFDictionary(attrList) { cfAttr ->
            SecItemAdd(
                attributes = cfAttr,
                result = null,
            )
        }
        println("[LastChat] Keychain write key=$key, status=$status")
        if (status != errSecSuccess) {
            println("[LastChat] Warning: Keychain write failed for key $key with status $status")
        }
    }

    override suspend fun remove(key: String) {
        val status = withCFDictionary(query(key)) { cfQuery ->
            SecItemDelete(cfQuery)
        }
        println("[LastChat] Keychain remove key=$key, status=$status")
    }

    private fun query(key: String): List<Pair<CFStringRef?, Any?>> = listOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to service,
        kSecAttrAccount to key,
    )

    private inline fun <R> withCFDictionary(
        entries: List<Pair<CFStringRef?, Any?>>,
        block: (CFDictionaryRef) -> R,
    ): R {
        val dict = CFDictionaryCreateMutable(
            allocator = null,
            capacity = entries.size.toLong(),
            keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
            valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
        ) ?: error("Failed to create CFMutableDictionary")
        val retainToRelease = mutableListOf<CFTypeRef>()
        try {
            for ((key, value) in entries) {
                if (key == null || value == null) continue
                val cfValue: CFTypeRef = when (value) {
                    is String -> {
                        val ref = CFBridgingRetain(value as NSString)!!
                        retainToRelease.add(ref)
                        ref
                    }
                    is NSData -> {
                        val ref = CFBridgingRetain(value)!!
                        retainToRelease.add(ref)
                        ref
                    }
                    else -> value as CFTypeRef
                }
                CFDictionarySetValue(dict, key, cfValue)
            }
            return block(dict)
        } finally {
            for (ref in retainToRelease) {
                CFRelease(ref)
            }
            CFRelease(dict)
        }
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
