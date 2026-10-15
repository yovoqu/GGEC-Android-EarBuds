package com.yovo.biz_appupdate;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.core.YoAppUpdateCore;

import java.io.File;

/**
 * APP 更新模块唯一对外门面。
 */
public final class YoAppUpdate {

    private YoAppUpdate() {
    }

    public static void init(@NonNull Context context, @NonNull Config config) {
        YoAppUpdateCore.install(context, config);
    }

    @NonNull
    public static LaunchSession startLaunchCheck(@NonNull Activity activity,
                                                 @NonNull LaunchCallback callback) {
        return YoAppUpdateCore.get().startLaunchCheck(activity, callback);
    }

    public static void attachLaunchPrompt(@NonNull Intent intent,
                                          @NonNull LaunchPrompt prompt) {
        YoAppUpdateCore.get().attachLaunchPrompt(intent, prompt);
    }

    @NonNull
    public static UpdateUiSession attachUpdateUi(@NonNull Activity activity,
                                                 @Nullable Intent launchIntent,
                                                 @NonNull UpdateUiHost host) {
        return YoAppUpdateCore.get().attachUpdateUi(activity, launchIntent, host);
    }

    public static boolean requestDownloadDirPermissionIfNeeded(@NonNull Activity activity) {
        return YoAppUpdateCore.get().requestDownloadDirPermissionIfNeeded(activity);
    }

    public static boolean requestInstallUnknownSources(@NonNull Activity activity) {
        return YoAppUpdateCore.get().requestInstallUnknownSources(activity);
    }

    @NonNull
    public static File downloadRootDir(@NonNull Context context) {
        return YoAppUpdateCore.get().downloadRootDir();
    }

    @NonNull
    public static String versionName() {
        return YoAppUpdateCore.get().versionName();
    }

    public static int versionCode() {
        return YoAppUpdateCore.get().versionCode();
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
            SELECT_LOCAL_APK,
            NEED_DOWNLOAD_DIR
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

    public interface UpdateUiHost {
        void openSafTreePicker();

        void requestDownloadDirPermission();

        void requestInstallUnknownSources();

        void openAppSettings();
    }

    public interface UpdateUiSession {
        void checkUpdate();

        void detach();

        void onSafTreeResult(boolean granted);

        void onDownloadDirPermissionResult(boolean granted);

        void onDownloadDirPermissionResume();

        void onDownloadDirPermissionPermanentlyDenied();

        void onInstallPermissionResult();
    }

    public interface LaunchCallback {
        void onResult(@NonNull LaunchPrompt prompt);
    }

    public interface LaunchSession {
        void cancel();
    }
}
