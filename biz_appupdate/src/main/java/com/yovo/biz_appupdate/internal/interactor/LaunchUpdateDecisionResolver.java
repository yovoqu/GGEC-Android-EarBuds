package com.yovo.biz_appupdate.internal.interactor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.model.DownloadedApk;
import com.yovo.biz_appupdate.internal.model.LaunchDecision;
import com.yovo.biz_appupdate.internal.model.LocalApk;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;
import com.yovo.biz_appupdate.internal.model.StoragePolicy;
import com.yovo.biz_appupdate.internal.model.UpdateMeta;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * 启动决策判定：结合远程更新、历史元信息与本地 APK 列表。
 */
public final class LaunchUpdateDecisionResolver {

    private static final long TIME_TOLERANCE_MS = 5_000L;

    private LaunchUpdateDecisionResolver() {
    }

    @NonNull
    public static LaunchDecision resolve(@NonNull RemoteUpdate remote,
                                         @Nullable UpdateMeta meta,
                                         @Nullable LocalApk local,
                                         boolean autoInstallOnLaunch) {
        return resolve(remote, meta, local, autoInstallOnLaunch, "");
    }

    @NonNull
    public static LaunchDecision resolve(@NonNull RemoteUpdate remote,
                                         @Nullable UpdateMeta meta,
                                         @Nullable LocalApk local,
                                         boolean autoInstallOnLaunch,
                                         @NonNull String expectedSignatureHash) {
        List<LocalApk> locals = local == null
                ? Collections.emptyList()
                : Collections.singletonList(local);
        return resolve(remote, meta, locals, autoInstallOnLaunch,
                expectedSignatureHash, StoragePolicy.SINGLE_LATEST);
    }

    @NonNull
    public static LaunchDecision resolve(@NonNull RemoteUpdate remote,
                                         @Nullable UpdateMeta meta,
                                         @NonNull List<LocalApk> locals,
                                         boolean autoInstallOnLaunch,
                                         @NonNull String expectedSignatureHash,
                                         @NonNull StoragePolicy policy) {
        if (remote.hasNewVersion()) {
            LocalApk matched = findMatch(remote, meta, locals, expectedSignatureHash);
            if (autoInstallOnLaunch && matched != null) {
                return LaunchDecision.installDirect(remote, matched);
            }
            return LaunchDecision.showUpdate(remote);
        }
        if (policy == StoragePolicy.MULTI_VERSION && !locals.isEmpty()) {
            return LaunchDecision.selectLocalApks(locals);
        }
        return LaunchDecision.none();
    }

    @Nullable
    private static LocalApk findMatch(@NonNull RemoteUpdate remote,
                                      @Nullable UpdateMeta meta,
                                      @NonNull List<LocalApk> locals,
                                      @NonNull String expectedSignatureHash) {
        for (LocalApk local : locals) {
            if (matches(remote, meta, local, expectedSignatureHash)) {
                return local;
            }
        }
        return null;
    }

    private static boolean matches(@NonNull RemoteUpdate remote,
                                   @Nullable UpdateMeta meta,
                                   @NonNull LocalApk local,
                                   @NonNull String expectedSignatureHash) {
        if (!signatureMatches(expectedSignatureHash, local)) {
            return false;
        }
        if (matchesVersionName(remote, local)) {
            return true;
        }
        if (matchesRealVersionCode(remote, local)) {
            return true;
        }
        if (meta != null && matchesDownloadRecord(remote, meta, local)) {
            return true;
        }
        return matchesModificationTime(meta, local);
    }

    private static boolean signatureMatches(@NonNull String expectedSignatureHash,
                                            @NonNull LocalApk local) {
        if (expectedSignatureHash.isEmpty()) {
            return true;
        }
        String localHash = local.signatureHash();
        return localHash != null && localHash.equalsIgnoreCase(expectedSignatureHash);
    }

    private static boolean matchesRealVersionCode(@NonNull RemoteUpdate remote,
                                                  @NonNull LocalApk local) {
        return remote.versionCode() > 0
                && local.versionCode() > 0
                && remote.versionCode() == local.versionCode();
    }

    private static boolean matchesVersionName(@NonNull RemoteUpdate remote,
                                              @NonNull LocalApk local) {
        String localVersion = local.versionName();
        return localVersion != null && localVersion.equals(remote.version());
    }

    private static boolean matchesDownloadRecord(@NonNull RemoteUpdate remote,
                                                 @NonNull UpdateMeta meta,
                                                 @NonNull LocalApk local) {
        DownloadedApk downloaded = meta.downloadedApk();
        if (downloaded == null) {
            return false;
        }
        boolean samePath = samePath(downloaded.path(), local.file());
        if (!samePath) {
            return false;
        }
        boolean sameVersion = downloaded.version() != null
                && downloaded.version().equals(remote.version());
        boolean sameVersionCode = downloaded.versionCode() > 0
                && remote.versionCode() > 0
                && downloaded.versionCode() == remote.versionCode();
        return sameVersion || sameVersionCode;
    }

    private static boolean matchesModificationTime(@Nullable UpdateMeta meta,
                                                   @NonNull LocalApk local) {
        return meta != null
                && meta.discoveredAtMillis() > 0L
                && local.lastModified() >= meta.discoveredAtMillis() - TIME_TOLERANCE_MS;
    }

    private static boolean samePath(@Nullable String recordedPath, @NonNull File file) {
        return recordedPath != null && recordedPath.equals(file.getAbsolutePath());
    }
}
