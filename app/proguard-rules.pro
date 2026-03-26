# Firebase Rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.firebase.** { *; }

# Keep data models and members for Firebase serialisation
-keep class com.example.inf2215.FeedPost { *; }
-keep class com.example.inf2215.Comment { *; }
-keep class com.example.inf2215.ReportItem { *; }
-keep class com.example.inf2215.ProfileRunItem { *; }
-keep class com.example.inf2215.ChatThread { *; }
-keep class com.example.inf2215.ChatMessage { *; }
-keep class com.example.inf2215.GroupThread { *; }
-keep class com.example.inf2215.GroupThreadComment { *; }
-keep class com.example.inf2215.Announcement { *; }
-keep class com.example.inf2215.GroupSelection { *; }
-keep class com.example.inf2215.SimpleUser { *; }
-keep class com.example.inf2215.GroupCardModel { *; }
-keep class com.example.inf2215.NavItem { *; }

# Keep all public fields and methods in model classes
-keepclassmembers class com.example.inf2215.** {
    public <fields>;
    public <methods>;
}

# Google Play Services
-keep class com.google.android.gms.** { *; }
