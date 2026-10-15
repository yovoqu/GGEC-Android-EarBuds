package com.yovo.yotheme.dialog.performance;

import androidx.annotation.NonNull;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 对话框实例的性能与渲染策略
 */
public final class DialogPerformancePolicy {

    @NonNull
    private final BlurMode blurMode;

    @NonNull
    private final ShadowMode shadowMode;

    private final boolean enableFrameMetrics;

    private DialogPerformancePolicy(@NonNull Builder builder) {
        this.blurMode = builder.blurMode;
        this.shadowMode = builder.shadowMode;
        this.enableFrameMetrics = builder.enableFrameMetrics;
    }

    @NonNull
    public BlurMode getBlurMode() {
        return blurMode;
    }

    @NonNull
    public ShadowMode getShadowMode() {
        return shadowMode;
    }

    public boolean isEnableFrameMetrics() {
        return enableFrameMetrics;
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
    public static DialogPerformancePolicy defaults() {
        return builder().build();
    }

    public static final class Builder {

        @NonNull
        private BlurMode blurMode = BlurMode.NONE;

        @NonNull
        private ShadowMode shadowMode = ShadowMode.ELEVATION;

        private boolean enableFrameMetrics = false;

        public Builder() {}

        private Builder(@NonNull DialogPerformancePolicy source) {
            this.blurMode = source.blurMode;
            this.shadowMode = source.shadowMode;
            this.enableFrameMetrics = source.enableFrameMetrics;
        }

        @NonNull
        public Builder blurMode(@NonNull BlurMode blurMode) {
            this.blurMode = blurMode;
            return this;
        }

        @NonNull
        public Builder shadowMode(@NonNull ShadowMode shadowMode) {
            this.shadowMode = shadowMode;
            return this;
        }

        @NonNull
        public Builder enableFrameMetrics(boolean enableFrameMetrics) {
            this.enableFrameMetrics = enableFrameMetrics;
            return this;
        }

        @NonNull
        public DialogPerformancePolicy build() {
            return new DialogPerformancePolicy(this);
        }
    }
}
