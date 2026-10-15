package com.yovo.biz_appupdate.internal.interactor;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.model.CurrentAppVersion;
import com.yovo.biz_appupdate.internal.model.LocalApk;
import com.yovo.biz_appupdate.internal.model.StoragePolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 安装包保留策略纯用例：决定哪些 APK 保留、哪些删除。
 */
public final class ApkRetentionDecider {

    private ApkRetentionDecider() {
    }

    @NonNull
    public static RetentionPlan decide(@NonNull List<LocalApk> candidates,
                                       @NonNull CurrentAppVersion current,
                                       @NonNull String expectedPackageName,
                                       @NonNull String expectedSignatureHash,
                                       @NonNull StoragePolicy policy) {
        List<LocalApk> valid = new ArrayList<>();
        List<LocalApk> remove = new ArrayList<>();
        for (LocalApk apk : candidates) {
            if (!isValid(apk, expectedPackageName, expectedSignatureHash)
                    || isCurrentVersion(apk, current)) {
                remove.add(apk);
            } else {
                valid.add(apk);
            }
        }
        valid.sort(Comparator
                .comparingInt(LocalApk::versionCode).reversed()
                .thenComparing(Comparator.comparingLong(LocalApk::lastModified).reversed()));

        List<LocalApk> keep;
        if (policy == StoragePolicy.MULTI_VERSION) {
            keep = new ArrayList<>(valid);
        } else {
            keep = valid.isEmpty()
                    ? Collections.emptyList()
                    : Collections.singletonList(valid.get(0));
        }

        for (LocalApk apk : candidates) {
            if (!containsFile(keep, apk) && !containsFile(remove, apk)) {
                remove.add(apk);
            }
        }
        return new RetentionPlan(keep, remove);
    }

    private static boolean isValid(@NonNull LocalApk apk,
                                   @NonNull String expectedPackageName,
                                   @NonNull String expectedSignatureHash) {
        if (!expectedPackageName.isEmpty()
                && !expectedPackageName.equals(apk.packageName())) {
            return false;
        }
        if (!expectedSignatureHash.isEmpty()) {
            return apk.signatureHash() != null
                    && apk.signatureHash().equalsIgnoreCase(expectedSignatureHash);
        }
        return true;
    }

    private static boolean isCurrentVersion(@NonNull LocalApk apk,
                                            @NonNull CurrentAppVersion current) {
        if (apk.versionCode() > 0 && current.versionCode() > 0) {
            return apk.versionCode() == current.versionCode();
        }
        return apk.versionName() != null && apk.versionName().equals(current.versionName());
    }

    private static boolean containsFile(@NonNull List<LocalApk> list, @NonNull LocalApk target) {
        for (LocalApk apk : list) {
            if (apk.file().equals(target.file())) {
                return true;
            }
        }
        return false;
    }

    public static final class RetentionPlan {

        @NonNull
        private final List<LocalApk> keep;
        @NonNull
        private final List<LocalApk> remove;

        RetentionPlan(@NonNull List<LocalApk> keep, @NonNull List<LocalApk> remove) {
            this.keep = Collections.unmodifiableList(new ArrayList<>(keep));
            this.remove = Collections.unmodifiableList(new ArrayList<>(remove));
        }

        @NonNull
        public List<LocalApk> keep() {
            return keep;
        }

        @NonNull
        public List<LocalApk> remove() {
            return remove;
        }
    }
}
