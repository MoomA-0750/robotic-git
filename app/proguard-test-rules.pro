# Rules for the instrumentation APK only.
#
# What we want to validate is whether R8 broke the *app* -- above all whether it
# stripped the JGit classes that are only ever reached through ServiceLoader.
# Shrinking the test harness as well adds nothing and actively gets in the way:
# nothing references a JUnit test class, so R8 removes them all and the run
# reports "0 tests" instead of failing.
#
# So: leave the test APK alone, and let the app APK be shrunk normally.
-dontshrink
-dontoptimize
-dontobfuscate

# Absent compile-time annotations, same as the app's rules.
-dontwarn com.google.errorprone.annotations.**
