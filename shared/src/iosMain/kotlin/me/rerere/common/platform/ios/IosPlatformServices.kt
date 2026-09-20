package me.rerere.common.platform.ios

import me.rerere.common.platform.PlatformFileStore
import me.rerere.common.platform.PlatformHaptics
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformJwtSigner
import me.rerere.common.platform.PlatformMediaEncoder
import me.rerere.common.platform.PlatformSpeechRecorder
import me.rerere.common.platform.SecureSettingsStore
import me.rerere.document.PlatformDocumentParser

/** Single composition root for the platform contracts consumed by shared features. */
class IosPlatformServices(
    val httpClient: PlatformHttpClient = IosPlatformHttpClient(),
    val fileStore: PlatformFileStore = IosPlatformFileStore(),
    val mediaEncoder: PlatformMediaEncoder = IosPlatformMediaEncoder(),
    val jwtSigner: PlatformJwtSigner = IosPlatformJwtSigner(),
    val secureSettingsStore: SecureSettingsStore = IosSecureSettingsStore(),
    val haptics: PlatformHaptics = IosPlatformHaptics(),
    val speechRecorder: PlatformSpeechRecorder = IosPlatformSpeechRecorder(),
    val documentParser: PlatformDocumentParser = IosPlatformDocumentParser(),
)
