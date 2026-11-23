# Preserve source file and line numbers for readable Play Console stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.fillmark.app.**$$serializer { *; }
-keepclassmembers class com.fillmark.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.fillmark.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database & Entities
-keep class com.fillmark.app.data.local.FillmarkDatabase { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class com.fillmark.app.data.local.FillmarkDatabase_Impl { *; }
-keep class com.fillmark.app.data.local.entity.** { *; }

# WorkManager Workers
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Jetpack Glance Widgets & ActionCallbacks
-keep class com.fillmark.app.widget.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}

# ViewModels
-keep class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# Health Connect Client & Records
-keep class androidx.health.connect.** { *; }
-dontwarn androidx.health.connect.**
