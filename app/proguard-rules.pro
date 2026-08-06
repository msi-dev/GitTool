# Retrofit rules
-keepattributes Signature, InnerClasses, EnclosingMethod
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Moshi serialization keeping
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier class * { *; }

# Keep remote API model DTOs from reflection failures in shrinking
-keep class com.msi.gittool.data.remote.** { *; }

# Security Crypto rules
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.ParametersAreNonnullByDefault

# Security Script preserving rules
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.msi.gittool.security.NativeSecurity { *; }
-keep class com.msi.gittool.** { *; }

# JGit rules
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
-dontwarn com.jcraft.jsch.**
-dontwarn org.slf4j.**
-dontwarn java.awt.**


