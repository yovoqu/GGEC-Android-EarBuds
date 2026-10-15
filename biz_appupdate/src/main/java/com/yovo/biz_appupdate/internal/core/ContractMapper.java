package com.yovo.biz_appupdate.internal.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.YoAppUpdate;
import com.yovo.biz_appupdate.internal.model.AppUpdateConfig;
import com.yovo.biz_appupdate.internal.model.DownloadDirSpec;
import com.yovo.biz_appupdate.internal.model.LaunchDecision;
import com.yovo.biz_appupdate.internal.model.LocalApk;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;
import com.yovo.biz_appupdate.internal.model.StoragePolicy;

import java.io.File;
import java.util.Collections;

/**
 * 对外契约与内部模型的双向映射。
 */
public final class ContractMapper {

    private ContractMapper() {
    }

    @NonNull
    public static AppUpdateConfig toInternalConfig(@NonNull YoAppUpdate.Config config) {
        return new AppUpdateConfig.Builder()
                .fileProviderAuthority(config.fileProviderAuthority())
                .apkFilePrefix(config.apkFilePrefix())
                .downloadUrl(config.downloadUrl())
                .downloadDir(toInternalDir(config.downloadDir()))
                .autoInstallOnLaunch(config.autoInstallOnLaunch())
                .wifiOnly(config.wifiOnly())
                .splashMinDelayMs(config.splashMinDelayMs())
                .splashCheckTimeoutMs(config.splashCheckTimeoutMs())
                .storagePolicy(toInternalPolicy(config.storagePolicy()))
                .build();
    }

    @NonNull
    private static StoragePolicy toInternalPolicy(@NonNull YoAppUpdate.StoragePolicy policy) {
        switch (policy) {
            case MULTI_VERSION:
                return StoragePolicy.MULTI_VERSION;
            case SINGLE_LATEST:
            default:
                return StoragePolicy.SINGLE_LATEST;
        }
    }

    @NonNull
    public static DownloadDirSpec toInternalDir(@NonNull YoAppUpdate.DownloadDir dir) {
        switch (dir.type()) {
            case ABSOLUTE:
                return dir.dir() != null
                        ? DownloadDirSpec.absolute(dir.dir())
                        : DownloadDirSpec.appPrivate("AppUpdate");
            case SAF:
                return DownloadDirSpec.saf();
            case APP_PRIVATE:
            default:
                return dir.dir() != null
                        ? DownloadDirSpec.appPrivate(dir.dir().getPath())
                        : DownloadDirSpec.appPrivate("AppUpdate");
        }
    }

    @NonNull
    public static YoAppUpdate.Info toPublicInfo(@NonNull RemoteUpdate update) {
        return new YoAppUpdate.Info(
                update.version(),
                update.versionCode(),
                update.description(),
                update.downloadUrl(),
                update.forceUpdate(),
                update.md5(),
                update.sha256(),
                update.sizeBytes());
    }

    @NonNull
    public static RemoteUpdate toRemoteUpdate(@NonNull YoAppUpdate.Info info) {
        return new RemoteUpdate(
                true,
                info.version(),
                info.versionCode(),
                info.description(),
                info.downloadUrl(),
                info.forceUpdate(),
                info.md5(),
                info.sha256(),
                info.sizeBytes());
    }

    @NonNull
    public static YoAppUpdate.LaunchPrompt toPublicPrompt(@NonNull LaunchDecision decision) {
        YoAppUpdate.LaunchPrompt.Action action;
        switch (decision.action()) {
            case INSTALL_DIRECT:
                action = YoAppUpdate.LaunchPrompt.Action.INSTALL_DIRECT;
                break;
            case SHOW_UPDATE_DIALOG:
                action = YoAppUpdate.LaunchPrompt.Action.SHOW_UPDATE;
                break;
            case SELECT_LOCAL_APK:
                action = YoAppUpdate.LaunchPrompt.Action.SELECT_LOCAL_APK;
                break;
            case NEED_DOWNLOAD_DIR:
                action = YoAppUpdate.LaunchPrompt.Action.NEED_DOWNLOAD_DIR;
                break;
            case NONE:
            default:
                action = YoAppUpdate.LaunchPrompt.Action.NONE;
                break;
        }
        LocalApk local = decision.localApk();
        return new YoAppUpdate.LaunchPrompt(
                action,
                decision.update() != null ? toPublicInfo(decision.update()) : null,
                local != null ? local.file() : null);
    }

    @NonNull
    public static LaunchDecision toLaunchDecision(@NonNull YoAppUpdate.LaunchPrompt prompt) {
        switch (prompt.action()) {
            case INSTALL_DIRECT:
                if (prompt.update() != null && prompt.apkFile() != null) {
                    RemoteUpdate remote = toRemoteUpdate(prompt.update());
                    return LaunchDecision.installDirect(
                            remote,
                            new LocalApk(
                                    prompt.apkFile(),
                                    prompt.apkFile().lastModified(),
                                    remote.version()));
                }
                return LaunchDecision.none();
            case SHOW_UPDATE:
                return prompt.update() != null
                        ? LaunchDecision.showUpdate(toRemoteUpdate(prompt.update()))
                        : LaunchDecision.none();
            case SELECT_LOCAL_APK:
                return LaunchDecision.selectLocalApks(Collections.emptyList());
            case NEED_DOWNLOAD_DIR:
                return LaunchDecision.needDownloadDir();
            case NONE:
            default:
                return LaunchDecision.none();
        }
    }

}
