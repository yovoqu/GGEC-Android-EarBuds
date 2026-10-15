package com.yovo.biz_appupdate.internal.infrastructure.local;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.contract.UpdateMetaStore;
import com.yovo.biz_appupdate.internal.model.DownloadedApk;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;
import com.yovo.biz_appupdate.internal.model.UpdateMeta;

import java.io.File;

/**
 * SharedPreferences 实现的最新更新元信息存储。
 */
public final class PrefsUpdateMetaStore implements UpdateMetaStore {

    private static final String PREF_NAME = "biz_appupdate_meta";

    private static final String KEY_VERSION = "meta_version";
    private static final String KEY_VERSION_CODE = "meta_version_code";
    private static final String KEY_DESCRIPTION = "meta_description";
    private static final String KEY_URL = "meta_url";
    private static final String KEY_FORCE = "meta_force_update";
    private static final String KEY_MD5 = "meta_md5";
    private static final String KEY_SHA256 = "meta_sha256";
    private static final String KEY_SIZE = "meta_size_bytes";
    private static final String KEY_DISCOVERED_AT = "meta_discovered_at";

    private static final String KEY_APK_PATH = "apk_path";
    private static final String KEY_APK_VERSION = "apk_version";
    private static final String KEY_APK_VERSION_CODE = "apk_version_code";
    private static final String KEY_APK_DOWNLOADED_AT = "apk_downloaded_at";

    private final SharedPreferences prefs;

    public PrefsUpdateMetaStore(@NonNull Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public void record(@NonNull RemoteUpdate update) {
        prefs.edit()
                .putString(KEY_VERSION, update.version())
                .putInt(KEY_VERSION_CODE, update.versionCode())
                .putString(KEY_DESCRIPTION, update.description())
                .putString(KEY_URL, update.downloadUrl())
                .putBoolean(KEY_FORCE, update.forceUpdate())
                .putString(KEY_MD5, update.md5())
                .putString(KEY_SHA256, update.sha256())
                .putLong(KEY_SIZE, update.sizeBytes())
                .putLong(KEY_DISCOVERED_AT, System.currentTimeMillis())
                .apply();
    }

    @Override
    public void recordDownloadedApk(@NonNull RemoteUpdate update, @NonNull File apkFile) {
        prefs.edit()
                .putString(KEY_APK_PATH, apkFile.getAbsolutePath())
                .putString(KEY_APK_VERSION, update.version())
                .putInt(KEY_APK_VERSION_CODE, update.versionCode())
                .putLong(KEY_APK_DOWNLOADED_AT, System.currentTimeMillis())
                .apply();
    }

    @Nullable
    @Override
    public UpdateMeta snapshot() {
        String version = prefs.getString(KEY_VERSION, "");
        if (TextUtils.isEmpty(version)) {
            return null;
        }
        DownloadedApk downloadedApk = null;
        String apkPath = prefs.getString(KEY_APK_PATH, "");
        if (!TextUtils.isEmpty(apkPath)) {
            downloadedApk = new DownloadedApk(
                    apkPath,
                    prefs.getString(KEY_APK_VERSION, null),
                    prefs.getInt(KEY_APK_VERSION_CODE, 0),
                    prefs.getLong(KEY_APK_DOWNLOADED_AT, 0L));
        }
        return new UpdateMeta(
                version,
                prefs.getInt(KEY_VERSION_CODE, 0),
                prefs.getString(KEY_DESCRIPTION, ""),
                prefs.getString(KEY_URL, ""),
                prefs.getBoolean(KEY_FORCE, false),
                prefs.getString(KEY_MD5, ""),
                prefs.getString(KEY_SHA256, ""),
                prefs.getLong(KEY_SIZE, 0L),
                prefs.getLong(KEY_DISCOVERED_AT, 0L),
                downloadedApk);
    }

    @Override
    public void clear() {
        prefs.edit().clear().apply();
    }
}
