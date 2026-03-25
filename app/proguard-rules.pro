# ============================================================================
# Part 3 – Obfuscation and Analysis Evasion
# ============================================================================

# ── Aggressive renaming ──────────────────────────────────────────────────────
# Repackage all classes into the root package so the original package structure
# is not visible to a reverse-engineer using JADX or dex2jar.
-repackageclasses ''
-allowaccessmodification

# Run the optimiser multiple times to strip dead code and inline trivial methods
-optimizationpasses 5

# Strip SourceFile and LineNumberTable attributes entirely so decompilers cannot
# map obfuscated names back to the original source file or line numbers.
-keepattributes !SourceFile,!LineNumberTable

# ── Android entry-points that must be kept ───────────────────────────────────
# The Manifest references these by name; ProGuard must not rename them.
-keep public class com.example.inf2215.MainActivity
-keep public class com.example.inf2215.RunningService
-keep public class com.example.inf2215.StealthService

# ── Kotlin metadata / coroutines ─────────────────────────────────────────────
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }

# ── Firebase (must survive obfuscation to communicate with backend) ───────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── Jetpack Compose (reflection-heavy; keep annotated composables) ────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Coil image loader ────────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── Gson (used by some Firebase internals) ───────────────────────────────────
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── Data models used with Firestore serialisation ────────────────────────────
-keep class com.example.inf2215.Models** { *; }

# ── Remove all Log.* calls from the release build ───────────────────────────
# This prevents revealing internal class names and logic via logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}