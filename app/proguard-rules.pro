# web3j — keep all classes intact for secp256k1 and RLP encoding
-keepattributes Signature
-keepattributes *Annotation*
-keep class org.web3j.** { *; }
-keep interface org.web3j.** { *; }
-dontwarn org.web3j.**

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-keep interface org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep all database entities and their fields for Room reflection
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Database class * { *; }

# Keep data models used in JSON serialization
-keep class com.sadhu.nftautopilot.engine.** { *; }
-keep class com.sadhu.nftautopilot.data.database.entity.** { *; }

# Gson
-keepattributes EnclosingMethod
-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Hilt
-keep class * extends dagger.hilt.android.internal.managers.** { *; }
-keep class dagger.hilt.** { *; }
-dontwarn dagger.hilt.**

# WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Kotlin metadata
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# Prevent removal of crash-recovery state
-keep class com.sadhu.nftautopilot.engine.automation.** { *; }
-keep class com.sadhu.nftautopilot.service.** { *; }

# Remove all logging in release
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
