# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class org.audimmory.mobile.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}
