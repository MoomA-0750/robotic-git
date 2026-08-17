# Extra rules applied to the *app* APK only when instrumentation tests are run
# against release (`-PtestBuildType=release`). The shipping release build never
# sees this file.
#
# The two APKs share one class loader, and any library both of them use is
# packaged in the app APK alone. R8 shrinks the app APK against the app's own
# code, so whatever such a library holds that only the *test* APK reaches is
# already gone when the harness starts:
#
#     NoClassDefFoundError: Lkotlin/LazyKt;
#         androidx.test.platform.io.OutputDirCalculator.<init>
#         androidx.test.runner.AndroidJUnitRunner.onStart
#
#     NoClassDefFoundError: Lkotlinx/coroutines/JobKt;
#         androidx.compose.ui.test.IdlingResourceRegistry.<init>
#
#     NoClassDefFoundError: Lcom/google/common/util/concurrent/ListenableFuture;
#         java.lang.Class.classForName
#
# The first one crashes the runner before a single test is collected, so the run
# reports "0 tests" rather than a failure -- the harness never gets as far as
# looking at the app at all.
#
# Rather than chase these one at a time, draw the line by owner: keep whole the
# frameworks the two APKs share, and let R8 shrink the code this run is actually
# asking about. JGit, OkHttp, Retrofit, kotlinx.serialization and this app's own
# classes all still go through R8 exactly as they do in a shipping release, so a
# ServiceLoader lookup that R8 broke would still show up here. What is given up
# is fidelity in the parts the harness itself needs, which cannot answer that
# question anyway.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.** { *; }
-keep class com.google.common.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.coroutines.**
-dontwarn com.google.common.**

# Renaming and optimization are separate problems with the same shape: the test
# APK calls into the app by the signatures it was compiled against, and R8's
# other two passes are free to change them.
#
# Renaming, because the test APK is shrunk by a second R8 pass carrying
# `-dontobfuscate` -- necessary there, since nothing references a JUnit test
# class and R8 would otherwise delete every one of them. R8 ignores the app's
# mapping file when it is told not to obfuscate, so app calls land on names that
# no longer exist (`Lc0/d;` in the failure below).
#
# Optimization, because `proguard-android-optimize.txt` lets R8 inline a method
# with one caller. `AuthManager.setDefaultCloneDir` has exactly one -- the
# settings view model -- so it is inlined out of existence and the method the
# test calls is simply absent:
#
#     NoSuchMethodError: No virtual method setDefaultCloneDir(Ljava/lang/String;)V
#         in class Lcom/example/roboticgit/data/AuthManager;
#         com.example.roboticgit.ui.OnDeviceRepositories.freshWorkspace
#
# Neither says anything about whether the app survives R8. What remains after
# switching both off is the shrink, which is the question: an entry R8 deleted
# because only a ServiceLoader file names it is deleted here exactly as it would
# be in a shipping build.
-dontobfuscate
-dontoptimize

# One more of the same: the Compose compiler puts a `$stable` field on every
# class it can reason about, and code that composes with such a class reads it.
# The app reads its own; nothing in the app reads the one on ValidationStatus's
# subclasses, because only the tests compose those directly, so the shrink drops
# the field and the test faults on the read:
#
#     NoSuchFieldError: No field $stable of type I
#         in class Lcom/example/roboticgit/ui/viewmodel/ValidationStatus$Idle;
#
# The field is generated, not written, so keeping it changes nothing about what
# is being tested.
-keepclassmembers class com.example.roboticgit.** {
    static int $stable;
}
