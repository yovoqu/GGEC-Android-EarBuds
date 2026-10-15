-keepclassmembers class com.yovo.biz_appupdate.BuildConfig {
    public static java.lang.String PGY_API_KEY;
    public static java.lang.String PGY_APP_KEY;
}

# APP 更新 SDK 对外公开面（api），宿主混淆不破坏契约
-keep public class com.yovo.biz_appupdate.** { public protected *; }

