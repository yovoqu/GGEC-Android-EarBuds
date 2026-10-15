package com.yovo.yotheme.dialog.api.style;

import androidx.annotation.NonNull;

import com.yovo.yotheme.constants.ListConstants;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 选项列表内容的行级样式
 */
public final class OptionRowStyle {

    private final boolean compactSpacing;
    private final float subtitleTextSizeSp;
    private final boolean rippleEnabled;

    private OptionRowStyle(boolean compactSpacing, float subtitleTextSizeSp, boolean rippleEnabled) {
        this.compactSpacing = compactSpacing;
        this.subtitleTextSizeSp = subtitleTextSizeSp;
        this.rippleEnabled = rippleEnabled;
    }

    public boolean isCompactSpacing() {
        return compactSpacing;
    }

    public float getSubtitleTextSizeSp() {
        return subtitleTextSizeSp;
    }

    public boolean isRippleEnabled() {
        return rippleEnabled;
    }

    @NonNull
    public static OptionRowStyle defaults() {
        return new OptionRowStyle(
                false,
                ListConstants.SUBTITLE_TEXT_SIZE_SP.getFloatValue(),
                false);
    }

    @NonNull
    public static OptionRowStyle compact(float subtitleTextSizeSp) {
        return new OptionRowStyle(true, subtitleTextSizeSp, false);
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean compactSpacing;
        private float subtitleTextSizeSp = ListConstants.SUBTITLE_TEXT_SIZE_SP.getFloatValue();
        private boolean rippleEnabled;

        @NonNull
        public Builder compactSpacing(boolean compactSpacing) {
            this.compactSpacing = compactSpacing;
            return this;
        }

        @NonNull
        public Builder subtitleTextSizeSp(float subtitleTextSizeSp) {
            this.subtitleTextSizeSp = subtitleTextSizeSp;
            return this;
        }

        @NonNull
        public Builder rippleEnabled(boolean rippleEnabled) {
            this.rippleEnabled = rippleEnabled;
            return this;
        }

        @NonNull
        public OptionRowStyle build() {
            return new OptionRowStyle(compactSpacing, subtitleTextSizeSp, rippleEnabled);
        }
    }
}

