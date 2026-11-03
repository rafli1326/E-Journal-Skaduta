# Proguard rules for E-Journal
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn okio.**

# Keep Room entities & DAOs safe from aggressive obfuscation
-keep class com.ejournal.skaduta.data.local.entity.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep WorkManager CoroutineWorker constructor
-keep class com.ejournal.skaduta.worker.DailyReminderWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Security: hapus seluruh pemanggilan Log debug/verbose/info pada release build
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
