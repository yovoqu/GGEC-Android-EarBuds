package com.yovo.lib_common.system.receive.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description ACTION_SEND / ACTION_SEND_MULTIPLE 的不可变解析结果。
 *
 * <p>MIME、文件名和内容均来自外部应用，只能视为提示，不能作为安全校验依据。</p>
 */
public final class IncomingShare {

    private final String action;
    @Nullable
    private final String declaredMimeType;
    @Nullable
    private final CharSequence text;
    private final List<Uri> streamUris;

    public IncomingShare(@NonNull String action,
                         @Nullable String declaredMimeType,
                         @Nullable CharSequence text,
                         @NonNull List<Uri> streamUris) {
        this.action = action;
        this.declaredMimeType = declaredMimeType;
        this.text = text;
        this.streamUris = Collections.unmodifiableList(new ArrayList<>(streamUris));
    }

    @NonNull
    public String action() {
        return action;
    }

    @Nullable
    public String declaredMimeType() {
        return declaredMimeType;
    }

    @Nullable
    public CharSequence text() {
        return text;
    }

    @NonNull
    public List<Uri> streamUris() {
        return streamUris;
    }

    public boolean isMultiple() {
        return streamUris.size() > 1;
    }

    public boolean hasContent() {
        return text != null || !streamUris.isEmpty();
    }
}
