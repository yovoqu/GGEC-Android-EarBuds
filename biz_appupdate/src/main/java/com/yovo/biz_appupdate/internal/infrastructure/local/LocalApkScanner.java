package com.yovo.biz_appupdate.internal.infrastructure.local;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.model.ApkNamingSpec;
import com.yovo.biz_appupdate.internal.model.LocalApk;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 本地 APK 扫描器：只识别当前 {@link ApkNamingSpec} 下的严格命名文件。
 */
public final class LocalApkScanner {

    private LocalApkScanner() {
    }

    @NonNull
    public static List<LocalApk> list(@NonNull File dir, @NonNull ApkNamingSpec spec) {
        List<LocalApk> result = new ArrayList<>();
        if (!dir.isDirectory()) {
            return result;
        }
        File[] files = dir.listFiles((d, name) -> spec.matches(name));
        if (files == null) {
            return result;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            String versionName = spec.parseVersionName(file.getName());
            int versionCode = spec.parseVersionCode(file.getName());
            if (versionName == null) {
                continue;
            }
            result.add(new LocalApk(file, file.lastModified(), versionName, versionCode, null, null));
        }
        return result;
    }
}
