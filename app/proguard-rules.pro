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
