-dontwarn okhttp3.**
-dontwarn okio.**

-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.paiwand.translate.**$$serializer { *; }
-keepclassmembers class com.paiwand.translate.** {
    *** Companion;
}
