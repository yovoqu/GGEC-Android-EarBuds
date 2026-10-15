package com.yovo.yotheme.dialog.api.behavior;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 对话框实例的交互与关闭行为
 */
public final class DialogBehavior {

    private final boolean cancelOnTouchOutside;
    private final boolean cancelableByBack;
    private final boolean modal;
    private final boolean dismissOnAction;
    private final int backPriority;

    private DialogBehavior(@NonNull Builder builder) {
        this.cancelOnTouchOutside = builder.cancelOnTouchOutside;
        this.cancelableByBack = builder.cancelableByBack;
        this.modal = builder.modal;
        this.dismissOnAction = builder.dismissOnAction;
        this.backPriority = builder.backPriority;
    }

    public boolean isCancelOnTouchOutside() {
        return cancelOnTouchOutside;
    }

    public boolean isCancelableByBack() {
        return cancelableByBack;
    }

    public boolean isModal() {
        return modal;
    }

    public boolean isDismissOnAction() {
        return dismissOnAction;
    }

    public int getBackPriority() {
        return backPriority;
    }

    @NonNull
    public Builder toBuilder() {
        return new Builder(this);
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public static DialogBehavior defaults() {
        return builder().build();
    }

    public static final class Builder {

        private boolean cancelOnTouchOutside = true;
        private boolean cancelableByBack = true;
        private boolean modal = true;
        private boolean dismissOnAction = true;
        private int backPriority = 0;

        public Builder() {}

        private Builder(@NonNull DialogBehavior source) {
            this.cancelOnTouchOutside = source.cancelOnTouchOutside;
            this.cancelableByBack = source.cancelableByBack;
            this.modal = source.modal;
            this.dismissOnAction = source.dismissOnAction;
            this.backPriority = source.backPriority;
        }

        @NonNull
        public Builder cancelOnTouchOutside(boolean cancelOnTouchOutside) {
            this.cancelOnTouchOutside = cancelOnTouchOutside;
            return this;
        }

        @NonNull
        public Builder cancelableByBack(boolean cancelableByBack) {
            this.cancelableByBack = cancelableByBack;
            return this;
        }

        @NonNull
        public Builder modal(boolean modal) {
            this.modal = modal;
            return this;
        }

        @NonNull
        public Builder dismissOnAction(boolean dismissOnAction) {
            this.dismissOnAction = dismissOnAction;
            return this;
        }

        @NonNull
        public Builder backPriority(int backPriority) {
            this.backPriority = backPriority;
            return this;
        }

        @NonNull
        public DialogBehavior build() {
            return new DialogBehavior(this);
        }
    }
}

