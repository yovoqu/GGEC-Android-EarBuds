package com.yovo.biz_appupdate;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.api.UpdateUiPort;
import com.yovo.biz_appupdate.api.UpdateUiSession;
import com.yovo.biz_appupdate.internal.core.YoAppUpdateCore;

import java.io.File;

/**
 * APP 更新模块唯一对外门面（实例）。
 *
 * <p>没有静态单例：装配根创建一次、持有一个实例，模块内部状态都在这个实例的
 * {@link YoAppUpdateCore} 里。多实例各自独立，互不可见。</p>
 */
public final class YoAppUpdate {

    private final YoAppUpdateCore core;

    private YoAppUpdate(@NonNull YoAppUpdateCore core) {
        this.core = core;
    }

    /** 创建门面：内部建图（目录、端口适配、引擎）并返回可控实例。 */
    @NonNull
    public static YoAppUpdate create(@NonNull Context context, @NonNull Config config) {
        return new YoAppUpdate(YoAppUpdateCore.create(context, config));
    }

    @NonNull
    public LaunchSession startLaunchCheck(@NonNull Activity activity,
                                          @NonNull LaunchCallback callback) {
        return core.startLaunchCheck(activity, callback);
    }

    public void attachLaunchPrompt(@NonNull Intent intent, @NonNull LaunchPrompt prompt) {
        core.attachLaunchPrompt(intent, prompt);
    }

    /**
     * 绑定更新 UI：状态、事件、系统级 effect 全部经 {@code port} 推给宿主。
     *
     * @param context 宿主上下文（安装要它）；一般是 Activity
     * @param launchIntent 启动 Intent，可为 null；其中的启动更新决策会被消费
     * @param port 宿主的渲染实现
     */
    @NonNull
    public UpdateUiSession attachUpdateUi(@NonNull Context context,
                                          @Nullable Intent launchIntent,
                                          @NonNull UpdateUiPort port) {
        return core.attachUpdateUi(context, launchIntent, port);
    }

    public boolean requestInstallUnknownSources(@NonNull Activity activity) {
        return core.requestInstallUnknownSources(activity);
    }

    @NonNull
    public File downloadRootDir() {
        return core.downloadRootDir();
    }

    @NonNull
    public String versionName() {
        return core.versionName();
    }

    public int versionCode() {
        return core.versionCode();
    }

    public enum StoragePolicy {
        SINGLE_LATEST,
        MULTI_VERSION
    }

    public static final class Config {

        private final String fileProviderAuthority;
        private final String apkFilePrefix;
        private final String downloadUrl;
        private final DownloadDir downloadDir;
        private final boolean autoInstallOnLaunch;
        private final boolean wifiOnly;
        private final long splashMinDelayMs;
        private final long splashCheckTimeoutMs;
        private final StoragePolicy storagePolicy;

        private Config(@NonNull Builder builder) {
            fileProviderAuthority = builder.fileProviderAuthority;
            apkFilePrefix = builder.apkFilePrefix;
            downloadUrl = builder.downloadUrl;
            downloadDir = builder.downloadDir;
            autoInstallOnLaunch = builder.autoInstallOnLaunch;
            wifiOnly = builder.wifiOnly;
            splashMinDelayMs = builder.splashMinDelayMs;
            splashCheckTimeoutMs = builder.splashCheckTimeoutMs;
            storagePolicy = builder.storagePolicy;
        }

        @NonNull
        public String fileProviderAuthority() {
            return fileProviderAuthority;
        }

        @NonNull
        public String apkFilePrefix() {
            return apkFilePrefix;
        }

        @NonNull
        public String downloadUrl() {
            return downloadUrl;
        }

        @NonNull
        public DownloadDir downloadDir() {
            return downloadDir;
        }

        public boolean autoInstallOnLaunch() {
            return autoInstallOnLaunch;
        }

        public boolean wifiOnly() {
            return wifiOnly;
        }

        public long splashMinDelayMs() {
            return splashMinDelayMs;
        }

        public long splashCheckTimeoutMs() {
            return splashCheckTimeoutMs;
        }

        @NonNull
        public StoragePolicy storagePolicy() {
            return storagePolicy;
        }

        public static final class Builder {

            private String fileProviderAuthority = "";
            private String apkFilePrefix = "App";
            private String downloadUrl = "";
            private DownloadDir downloadDir = DownloadDir.appPrivate("AppUpdate");
            private boolean autoInstallOnLaunch = true;
            private boolean wifiOnly = false;
            private long splashMinDelayMs = 1000L;
            private long splashCheckTimeoutMs = 5000L;
            private StoragePolicy storagePolicy = StoragePolicy.SINGLE_LATEST;

