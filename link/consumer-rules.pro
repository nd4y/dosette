# kotlinx.serialization: both apps decode the link DTOs through their
# generated serializers, which R8 would otherwise strip.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class icu.nd4y.dosette.link.** {
    *** Companion;
}
-keepclasseswithmembers class icu.nd4y.dosette.link.** {
    kotlinx.serialization.KSerializer serializer(...);
}
