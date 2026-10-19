# Libraries (kotlinx.serialization, Retrofit, OkHttp, Media3, Coil, Moshi) ship their own
# consumer rules. These are a safety net for our own JSON models, which are looked up by
# their generated serializers.

# kotlinx.serialization: keep generated serializers and the companion accessors of our models.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keep,includedescriptorclasses class com.portalcast.tv.**$$serializer { *; }
-keepclassmembers class com.portalcast.tv.** {
    *** Companion;
}
-keepclasseswithmembers class com.portalcast.tv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit service interface: method signatures/generics are read reflectively.
-keep,allowobfuscation interface com.portalcast.tv.data.net.ApiService
-keepclassmembers,allowobfuscation interface com.portalcast.tv.data.net.ApiService { *; }

# Crash reports stay readable (line numbers kept, source file name hidden).
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# lifecycle-runtime-compose 2.8 finds Compose UI 1.6's LocalLifecycleOwner by REFLECTION
# (to share one CompositionLocal); R8 renamed it and every collectAsStateWithLifecycle()
# crashed with "CompositionLocal LocalLifecycleOwner not present". Drop once on Compose 1.7+.
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt { *; }
