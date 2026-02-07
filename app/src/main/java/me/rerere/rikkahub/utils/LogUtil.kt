package me.rerere.rikkahub.utils

import android.util.Log
import me.rerere.rikkahub.BuildConfig

/**
 * Logging utility that gates debug logs behind BuildConfig.DEBUG.
 * 
 * In release builds:
 * - d() and i() are no-ops (no debug/info logs)
 * - w() and e() still log (warnings and errors are important)
 * 
 * Usage: Replace Log.d(TAG, msg) with LogUtil.d(TAG, msg)
 */
object LogUtil {
    /**
     * Debug log - only in debug builds
     */
    inline fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message())
        }
    }

    /**
     * Debug log - only in debug builds (simple string version)
     */
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    /**
     * Info log - only in debug builds
     */
    inline fun i(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message())
        }
    }

    /**
     * Info log - only in debug builds (simple string version)
     */
    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }

    /**
     * Warning log - always logs (warnings are important)
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    /**
     * Warning log with exception - always logs
     */
    fun w(tag: String, message: String, throwable: Throwable) {
        Log.w(tag, message, throwable)
    }

    /**
     * Error log - always logs (errors are critical)
     */
    fun e(tag: String, message: String) {
        Log.e(tag, message)
    }

    /**
     * Error log with exception - always logs
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
    }
}
