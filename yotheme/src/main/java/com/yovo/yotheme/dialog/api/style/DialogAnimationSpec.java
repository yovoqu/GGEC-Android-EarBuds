package com.yovo.yotheme.dialog.api.style;

import androidx.annotation.NonNull;

import com.yovo.yotheme.constants.DialogConstants;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 显示与关闭动画时序配置
 */
public final class DialogAnimationSpec {

    private final long showDurationMs;
    private final long dismissDurationMs;

    private DialogAnimationSpec(long showDurationMs, long dismissDurationMs) {
        this.showDurationMs = showDurationMs;
        this.dismissDurationMs = dismissDurationMs;
    }

    public long getShowDurationMs() {
        return showDurationMs;
    }

    public long getDismissDurationMs() {
        return dismissDurationMs;
    }

    @NonNull
    public static DialogAnimationSpec defaults() {
        return new DialogAnimationSpec(
                DialogConstants.SHOW_ANIMATION_DURATION_MS.getValue(),
                DialogConstants.DISMISS_ANIMATION_DURATION_MS.getValue());
    }
}

