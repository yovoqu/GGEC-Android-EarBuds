package com.yovo.lib_common.system.file.internal.saf.android;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.yovo.lib_common.internal.util.IoUtil;
import com.yovo.lib_common.system.file.model.SafFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * SAF 文件实现（DocumentFile 包装）。
 */
final class AndroidSafFile implements SafFile {

    private final Context appContext;
    private final DocumentFile document;

    AndroidSafFile(@NonNull Context appContext, @NonNull DocumentFile document) {
        this.appContext = appContext;
        this.document = document;
    }

    @NonNull
    @Override
    public String name() {
        String name = document.getName();
        return name != null ? name : "";
    }

    @Override
    public long lastModified() {
        return document.lastModified();
    }

    @Override
    public long length() {
        return document.length();
    }

    @Override
    public boolean isDirectory() {
        return document.isDirectory();
    }

    @Override
    @Nullable
    public File copyTo(@NonNull File target) {
        File parent = target.getParentFile();
        if (parent != null && !IoUtil.ensureDir(parent)) {
            return null;
        }
        File temp = new File(parent,
                target.getName() + ".tmp-" + System.currentTimeMillis());
        try (InputStream in = appContext.getContentResolver()
                .openInputStream(document.getUri());
             OutputStream out = new FileOutputStream(temp)) {
            if (in == null) {
                IoUtil.deleteQuietly(temp);
                return null;
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return IoUtil.moveReplace(temp, target) ? target : null;
        } catch (IOException e) {
            IoUtil.deleteQuietly(temp);
            return null;
        }
    }

    @Override
    public boolean delete() {
        return document.delete();
    }
}
