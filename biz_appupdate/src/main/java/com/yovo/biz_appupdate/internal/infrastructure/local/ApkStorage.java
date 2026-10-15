package com.yovo.biz_appupdate.internal.infrastructure.local;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.contract.ApkMetaReader;
import com.yovo.biz_appupdate.internal.contract.ApkStoragePort;
import com.yovo.biz_appupdate.internal.contract.SafEntry;
import com.yovo.biz_appupdate.internal.contract.SafTreeStorePort;
import com.yovo.biz_appupdate.internal.model.ApkNamingSpec;
import com.yovo.biz_appupdate.internal.model.DownloadDirSpec;
import com.yovo.biz_appupdate.internal.model.LocalApk;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * APP 更新包存储实现：支持普通目录与 SAF 目录。
 */
public final class ApkStorage implements ApkStoragePort {

    private final Context appContext;
    private final DownloadDirSpec dirSpec;
    private final ApkNamingSpec namingSpec;
    private final ApkMetaReader metaReader;
    private final SafTreeStorePort safTreeStore;

    public ApkStorage(@NonNull Context context,
                      @NonNull DownloadDirSpec dirSpec,
                      @NonNull ApkNamingSpec namingSpec,
                      @NonNull ApkMetaReader metaReader,
                      @NonNull SafTreeStorePort safTreeStore) {
        this.appContext = context.getApplicationContext();
        this.dirSpec = dirSpec;
        this.namingSpec = namingSpec;
        this.metaReader = metaReader;
        this.safTreeStore = safTreeStore;
    }

    @NonNull
    public DownloadDirSpec dirSpec() {
        return dirSpec;
    }

    @NonNull
    public ApkNamingSpec namingSpec() {
        return namingSpec;
    }

    @Override
    @NonNull
    public File rootDir() {
        return DownloadDirResolver.resolve(
                dirSpec,
                appContext.getExternalFilesDir(null),
                appContext.getFilesDir());
    }

    public boolean isSaf() {
        return dirSpec.type() == DownloadDirSpec.Type.SAF;
    }

    public boolean isSafReady() {
        return !isSaf() || hasSafPermission();
    }

    @Nullable
    public Uri safTreeUri() {
        Uri explicit = dirSpec.safTreeUri();
        return explicit != null ? explicit : safTreeStore.loadTreeUri();
    }

    public boolean ensureReady() {
        if (isSaf() && !hasSafPermission()) {
            return false;
        }
        File dir = rootDir();
        if (!dir.exists() && !dir.mkdirs()) {
            return false;
        }
        return dir.isDirectory() && dir.canWrite();
    }

    @Override
    @NonNull
    public List<LocalApk> listApks() {
        if (isSaf()) {
            return listSafApks();
        }
        File dir = rootDir();
        List<LocalApk> result = new ArrayList<>();
        for (LocalApk base : LocalApkScanner.list(dir, namingSpec)) {
            result.add(metaReader.enrich(base));
        }
        return result;
    }

    @Override
    public boolean deleteApk(@NonNull LocalApk apk) {
        boolean deleted = true;
        if (isSaf()) {
            deleted = deleteSafDocument(apk.file().getName());
        }
        File file = apk.file();
        if (file.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        return deleted;
    }

    @Override
    public void deleteOwnedFilesExcept(@NonNull Collection<File> keepFiles) {
        if (isSaf()) {
            deleteOwnedSafExcept(keepFiles);
            return;
        }
        File dir = rootDir();
        if (!dir.isDirectory()) {
            return;
        }
        Set<String> keepPaths = new HashSet<>();
        for (File keep : keepFiles) {
            keepPaths.add(keep.getAbsolutePath());
        }
        File[] files = dir.listFiles((d, name) -> namingSpec.hasOwnedPrefix(name));
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (!keepPaths.contains(file.getAbsolutePath())) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    public boolean copyToSaf(@NonNull File source) {
        Uri treeUri = safTreeUri();
        return treeUri != null && safTreeStore.copyApkToTree(treeUri, source);
    }

    @Override
    @NonNull
    public String currentSignatureHash() {
        return metaReader.currentSignatureHash();
    }

    @Override
    @NonNull
    public String currentPackageName() {
        return metaReader.currentPackageName();
    }

    @Override
    @NonNull
    public String fileName(@NonNull String versionName, int versionCode) {
        return namingSpec.fileName(versionName, versionCode);
    }

    private boolean hasSafPermission() {
        return safTreeStore.isTreeAccessible(safTreeUri());
    }

    @NonNull
    private List<LocalApk> listSafApks() {
        List<LocalApk> result = new ArrayList<>();
        Uri treeUri = safTreeUri();
        if (treeUri == null) {
            return result;
        }
        for (SafEntry entry : safTreeStore.listEntries(
                treeUri, name -> namingSpec.matches(name))) {
            File cacheFile = copySafToCache(entry);
            if (cacheFile == null) {
                continue;
            }
            LocalApk base = new LocalApk(
                    cacheFile,
                    entry.lastModified(),
                    namingSpec.parseVersionName(entry.name()),
                    namingSpec.parseVersionCode(entry.name()),
                    null,
                    null);
            result.add(metaReader.enrich(base));
        }
        return result;
    }

    @Nullable
    private File copySafToCache(@NonNull SafEntry document) {
        File dir = rootDir();
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        File target = new File(dir, document.name());
        if (target.isFile()
                && target.lastModified() == document.lastModified()
                && target.length() == document.length()) {
            return target;
        }
        return document.copyTo(target);
    }

    private boolean deleteSafDocument(@NonNull String name) {
        Uri treeUri = safTreeUri();
        if (treeUri == null) {
            return false;
        }
        boolean deleted = safTreeStore.deleteEntry(treeUri, name);
        File cache = new File(rootDir(), name);
        if (cache.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            cache.delete();
        }
        return deleted;
    }

    private void deleteOwnedSafExcept(@NonNull Collection<File> keepFiles) {
        Set<String> keepNames = new HashSet<>();
        for (File keep : keepFiles) {
            keepNames.add(keep.getName());
        }
        Uri treeUri = safTreeUri();
        if (treeUri != null) {
            for (SafEntry entry : safTreeStore.listEntries(
                    treeUri, name -> namingSpec.hasOwnedPrefix(name))) {
                if (!keepNames.contains(entry.name())) {
                    entry.delete();
                }
            }
        }
        File dir = rootDir();
        if (dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> namingSpec.hasOwnedPrefix(name));
            if (files != null) {
                for (File file : files) {
                    if (!keepNames.contains(file.getName())) {
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }
                }
            }
        }
    }
}
