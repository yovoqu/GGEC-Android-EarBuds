package com.yovo.lib_common.system.file.internal.resolve;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.system.file.model.PathTarget;
import com.yovo.lib_common.system.file.model.RootKind;

import java.io.File;

/**
 * 基于 Android Context 的根目录解析。
 */
public final class AndroidPathRootProvider implements PathRootProvider {

    private final Context appContext;

    public AndroidPathRootProvider(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @Override
    @Nullable
    public File root(@NonNull PathTarget target) {
        RootKind kind = target.rootKind();
        if (kind == null) {
            return null;
        }
        switch (kind) {
            case FILES:
                return appContext.getFilesDir();
            case CACHE:
                return appContext.getCacheDir();
            case EXT_FILES:
                return appContext.getExternalFilesDir(null);
            case EXT_CACHE:
                return appContext.getExternalCacheDir();
            case CUSTOM_SUBDIR: {
                String path = target.customRootPath();
                return path == null ? null : new File(path);
            }
            default:
                return null;
        }
    }
}
