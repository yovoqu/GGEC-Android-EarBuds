package com.yovo.lib_common.system.keepalive.model;

import android.app.PendingIntent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class KeepAliveActionSpec {

    @NonNull private final CharSequence label;
    @DrawableRes private final int iconResId;
    @NonNull private final PendingIntent pendingIntent;

    public KeepAliveActionSpec(@NonNull CharSequence label,
                               @DrawableRes int iconResId,
                               @NonNull PendingIntent pendingIntent) {
        this.label = label;
        this.iconResId = iconResId;
        this.pendingIntent = pendingIntent;
    }

    @NonNull
    public CharSequence label() {
        return label;
    }

    @DrawableRes
    public int iconResId() {
        return iconResId;
    }

    @NonNull
    public PendingIntent pendingIntent() {
        return pendingIntent;
    }
}
