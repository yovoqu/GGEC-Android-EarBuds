# lib_common 对外门面与契约模型 keep 规则。

# 对外静态门面
-keep public class com.yovo.lib_common.download.YoDownload { public protected *; }
-keep public class com.yovo.lib_common.archive.YoArchive { public protected *; }
-keep public class com.yovo.lib_common.log.YoLog { public protected *; }
-keep public class com.yovo.lib_common.system.permission.YoPermission { public protected *; }
-keep public class com.yovo.lib_common.system.file.YoFileManager { public protected *; }
-keep public class com.yovo.lib_common.system.share.YoShare { public protected *; }
-keep public class com.yovo.lib_common.system.receive.YoReceive { public protected *; }
-keep public class com.yovo.lib_common.system.screen_on.YoScreenOn { public protected *; }
-keep public class com.yovo.lib_common.system.download.YoSystemDownload { public protected *; }
-keep public class com.yovo.lib_common.system.keepalive.YoKeepAlive { public protected *; }
-keep public class com.yovo.lib_common.system.refresh_rate.YoRefreshRate { public protected *; }

# 对外契约模型
-keep public class com.yovo.lib_common.archive.model.** { public protected *; }
-keep public class com.yovo.lib_common.log.model.** { public protected *; }
-keep public class com.yovo.lib_common.system.permission.model.** { public protected *; }
-keep public class com.yovo.lib_common.system.file.model.** { public protected *; }
-keep public class com.yovo.lib_common.system.share.model.** { public protected *; }
-keep public class com.yovo.lib_common.system.receive.model.** { public protected *; }
-keep public class com.yovo.lib_common.system.download.model.** { public protected *; }
-keep public class com.yovo.lib_common.system.keepalive.model.** { public protected *; }
-keep public class com.yovo.lib_common.system.refresh_rate.model.** { public protected *; }

# Manifest 扩展点
-keep public class com.yovo.lib_common.system.keepalive.service.BaseKeepAliveService { public protected *; }
-keep public class com.yovo.lib_common.system.keepalive.receiver.BaseKeepAliveActionReceiver { public protected *; }

# 枚举按名称保留，便于日志与序列化
-keepclassmembers enum com.yovo.lib_common.**.model.** {
    public *;
}
