# Project-specific R8 rules.
# Hilt, Room and Compose ship their own consumer rules.

# kotlinx.serialization: keep generated serializers for the backup DTOs.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class icu.nd4y.dosette.domain.backup.** {
    *** Companion;
}
-keepclasseswithmembers class icu.nd4y.dosette.domain.backup.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# SnakeYAML engine used by kaml reflects on its own classes.
-keep class it.krzeminski.snakeyaml.engine.** { *; }
-dontwarn it.krzeminski.snakeyaml.engine.**

# Glance renders app widgets through WorkManager sessions; WorkManager
# instantiates workers and input mergers reflectively by class name, and
# Glance instantiates action callbacks the same way. Without these the
# placed widget silently stays on the loading layout in release builds
# ("Could not create Input Merger androidx.work.OverwritingInputMerger").
-keep class * implements androidx.glance.appwidget.action.ActionCallback { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.InputMerger { *; }
