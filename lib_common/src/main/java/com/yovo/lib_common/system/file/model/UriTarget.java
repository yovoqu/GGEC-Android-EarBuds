package com.yovo.lib_common.system.file.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * URI / SAF 目标：不透明句柄，不解析为文件系统路径作为主路径。
 */
public final class UriTarget extends FileTarget {

    @NonNull
    private final Uri uri;

    @Nullable
    private final String relativePath;

    @NonNull
    private final UriKind uriKind;

    private UriTarget(@NonNull Uri uri,
                      @Nullable String relativePath,
                      @NonNull UriKind uriKind) {
        super();
        this.uri = uri;
        this.relativePath = relativePath;
        this.uriKind = uriKind;
    }

    @NonNull
    public static UriTarget document(@NonNull Uri uri) {
        return new UriTarget(uri, null, UriKind.DOCUMENT);
    }

    @NonNull
    public static UriTarget tree(@NonNull Uri treeUri) {
        return new UriTarget(treeUri, null, UriKind.TREE);
    }

    @NonNull
    public static UriTarget tree(@NonNull Uri treeUri, @Nullable String relativePath) {
        return new UriTarget(treeUri, relativePath, UriKind.TREE);
    }

    @NonNull
    public Uri uri() {
        return uri;
    }

    @Nullable
    public String relativePath() {
        return relativePath;
    }

    @NonNull
    public UriKind uriKind() {
        return uriKind;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UriTarget)) {
            return false;
        }
        UriTarget that = (UriTarget) o;
        return uri.equals(that.uri)
                && Objects.equals(relativePath, that.relativePath)
                && uriKind == that.uriKind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, relativePath, uriKind);
    }

    @NonNull
    @Override
    public String toString() {
        return "UriTarget{" + uriKind + ":" + uri
                + (relativePath != null ? "/" + relativePath : "") + "}";
    }
}
