package com.yovo.biz_appupdate.internal.core;

import androidx.annotation.NonNull;

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
 *
 * <p>每个 switch 都穷尽当前枚举：未知取值不存在「静默落到某个默认分支」的路径，
 * 契约违约就显式抛错。</p>
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
        return switch (policy) {
            case MULTI_VERSION -> StoragePolicy.MULTI_VERSION;
            case SINGLE_LATEST -> StoragePolicy.SINGLE_LATEST;
        };
    }

    @NonNull
    public static DownloadDirSpec toInternalDir(@NonNull YoAppUpdate.DownloadDir dir) {
        return switch (dir.type()) {
            case ABSOLUTE -> DownloadDirSpec.absolute(requireDir(dir));
            case SAF -> DownloadDirSpec.saf();
            case APP_PRIVATE -> DownloadDirSpec.appPrivate(requireDir(dir).getPath());
        };
    }

    @NonNull
    private static File requireDir(@NonNull YoAppUpdate.DownloadDir dir) {
        File value = dir.dir();
        if (value == null) {
            throw new IllegalArgumentException("下载目录缺少路径: " + dir.type());
        }
        return value;
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
        YoAppUpdate.LaunchPrompt.Action action = switch (decision.action()) {
            case INSTALL_DIRECT -> YoAppUpdate.LaunchPrompt.Action.INSTALL_DIRECT;
            case SHOW_UPDATE_DIALOG -> YoAppUpdate.LaunchPrompt.Action.SHOW_UPDATE;
            case SELECT_LOCAL_APK -> YoAppUpdate.LaunchPrompt.Action.SELECT_LOCAL_APK;
            case NONE -> YoAppUpdate.LaunchPrompt.Action.NONE;
        };
        LocalApk local = decision.localApk();
        return new YoAppUpdate.LaunchPrompt(
                action,
                decision.update() != null ? toPublicInfo(decision.update()) : null,
                local != null ? local.file() : null);
    }

    @NonNull
    public static LaunchDecision toLaunchDecision(@NonNull YoAppUpdate.LaunchPrompt prompt) {
        return switch (prompt.action()) {
            case INSTALL_DIRECT -> toInstallDirect(prompt);
            case SHOW_UPDATE -> prompt.update() != null
                    ? LaunchDecision.showUpdate(toRemoteUpdate(prompt.update()))
                    : LaunchDecision.none();
            case SELECT_LOCAL_APK -> LaunchDecision.selectLocalApks(Collections.emptyList());
            case NONE -> LaunchDecision.none();
        };
    }

    @NonNull
    private static LaunchDecision toInstallDirect(@NonNull YoAppUpdate.LaunchPrompt prompt) {
        if (prompt.update() == null || prompt.apkFile() == null) {
            return LaunchDecision.none();
        }
        RemoteUpdate remote = toRemoteUpdate(prompt.update());
        return LaunchDecision.installDirect(
                remote,
                new LocalApk(prompt.apkFile(), prompt.apkFile().lastModified(), remote.version()));
    }
}
