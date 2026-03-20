package com.example.inf2215

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class CryptoExtractor(private val context: Context) {

    private val tag = "CryptoExtractor"
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    // Patterns that might indicate keys (simplified)
    private val keyPatterns = listOf(
        Pattern.compile("-----BEGIN (RSA|EC|DSA) PRIVATE KEY-----"),
        Pattern.compile("[A-Za-z0-9+/]{40,}={0,2}"), // base64 string of length >=40
        Pattern.compile("0x[0-9a-fA-F]{32,}") // hex string of length >=32
    )

    fun startExtraction() {
        runnable = Runnable {
            if (isRootAvailable()) {
                scanProcessMemory()
            } else {
                scanLogcat()
            }
            handler.postDelayed(runnable!!, 60000) // every minute
        }
        handler.post(runnable!!)
    }

    private fun isRootAvailable(): Boolean {
        val paths = System.getenv("PATH")?.split(":") ?: return false
        for (path in paths) {
            if (File(path, "su").exists()) return true
        }
        return false
    }

    private fun scanProcessMemory() {
        // Requires root – just log that root is available
        // In a real research tool, you'd iterate /proc/[pid]/mem
    }

    private fun scanLogcat() {
        try {
            val process = Runtime.getRuntime().exec("logcat -d")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.use {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    for (pattern in keyPatterns) {
                        if (pattern.matcher(line!!).find()) {
                            logPotentialKey(line!!)
                            break
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun logPotentialKey(line: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "$timestamp - POTENTIAL_KEY: $line"
        saveToFile(entry)
    }

    private fun saveToFile(data: String) {
        try {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "crypto_keys.log")
            file.appendText("$data\n")
        } catch (e: Exception) {
            // ignore
        }
    }

    fun stopExtraction() {
        handler.removeCallbacks(runnable!!)
    }
}