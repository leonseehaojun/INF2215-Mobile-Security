# ============================================================================
# Part 3 – Obfuscation & Analysis Evasion
# ProGuard / R8 rules for the release build
# ============================================================================

# ---------------------------------------------------------------------------
# General optimisation & obfuscation settings
# ---------------------------------------------------------------------------

# Flatten the class hierarchy where safe to do so
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''          # move all classes into the default package
-overloadaggressively         # reuse the same obfuscated names across unrelated classes

# Replace every source-file attribute in the compiled bytecode with the
# generic token "SourceFile" so stack traces reveal no original filenames.
-renamesourcefileattribute SourceFile

# Remove all android.util.Log calls so log messages cannot reveal logic
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ---------------------------------------------------------------------------
# Keep rules – classes that must not be renamed or removed
# ---------------------------------------------------------------------------

# Android entry-points referenced by the OS via the manifest
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Application

# Jetpack Compose infrastructure (internals are reflectively accessed)
-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}

# Firebase SDK – must remain intact for reflection-based initialisation
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Google Maps Compose
-keep class com.google.maps.android.** { *; }
-dontwarn com.google.maps.android.**

# Coil image loader
-keep class coil.** { *; }
-dontwarn coil.**

# Kotlin coroutines & stdlib
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# Kotlin serialisation / reflection helpers
-keepclassmembers class ** implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ---------------------------------------------------------------------------
# Suppress warnings for optional dependencies
# ---------------------------------------------------------------------------
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**