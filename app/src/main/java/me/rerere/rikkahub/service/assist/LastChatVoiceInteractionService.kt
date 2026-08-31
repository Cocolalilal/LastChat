package me.rerere.rikkahub.service.assist

import android.service.voice.VoiceInteractionService

/**
 * Root voice-interaction service. Registering this (plus the session service below)
 * makes LastChat selectable as the device "Default digital assistant app".
 *
 * The system binds this service when the user picks LastChat as their assistant; the
 * actual per-invocation UI lives in [LastChatVoiceInteractionSessionService].
 */
class LastChatVoiceInteractionService : VoiceInteractionService()
