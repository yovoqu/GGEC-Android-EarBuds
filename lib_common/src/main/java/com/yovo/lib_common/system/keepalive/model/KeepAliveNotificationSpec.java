package com.yovo.lib_common.system.keepalive.model;

import android.app.PendingIntent;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class KeepAliveNotificationSpec {

    @NonNull private final CharSequence title;
    @NonNull private final CharSequence text;
    @Nullable private final PendingIntent contentIntent;
    @DrawableRes private final int smallIconResId;
    private final boolean showProgress;
    private final boolean indeterminate;
    private final int progressMax;
    private final int progressValue;
    @Nullable private final String throttleKey;
    @NonNull private final List<KeepAliveActionSpec> actions;

    private KeepAliveNotificationSpec(@NonNull Builder builder) {
        this.title = builder.title;
        this.text = builder.text;
        this.contentIntent = builder.contentIntent;
        this.smallIconResId = builder.smallIconResId;
        this.showProgress = builder.showProgress;
        this.indeterminate = builder.indeterminate;
        this.progressMax = builder.progressMax;
        this.progressValue = builder.progressValue;
        this.throttleKey = builder.throttleKey;
        this.actions = Collections.unmodifiableList(new ArrayList<>(builder.actions));
    }

    @NonNull
    public CharSequence title() {
        return title;
    }

    @NonNull
    public CharSequence text() {
        return text;
    }

    @Nullable
    public PendingIntent contentIntent() {
        return contentIntent;
    }

    @DrawableRes
    public int smallIconResId() {
        return smallIconResId;
    }

    public boolean showProgress() {
        return showProgress;
    }

    public boolean indeterminate() {
        return indeterminate;
    }

    public int progressMax() {
        return progressMax;
    }

    public int progressValue() {
        return progressValue;
    }

    @Nullable
    public String throttleKey() {
        return throttleKey;
    }

    @NonNull
    public List<KeepAliveActionSpec> actions() {
        return actions;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private CharSequence title = "";
        private CharSequence text = "";
        @Nullable private PendingIntent contentIntent;
        @DrawableRes private int smallIconResId;
        private boolean showProgress;
        private boolean indeterminate;
        private int progressMax = 100;
        private int progressValue;
        @Nullable private String throttleKey;
        private final List<KeepAliveActionSpec> actions = new ArrayList<>();

        @NonNull
        public Builder title(@NonNull CharSequence title) {
            this.title = title;
            return this;
        }

        @NonNull
        public Builder text(@NonNull CharSequence text) {
            this.text = text;
            return this;
        }

        @NonNull
        public Builder contentIntent(@Nullable PendingIntent contentIntent) {
            this.contentIntent = contentIntent;
            return this;
        }

        @NonNull
        public Builder smallIconResId(@DrawableRes int smallIconResId) {
            this.smallIconResId = smallIconResId;
            return this;
        }

        @NonNull
        public Builder progress(boolean showProgress,
                                boolean indeterminate,
                                int progressMax,
                                int progressValue) {
            this.showProgress = showProgress;
            this.indeterminate = indeterminate;
            this.progressMax = progressMax;
            this.progressValue = progressValue;
            return this;
        }

        @NonNull
        public Builder throttleKey(@Nullable String throttleKey) {
            this.throttleKey = throttleKey;
            return this;
        }

        @NonNull
        public Builder addAction(@NonNull KeepAliveActionSpec action) {
            this.actions.add(action);
            return this;
        }

        @NonNull
        public KeepAliveNotificationSpec build() {
            return new KeepAliveNotificationSpec(this);
        }
    }
}
