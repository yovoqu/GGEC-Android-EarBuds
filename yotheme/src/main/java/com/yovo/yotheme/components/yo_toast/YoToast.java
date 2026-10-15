package com.yovo.yotheme.components.yo_toast;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.yovo.yotheme.components.yo_toast.toast_structure.ToastWindowController;
import com.yovo.yotheme.constants.ToastConstants;

/**
 * @author tingfeng.yang
 * @date 2026-06-24
 * @description YoTheme Toast 组件入口
 * @note 默认居中、黑色半透明、圆角 16dp；无消息队列，每次调用立即取消上一条并显示最新内容。
 * 触摸事件穿透，不阻挡底层界面。请传入 Activity 级别的 Context。
 */
public final class YoToast extends Dialog {

    public static final int LENGTH_SHORT = ToastConstants.DURATION_SHORT_MS.getIntValue();
    public static final int LENGTH_LONG = ToastConstants.DURATION_LONG_MS.getIntValue();

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    @Nullable
    private static YoToast current;

    private final ToastWindowController windowController = new ToastWindowController();
    private final CharSequence message;
    private final Runnable autoDismiss = this::dismiss;

    private YoToast(@NonNull Context context, @NonNull CharSequence message) {
        super(context, com.yovo.yotheme.R.style.YoTheme_FloatingWindow);
        this.message = message;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setCancelable(false);
        setCanceledOnTouchOutside(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(windowController.createContent(getContext(), message));
        windowController.configure(this);
    }

    // ======================== 静态 API ========================

    public static void show(@NonNull Context context, @StringRes int resId) {
        show(context, context.getString(resId), LENGTH_SHORT);
    }

    public static void show(@NonNull Context context, @Nullable CharSequence message) {
        show(context, message, LENGTH_SHORT);
    }

    public static void show(@NonNull Context context, @StringRes int resId, int durationMs) {
        show(context, context.getString(resId), durationMs);
    }

    /**
     * 显示一条 Toast。无队列：若已有显示中的 Toast，会先取消再显示最新内容。
     *
     * @param context    Activity 级别 Context
     * @param message    文案，为空时忽略
     * @param durationMs 显示时长（毫秒），可用 {@link #LENGTH_SHORT} / {@link #LENGTH_LONG}
     */
    public static void show(@NonNull Context context,
                            @Nullable CharSequence message,
                            int durationMs) {
        if (message == null || message.length() == 0) {
            return;
        }
        runOnMain(() -> display(context, message, durationMs));
    }

    /** 立即取消当前显示中的 Toast（若有）。 */
    public static void cancelCurrent() {
        runOnMain(YoToast::cancelInternal);
    }

    // ======================== 内部实现 ========================

    private static void display(@NonNull Context context,
                                @NonNull CharSequence message,
                                int durationMs) {
        cancelInternal();
        try {
            YoToast toast = new YoToast(context, message);
            toast.show();
            current = toast;
            MAIN_HANDLER.postDelayed(toast.autoDismiss, Math.max(0, durationMs));
        } catch (Exception ignored) {
            // Activity 已销毁等异常场景，静默忽略，避免崩溃
        }
    }

    private static void cancelInternal() {
        if (current != null) {
            current.dismiss();
        }
    }

    @Override
    public void dismiss() {
        MAIN_HANDLER.removeCallbacks(autoDismiss);
        try {
            super.dismiss();
        } catch (Exception ignored) {
            // 窗口已分离等场景，忽略
        }
        if (current == this) {
            current = null;
        }
    }

    private static void runOnMain(@NonNull Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            MAIN_HANDLER.post(action);
        }
    }
}
