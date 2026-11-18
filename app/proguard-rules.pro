# kotlinx.serialization keep rules
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class io.github.jsnjack.wallet.**$$serializer { *; }
-keepclassmembers class io.github.jsnjack.wallet.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.jsnjack.wallet.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# zxing-cpp builds its result and option classes from native code by name.
-keep class zxingcpp.** { *; }
