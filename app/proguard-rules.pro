# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Retrofit and Gson classes
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class retrofit2.** { *; }

# Keep Room classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep data classes
-keep class dev.aura.auradroid.data.model.** { *; }

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Payloads persisted to Room as reflective Gson JSON (EventSink.kt). R8 would
# rename their fields and stored rows would stop round-tripping across builds.
-keep class dev.aura.auradroid.data.repository.ToolPayload { *; }
-keep class dev.aura.auradroid.data.repository.PlanPayload { *; }
-keep class dev.aura.auradroid.data.repository.ArtifactPayload { *; }
-keep class dev.aura.auradroid.data.repository.StepSnapshot { *; }
