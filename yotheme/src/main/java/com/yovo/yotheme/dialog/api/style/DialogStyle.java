package com.yovo.yotheme.dialog.api.style;

import androidx.annotation.NonNull;

import com.yovo.yotheme.constants.DialogConstants;
import com.yovo.yotheme.constants.ModalPanelConstants;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 对话框框架、遮罩与内容内边距的视觉样式
 */
public final class DialogStyle {

    private final int horizontalScreenMarginDp;
    private final int contentPaddingHorizontalDp;
    private final float maskAlpha;
    private final int cornerRadiusDp;

    private DialogStyle(@NonNull Builder builder) {
        this.horizontalScreenMarginDp = builder.horizontalScreenMarginDp;
        this.contentPaddingHorizontalDp = builder.contentPaddingHorizontalDp;
        this.maskAlpha = builder.maskAlpha;
        this.cornerRadiusDp = builder.cornerRadiusDp;
    }

    public int getHorizontalScreenMarginDp() {
        return horizontalScreenMarginDp;
    }

    public int getContentPaddingHorizontalDp() {
        return contentPaddingHorizontalDp;
    }

    public float getMaskAlpha() {
        return maskAlpha;
    }

    public int getCornerRadiusDp() {
        return cornerRadiusDp;
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
    public static DialogStyle defaults() {
        return builder().build();
    }

    public static final class Builder {

        private int horizontalScreenMarginDp =
                DialogConstants.DIALOG_SCREEN_MARGIN.getValue();
        private int contentPaddingHorizontalDp =
                DialogConstants.PADDING_HORIZONTAL.getValue();
        private float maskAlpha = ModalPanelConstants.DEFAULT_MASK_ALPHA.getFloatValue();
        private int cornerRadiusDp = DialogConstants.CORNER_RADIUS.getValue();

        public Builder() {}

        private Builder(@NonNull DialogStyle source) {
            this.horizontalScreenMarginDp = source.horizontalScreenMarginDp;
            this.contentPaddingHorizontalDp = source.contentPaddingHorizontalDp;
            this.maskAlpha = source.maskAlpha;
            this.cornerRadiusDp = source.cornerRadiusDp;
        }

        @NonNull
        public Builder horizontalScreenMarginDp(int horizontalScreenMarginDp) {
            this.horizontalScreenMarginDp = horizontalScreenMarginDp;
            return this;
        }

        @NonNull
        public Builder contentPaddingHorizontalDp(int contentPaddingHorizontalDp) {
            this.contentPaddingHorizontalDp = contentPaddingHorizontalDp;
            return this;
        }

        @NonNull
        public Builder maskAlpha(float maskAlpha) {
            this.maskAlpha = maskAlpha;
            return this;
        }

        @NonNull
        public Builder cornerRadiusDp(int cornerRadiusDp) {
            this.cornerRadiusDp = cornerRadiusDp;
            return this;
        }

        @NonNull
        public DialogStyle build() {
            return new DialogStyle(this);
        }
    }
}

