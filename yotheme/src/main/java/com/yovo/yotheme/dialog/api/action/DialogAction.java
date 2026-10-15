package com.yovo.yotheme.dialog.api.action;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.dialog.api.lifecycle.DialogResult;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 单个对话框操作按钮的不可变配置
 */
public final class DialogAction {

    public static final String ID_POSITIVE = "positive";
    public static final String ID_NEGATIVE = "negative";

    public interface OnClickListener {
        void onClick(@NonNull DialogResult result);
    }

    @NonNull
    private final String id;

    @NonNull
    private final String text;

    @NonNull
    private final DialogActionRole role;

    private final boolean autoDismiss;

    private final boolean enabled;

    @Nullable
    private final OnClickListener onClick;

    private DialogAction(@NonNull Builder builder) {
        this.id = builder.id;
        this.text = builder.text;
        this.role = builder.role;
        this.autoDismiss = builder.autoDismiss;
        this.enabled = builder.enabled;
        this.onClick = builder.onClick;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getText() {
        return text;
    }

    @NonNull
    public DialogActionRole getRole() {
        return role;
    }

    public boolean isAutoDismiss() {
        return autoDismiss;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Nullable
    public OnClickListener getOnClick() {
        return onClick;
    }

    @NonNull
    public Builder toBuilder() {
        return new Builder(this);
    }

    @NonNull
    public static Builder builder(@NonNull String id, @NonNull String text) {
        return new Builder(id, text);
    }

    public static final class Builder {

        @NonNull
        private final String id;

        @NonNull
        private final String text;

        @NonNull
        private DialogActionRole role = DialogActionRole.PRIMARY;

        private boolean autoDismiss = true;

        private boolean enabled = true;

        @Nullable
        private OnClickListener onClick;

        public Builder(@NonNull String id, @NonNull String text) {
            this.id = id;
            this.text = text;
        }

        private Builder(@NonNull DialogAction source) {
            this.id = source.id;
            this.text = source.text;
            this.role = source.role;
            this.autoDismiss = source.autoDismiss;
            this.enabled = source.enabled;
            this.onClick = source.onClick;
        }

        @NonNull
        public Builder role(@NonNull DialogActionRole role) {
            this.role = role;
            return this;
        }

        @NonNull
        public Builder autoDismiss(boolean autoDismiss) {
            this.autoDismiss = autoDismiss;
            return this;
        }

        @NonNull
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        @NonNull
        public Builder onClick(@Nullable OnClickListener onClick) {
            this.onClick = onClick;
            return this;
        }

        @NonNull
        public Builder listener(@Nullable DialogActionListener listener) {
            this.onClick = listener;
            return this;
        }

        @NonNull
        public DialogAction build() {
            return new DialogAction(this);
        }
    }
}

