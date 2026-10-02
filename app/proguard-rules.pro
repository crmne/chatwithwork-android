# Bridge messages are decoded by kotlinx.serialization from JSON the web
# sends; keep the serializers of the app's message classes.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.chatwithwork.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.chatwithwork.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hotwire Native finds fragment destinations by their annotation and
# creates them by reflection.
-keep class com.chatwithwork.app.fragments.** { *; }
-keep @dev.hotwire.navigation.destinations.HotwireDestinationDeepLink class * { *; }

# Play's review library (Joe Masilotti's review-prompt component) refers to an
# annotation from a newer Play services than Firebase brings in. It's only an
# annotation, so nothing breaks without it.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
