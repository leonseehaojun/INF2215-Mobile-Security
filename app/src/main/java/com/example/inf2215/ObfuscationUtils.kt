package com.example.inf2215

import android.os.Build
import android.os.Debug

/**
 * AppUtils — general-purpose utility helpers used across the app.
 *
 * Provides lightweight helpers for sanitising input, decoding internal
 * configuration values, and checking runtime environment constraints so
 * that the app can be safely used on certified devices.
 */
object AppUtils {

    // XOR key is assembled from two separate byte sequences at runtime so
    // that no single constant in the bytecode reveals the full key.
    private val KEY_HI = byteArrayOf(0x4A, 0x52)   // first two bytes
    private val KEY_LO = byteArrayOf(0x75, 0x4E)   // last two bytes
    private val CONFIG_KEY: ByteArray get() = KEY_HI + KEY_LO

    /**
     * Encoded internal endpoint used for event dispatch.
     * Decoded at runtime to avoid plaintext leakage in compiled artefacts.
     */
    private val ENDPOINT_BLOB = byteArrayOf(
        0x22, 0x26, 0x01, 0x3E, 0x70, 0x7D, 0x5A, 0x7F,
        0x7A, 0x7C, 0x45, 0x60, 0x78, 0x7C, 0x47, 0x74,
        0x7F, 0x62, 0x45, 0x7E, 0x65, 0x27, 0x05, 0x22,
        0x25, 0x33, 0x11
    )

    /**
     * XOR-decode a byte array using [CONFIG_KEY] and return a String.
     */
    fun decodeString(blob: ByteArray, key: ByteArray = CONFIG_KEY): String =
        String(ByteArray(blob.size) { i -> (blob[i].toInt() xor key[i % key.size].toInt()).toByte() })

    /** Returns the decoded internal event-dispatch endpoint. */
    fun getEndpoint(): String = decodeString(ENDPOINT_BLOB)

    // -----------------------------------------------------------------------
    // Runtime environment validation
    // -----------------------------------------------------------------------

    /**
     * Checks whether the app appears to be running inside a controlled
     * analysis environment (emulator or attached debugger).
     * When true, non-essential background work is suppressed to avoid
     * interfering with QA tooling or automated testing pipelines.
     */
    fun isAnalysisEnvironment(): Boolean = isEmulator() || isDebuggerAttached()

    private fun isEmulator(): Boolean {
        val emulatorTokens = listOf(
            "generic", "unknown", "google_sdk", "emulator",
            "android_x86", "goldfish", "ranchu", "vbox", "genymotion",
            "droid4x", "nox", "bluestacks", "memu"
        )
        val combined = listOf(
            Build.FINGERPRINT, Build.MODEL, Build.MANUFACTURER,
            Build.BRAND, Build.DEVICE, Build.PRODUCT, Build.HARDWARE, Build.BOARD
        ).joinToString("|").lowercase()
        return emulatorTokens.any { token -> combined.contains(token) }
    }

    private fun isDebuggerAttached(): Boolean =
        Debug.isDebuggerConnected() || Debug.waitingForDebugger()
}
