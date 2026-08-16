# R8 rules for the release build.
#
# The build was never shrunk before, so these rules exist to make the release
# build behave like the debug one rather than to squeeze the APK. Where a
# choice arises between smaller output and code that certainly still works,
# they choose the latter.

# ---- JGit ----
#
# JGit resolves transports, ignore-rule parsers and filesystem probes through
# java.util.ServiceLoader and reflection. R8 cannot see those references, so it
# would strip classes that are only ever named in META-INF/services -- and the
# failure surfaces at runtime, as one operation quietly not working, rather
# than at build time. Keeping the library whole is the safe trade here: the
# app's entire purpose is git.
-keep class org.eclipse.jgit.** { *; }
-keepclassmembers class org.eclipse.jgit.** { *; }
-keepnames class org.eclipse.jgit.**

# Service declarations JGit dispatches through.
-keep,allowobfuscation @interface org.eclipse.jgit.**

# JGit compiles against optional dependencies the app does not ship: an Apache
# HTTP transport, a servlet container, and Bouncy Castle for signed objects.
# They are genuinely absent, and R8 must not treat that as an error.
-dontwarn org.eclipse.jgit.**
-dontwarn org.apache.**
-dontwarn javax.servlet.**
-dontwarn org.bouncycastle.**
-dontwarn org.slf4j.**
-dontwarn com.jcraft.**
-dontwarn org.ietf.jgss.**

# ---- Reflection-bearing attributes ----
#
# Generic signatures are needed by kotlinx.serialization and Retrofit to
# reconstruct types at runtime; annotations drive both.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# ---- kotlinx.serialization ----
#
# Serializers are generated as companions and looked up by name.
-keepclassmembers class com.example.roboticgit.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.roboticgit.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.roboticgit.data.model.**$$serializer { *; }

# ---- Line numbers ----
#
# A stripped stack trace from a release build is close to useless when the app
# is only ever debugged by the person who wrote it.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Tink (behind EncryptedSharedPreferences) ----
#
# Tink is compiled against Error Prone's annotations, which exist only at
# compile time and are not on the runtime classpath. Nothing reads them.
-dontwarn com.google.errorprone.annotations.**

# ---- Instrumentation runner ----
#
# AndroidJUnitRunner touches androidx.tracing.Trace before the first test runs.
# The app itself never does, so R8 drops it -- and the test APK cannot supply
# its own copy, because AGP excludes from the test APK anything already present
# in the app's dependency graph. The class can therefore only be kept here.
# It is one class; the cost is negligible next to being able to run the UI
# tests against the build that actually ships.
-keep class androidx.tracing.Trace { *; }