            public Builder fileProviderAuthority(@NonNull String authority) {
                fileProviderAuthority = authority;
                return this;
            }

            public Builder apkFilePrefix(@NonNull String prefix) {
                apkFilePrefix = prefix;
                return this;
            }

            public Builder downloadUrl(@NonNull String url) {
                downloadUrl = url;
                return this;
            }

            public Builder downloadDir(@NonNull DownloadDir dir) {
                downloadDir = dir;
                return this;
            }

            public Builder safDownloadDir() {
                downloadDir = DownloadDir.saf();
                return this;
            }

            public Builder autoInstallOnLaunch(boolean enabled) {
                autoInstallOnLaunch = enabled;
                return this;
            }

            public Builder wifiOnly(boolean enabled) {
                wifiOnly = enabled;
                return this;
            }

            public Builder splashMinDelayMs(long millis) {
                splashMinDelayMs = millis;
                return this;
            }

            public Builder splashCheckTimeoutMs(long millis) {
                splashCheckTimeoutMs = millis;
                return this;
            }

            public Builder storagePolicy(@NonNull StoragePolicy policy) {
                storagePolicy = policy;
                return this;
            }

            @NonNull
            public Config build() {
                if (fileProviderAuthority.isEmpty()) {
                    throw new IllegalStateException("fileProviderAuthority is required");
                }
                if (apkFilePrefix.trim().isEmpty()) {
                    throw new IllegalStateException("apkFilePrefix is required");
                }
                if (downloadUrl.trim().isEmpty()) {
                    throw new IllegalStateException("downloadUrl is required");
                }
                return new Config(this);
            }
        }
    }

    public static final class DownloadDir {

        public enum Type {
            APP_PRIVATE,
            ABSOLUTE,
            SAF
        }

        @NonNull
        private final Type type;
        @Nullable
        private final File dir;

        private DownloadDir(@NonNull Type type, @Nullable File dir) {
            this.type = type;
            this.dir = dir;
        }

        @NonNull
        public static DownloadDir appPrivate(@NonNull String name) {
            return new DownloadDir(Type.APP_PRIVATE, new File(name));
        }

        @NonNull
        public static DownloadDir absolute(@NonNull File dir) {
            return new DownloadDir(Type.ABSOLUTE, dir);
        }

        @NonNull
        public static DownloadDir saf() {
            return new DownloadDir(Type.SAF, null);
        }

        @NonNull
        public Type type() {
            return type;
        }

        @Nullable
        public File dir() {
            return dir;
        }
    }

    public static final class Info {

        @NonNull
        private final String version;
        private final int versionCode;
        @NonNull
        private final String description;
        @NonNull
        private final String downloadUrl;
        private final boolean forceUpdate;
        @NonNull
        private final String md5;
        @NonNull
        private final String sha256;
        private final long sizeBytes;

        public Info(@NonNull String version,
                    int versionCode,
                    @NonNull String description,
                    @NonNull String downloadUrl,
                    boolean forceUpdate,
                    @NonNull String md5,
                    @NonNull String sha256,
                    long sizeBytes) {
            this.version = version;
            this.versionCode = versionCode;
            this.description = description;
            this.downloadUrl = downloadUrl;
            this.forceUpdate = forceUpdate;
            this.md5 = md5;
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
        }

        @NonNull
        public String version() {
            return version;
        }

        public int versionCode() {
            return versionCode;
        }

        @NonNull
        public String description() {
            return description;
        }

        @NonNull
        public String downloadUrl() {
            return downloadUrl;
        }

        public boolean forceUpdate() {
            return forceUpdate;
        }

        @NonNull
        public String md5() {
            return md5;
        }

        @NonNull
        public String sha256() {
            return sha256;
        }

        public long sizeBytes() {
            return sizeBytes;
        }
    }

    public static final class LaunchPrompt {

        public enum Action {
            NONE,
            SHOW_UPDATE,
            INSTALL_DIRECT,
            SELECT_LOCAL_APK
        }

        @NonNull
        private final Action action;
        @Nullable
        private final Info update;
        @Nullable
        private final File apkFile;

        public LaunchPrompt(@NonNull Action action,
                            @Nullable Info update,
                            @Nullable File apkFile) {
            this.action = action;
            this.update = update;
            this.apkFile = apkFile;
        }

        @NonNull
        public Action action() {
            return action;
        }

        @Nullable
        public Info update() {
            return update;
        }

        @Nullable
        public File apkFile() {
            return apkFile;
        }
    }

    public interface LaunchCallback {
        void onResult(@NonNull LaunchPrompt prompt);
    }

    public interface LaunchSession {
        void cancel();
    }
}
