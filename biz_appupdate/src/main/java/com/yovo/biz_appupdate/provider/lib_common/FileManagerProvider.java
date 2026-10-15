package com.yovo.biz_appupdate.provider.lib_common;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.contract.SafEntry;
import com.yovo.biz_appupdate.internal.contract.SafTreeStorePort;
import com.yovo.lib_common.system.file.YoFileManager;
import com.yovo.lib_common.system.file.model.ConflictPolicy;
import com.yovo.lib_common.system.file.model.FileResult;
import com.yovo.lib_common.system.file.model.SafDirectory;
import com.yovo.lib_common.system.file.model.SafFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * lib_common 文件/SAF 能力 Provider（YoFileManager 唯一入口）。
 *
 * <p>只暴露 biz_appupdate 需要的能力，作为 API 过滤层与隔离层。</p>
 */
public final class FileManagerProvider implements SafTreeStorePort {

    private static final String APK_MIME = "application/vnd.android.package-archive";

    private final Context appContext;

    public FileManagerProvider(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Nullable
    @Override
    public Uri loadTreeUri() {
        return YoFileManager.treeStore(appContext).load();
    }

    @Override
    public boolean isDismissed() {
        return YoFileManager.treeStore(appContext).isDismissed();
    }

    @Override
    public boolean isTreeAccessible(@Nullable Uri treeUri) {
        return YoFileManager.isTreeAccessible(appContext, treeUri);
    }

    @Override
    public boolean copyApkToTree(@NonNull Uri treeUri, @NonNull File source) {
        FileResult<SafFile> copied = YoFileManager.copyFileToTree(
                appContext,
                treeUri,
                source,
                source.getName(),
                APK_MIME,
                ConflictPolicy.OVERWRITE);
        return copied.success();
    }

    @NonNull
    @Override
    public List<SafEntry> listEntries(@NonNull Uri treeUri,
                                      @NonNull Predicate<String> nameFilter) {
        SafDirectory directory = YoFileManager.openTree(appContext, treeUri);
        List<SafEntry> result = new ArrayList<>();
        if (directory == null) {
            return result;
        }
        for (SafFile file : directory.listFiles(nameFilter)) {
            SafFile captured = file;
            result.add(new SafEntry(
                    file.name(),
                    file.lastModified(),
                    file.length(),
                    new SafEntry.FileOps() {
                        @Nullable
                        @Override
                        public File copyTo(@NonNull File target) {
                            return captured.copyTo(target);
                        }

                        @Override
                        public boolean delete() {
                            return captured.delete();
                        }
                    }));
        }
        return result;
    }

    @Override
    public boolean deleteEntry(@NonNull Uri treeUri, @NonNull String name) {
        SafDirectory directory = YoFileManager.openTree(appContext, treeUri);
        return directory != null && directory.deleteFile(name);
    }
}
