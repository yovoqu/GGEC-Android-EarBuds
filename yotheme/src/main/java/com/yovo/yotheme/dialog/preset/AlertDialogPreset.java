package com.yovo.yotheme.dialog.preset;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.dialog.api.action.DialogAction;
import com.yovo.yotheme.dialog.api.action.DialogActionListener;
import com.yovo.yotheme.dialog.api.action.DialogActionRole;
import com.yovo.yotheme.dialog.api.YoDialogSpec;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 危险确认弹窗 Preset：正按钮使用 DANGER 角色
 */
public final class AlertDialogPreset {

    private final MessageDialogPreset delegate = MessageDialogPreset.create();

    @NonNull
    public static AlertDialogPreset danger() {
        return new AlertDialogPreset();
    }

    @NonNull
    public AlertDialogPreset title(@NonNull String title) {
        delegate.title(title);
        return this;
    }

    @NonNull
    public AlertDialogPreset message(@NonNull String message) {
        delegate.message(message);
        return this;
    }

    @NonNull
    public AlertDialogPreset positive(@NonNull String text, @Nullable DialogActionListener listener) {
        delegate.positive(text, listener);
        return this;
    }

    @NonNull
    public AlertDialogPreset negative(@NonNull String text, @Nullable DialogActionListener listener) {
        delegate.negative(text, listener);
        return this;
    }

    @NonNull
    public YoDialogSpec build() {
        YoDialogSpec spec = delegate.build();
        YoDialogSpec.Builder builder = spec.toBuilder();
        builder.actions(java.util.Collections.emptyList());
        for (DialogAction action : spec.getActions()) {
            if (DialogAction.ID_POSITIVE.equals(action.getId())) {
                builder.addAction(action.toBuilder().role(DialogActionRole.DANGER).build());
            } else {
                builder.addAction(action);
            }
        }
        return builder.build();
    }
}
