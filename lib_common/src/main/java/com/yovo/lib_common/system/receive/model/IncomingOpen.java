package com.yovo.lib_common.system.receive.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description ACTION_VIEW / ACTION_EDIT 的不可变解析结果。
 */
public final class IncomingOpen {

    public enum Mode {
        VIEW,
        EDIT
    }

    private final Mode mode;
    private final Uri uri;
    @Nullable
    private final String declaredMimeType;
    private final int grantFlags;

    public IncomingOpen(@NonNull Mode mode,
                        @NonNull Uri uri,
                        @Nullable String declaredMimeType,
                        int grantFlags) {
        this.mode = mode;
        this.uri = uri;
        this.declaredMimeType = declaredMimeType;
        this.grantFlags = grantFlags;
    }

    @NonNull
    public Mode mode() {
        return mode;
    }

    @NonNull
    public Uri uri() {
        return uri;
    }

    @Nullable
    public String declaredMimeType() {
        return declaredMimeType;
    }

    public int grantFlags() {
        return grantFlags;
    }
}
