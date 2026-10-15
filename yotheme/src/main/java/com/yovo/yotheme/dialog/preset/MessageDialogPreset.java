package com.yovo.yotheme.dialog.preset;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.constants.DialogConstants;
import com.yovo.yotheme.dialog.api.action.DialogAction;
import com.yovo.yotheme.dialog.api.action.DialogActionListener;
import com.yovo.yotheme.dialog.api.action.DialogActionRole;
import com.yovo.yotheme.dialog.api.content.MessageContent;
import com.yovo.yotheme.dialog.api.YoDialogSpec;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 普通确认弹窗 Preset
 */
public final class MessageDialogPreset {

    @Nullable
    private String title;
    @Nullable
    private String message;
    @Nullable
    private String positiveText;
    @Nullable
    private DialogActionListener positiveListener;
    @Nullable
    private String negativeText;
    @Nullable
    private DialogActionListener negativeListener;

    @NonNull
    public static MessageDialogPreset create() {
        return new MessageDialogPreset();
    }

    @NonNull
    public MessageDialogPreset title(@Nullable String title) {
        this.title = title;
        return this;
    }

    @NonNull
    public MessageDialogPreset message(@Nullable String message) {
        this.message = message;
        return this;
    }

    @NonNull
    public MessageDialogPreset positive(@NonNull String text, @Nullable DialogActionListener listener) {
        this.positiveText = text;
        this.positiveListener = listener;
        return this;
    }

    @NonNull
    public MessageDialogPreset negative(@NonNull String text, @Nullable DialogActionListener listener) {
        this.negativeText = text;
        this.negativeListener = listener;
        return this;
    }

    @NonNull
    public YoDialogSpec build() {
        YoDialogSpec.Builder builder = YoDialogSpec.builder().title(title);
        if (message != null) {
            builder.contentSpec(new MessageContent(message));
        }
        String positive = positiveText != null
                ? positiveText
                : DialogConstants.DEFAULT_POSITIVE_BUTTON_TEXT.getString();
        builder.addAction(DialogAction.builder(DialogAction.ID_POSITIVE, positive)
                .role(DialogActionRole.PRIMARY)
                .listener(positiveListener)
                .build());
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
