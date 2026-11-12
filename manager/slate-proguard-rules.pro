-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keep class moe.shizuku.api.BinderContainer { *; }
-keep class rikka.shizuku.ShizukuProvider { *; }
-keep class moe.shizuku.manager.phonefix.SlateProvider { *; }

# Instantiated by name by the Shizuku server (user service).
-keep class moe.shizuku.manager.phonefix.ShellService { *; }
