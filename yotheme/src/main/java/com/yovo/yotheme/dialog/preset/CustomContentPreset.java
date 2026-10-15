package com.yovo.yotheme.dialog.preset;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.constants.DialogConstants;
import com.yovo.yotheme.dialog.api.content.CustomContentBinder;
import com.yovo.yotheme.dialog.api.content.CustomViewContent;
import com.yovo.yotheme.dialog.api.action.DialogAction;
import com.yovo.yotheme.dialog.api.action.DialogActionListener;
import com.yovo.yotheme.dialog.api.action.DialogActionRole;
import com.yovo.yotheme.dialog.api.behavior.DialogBehavior;
import com.yovo.yotheme.dialog.api.style.DialogStyle;
import com.yovo.yotheme.dialog.api.YoDialogSpec;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 自定义内容弹窗 Preset
 */
public final class CustomContentPreset {

    @Nullable
    private String title;
    @Nullable
    private CustomContentBinder contentBinder;
    @Nullable
    private String positiveText;
    @Nullable
    private DialogActionListener positiveListener;
    @Nullable
    private String negativeText;
    @Nullable
    private DialogActionListener negativeListener;
    @Nullable
    private Integer contentPaddingHorizontalDp;
    @Nullable
    private Integer windowHorizontalMarginDp;
    @Nullable
    private DialogBehavior behavior;
    private boolean positiveAutoDismiss = true;

    @NonNull
    public static CustomContentPreset create() {
        return new CustomContentPreset();
    }

    @NonNull
    public CustomContentPreset title(@Nullable String title) {
        this.title = title;
        return this;
    }

    @NonNull
    public CustomContentPreset content(@NonNull CustomContentBinder binder) {
        this.contentBinder = binder;
        return this;
    }

    @NonNull
    public CustomContentPreset positive(@NonNull String text, @Nullable DialogActionListener listener) {
        this.positiveText = text;
        this.positiveListener = listener;
        return this;
    }

    @NonNull
    public CustomContentPreset negative(@Nullable String text, @Nullable DialogActionListener listener) {
        this.negativeText = text;
        this.negativeListener = listener;
        return this;
    }

    @NonNull
    public CustomContentPreset contentPaddingHorizontalDp(@Nullable Integer dp) {
        this.contentPaddingHorizontalDp = dp;
        return this;
    }

    @NonNull
    public CustomContentPreset windowHorizontalMarginDp(@Nullable Integer dp) {
        this.windowHorizontalMarginDp = dp;
        return this;
    }

    @NonNull
    public CustomContentPreset behavior(@NonNull DialogBehavior behavior) {
        this.behavior = behavior;
        return this;
    }

    @NonNull
    public CustomContentPreset positiveAutoDismiss(boolean autoDismiss) {
        this.positiveAutoDismiss = autoDismiss;
        return this;
    }

    @NonNull
    public YoDialogSpec build() {
        if (contentBinder == null) {
            throw new IllegalStateException("CustomContentPreset requires content binder");
        }

        DialogStyle.Builder styleBuilder = DialogStyle.builder();
        if (contentPaddingHorizontalDp != null) {
            styleBuilder.contentPaddingHorizontalDp(contentPaddingHorizontalDp);
        }
        if (windowHorizontalMarginDp != null) {
            styleBuilder.horizontalScreenMarginDp(windowHorizontalMarginDp);
        }

        YoDialogSpec.Builder builder = YoDialogSpec.builder()
                .title(title)
                .contentSpec(new CustomViewContent(contentBinder))
                .style(styleBuilder.build());
        if (behavior != null) {
            builder.behavior(behavior);
        }

        if (positiveText != null) {
            builder.addAction(DialogAction.builder(DialogAction.ID_POSITIVE, positiveText)
                    .role(DialogActionRole.PRIMARY)
                    .autoDismiss(positiveAutoDismiss)
                    .listener(positiveListener)
                    .build());
        }
        if (negativeText != null) {
            builder.addAction(DialogAction.builder(DialogAction.ID_NEGATIVE, negativeText)
                    .role(DialogActionRole.SECONDARY)
                    .listener(negativeListener)
                    .build());
        } else if (negativeListener != null) {
            builder.addAction(DialogAction.builder(
                            DialogAction.ID_NEGATIVE,
                            DialogConstants.DEFAULT_NEGATIVE_BUTTON_TEXT.getString())
                    .role(DialogActionRole.SECONDARY)
                    .listener(negativeListener)
                    .build());
        }
        return builder.build();
    }
}
