package com.yovo.yotheme.dialog.api.option;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 单个选项行的公共数据模型
 */
public final class OptionItemSpec {

    @NonNull
    private final String id;
    @NonNull
    private final String title;
    @Nullable
    private final String subtitle;
    @Nullable
    private final String leftSubtitle;
    @Nullable
    private final String rightSubtitle;
    private final int leadingIconRes;
    @Nullable
    private final TrailingActionSpec trailingAction;
    private final boolean enabled;
    @Nullable
    private final Object payload;

    private OptionItemSpec(@NonNull Builder builder) {
        this.id = builder.id;
        this.title = builder.title;
        this.subtitle = builder.subtitle;
        this.leftSubtitle = builder.leftSubtitle;
        this.rightSubtitle = builder.rightSubtitle;
        this.leadingIconRes = builder.leadingIconRes;
        this.trailingAction = builder.trailingAction;
        this.enabled = builder.enabled;
        this.payload = builder.payload;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getSubtitle() {
        return subtitle;
    }

    @Nullable
    public String getLeftSubtitle() {
        return leftSubtitle;
    }

    @Nullable
    public String getRightSubtitle() {
        return rightSubtitle;
    }

    public int getLeadingIconRes() {
        return leadingIconRes;
    }

    @Nullable
    public TrailingActionSpec getTrailingAction() {
        return trailingAction;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Nullable
    public Object getPayload() {
        return payload;
    }

    public boolean hasSplitSubtitle() {
        return rightSubtitle != null && !rightSubtitle.isEmpty();
    }

    @NonNull
    public static Builder builder(@NonNull String id, @NonNull String title) {
        return new Builder(id, title);
    }

    public static final class Builder {
        @NonNull
        private final String id;
        @NonNull
        private final String title;
        @Nullable
        private String subtitle;
        @Nullable
        private String leftSubtitle;
        @Nullable
        private String rightSubtitle;
        private int leadingIconRes;
        @Nullable
        private TrailingActionSpec trailingAction;
        private boolean enabled = true;
        @Nullable
        private Object payload;

        public Builder(@NonNull String id, @NonNull String title) {
            this.id = id;
            this.title = title;
        }

        @NonNull
        public Builder subtitle(@Nullable String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        @NonNull
        public Builder leftSubtitle(@Nullable String leftSubtitle) {
            this.leftSubtitle = leftSubtitle;
            return this;
        }

        @NonNull
        public Builder rightSubtitle(@Nullable String rightSubtitle) {
            this.rightSubtitle = rightSubtitle;
            return this;
        }

        @NonNull
        public Builder leadingIconRes(int leadingIconRes) {
            this.leadingIconRes = leadingIconRes;
            return this;
        }

        @NonNull
        public Builder trailingAction(@Nullable TrailingActionSpec trailingAction) {
            this.trailingAction = trailingAction;
            return this;
        }

        @NonNull
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        @NonNull
        public Builder payload(@Nullable Object payload) {
            this.payload = payload;
            return this;
        }

        @NonNull
        public OptionItemSpec build() {
            return new OptionItemSpec(this);
        }
    }
}


