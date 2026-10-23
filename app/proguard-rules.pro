# kotlinx.serialization: keep generated serializers for @Serializable classes (models and nav routes).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }
-keep class io.github.plumblossomcat.torbox.core.api.** { *; }
-keep class io.github.plumblossomcat.torbox.ui.nav.** { *; }

# OkHttp platform probes
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
