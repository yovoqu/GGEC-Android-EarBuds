package com.yovo.yotheme.components.yo_input.input_structure;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.yovo.yotheme.constants.InputConstants;

/**
 * @author tingfeng.yang
 * @date 2026-06-13
 * @description 输入框状态管理：焦点与自定义光标闪烁
 */
public class InputStateManager {

    public interface Callback {
        void onFocusChanged(boolean focused);

        void onCursorBlinkTick(boolean visible);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable blinkRunnable = this::toggleCursorBlink;

    private boolean focused;
    private boolean windowFocused = true;
    private boolean cursorVisible = true;
    private boolean blinkScheduled;
    private Callback callback;

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public boolean isFieldFocused(boolean editorFocused) {
        return editorFocused && windowFocused;
    }

    public boolean isCursorVisible(boolean editorFocused) {
        return editorFocused && windowFocused && cursorVisible;
    }

    public void onWindowFocusChanged(View view, boolean hasWindowFocus) {
        if (windowFocused == hasWindowFocus) {
            return;
        }
        windowFocused = hasWindowFocus;
        if (!hasWindowFocus) {
            stopBlink();
        } else if (focused) {
            startBlink();
        }
        if (callback != null) {
            callback.onFocusChanged(isFieldFocused(focused));
        }
        view.invalidate();
    }

    public void syncEditorFocus(View view, boolean editorFocused) {
        if (focused == editorFocused) {
            if (!editorFocused) {
                stopBlink();
            }
            view.invalidate();
            return;
        }
        onFocusChanged(view, editorFocused);
    }

    public void onFocusChanged(View view, boolean hasFocus) {
        if (focused == hasFocus) {
            return;
        }
        focused = hasFocus;
        cursorVisible = true;
        if (hasFocus) {
            startBlink();
        } else {
            stopBlink();
        }
        if (callback != null) {
            callback.onFocusChanged(hasFocus);
        }
        view.invalidate();
    }

    public void onSelectionChanged(View view) {
        if (!focused) {
            return;
        }
        cursorVisible = true;
        restartBlink();
        view.invalidate();
    }

    public void onTextChanged(View view) {
        if (!focused) {
            return;
        }
        cursorVisible = true;
        restartBlink();
        view.invalidate();
    }

    public void onDetachedFromWindow() {
        stopBlink();
    }

    private void startBlink() {
        if (blinkScheduled) {
            return;
        }
        blinkScheduled = true;
        handler.postDelayed(blinkRunnable, InputConstants.CURSOR_BLINK_INTERVAL.getIntValue());
    }

    private void stopBlink() {
        blinkScheduled = false;
        handler.removeCallbacks(blinkRunnable);
        cursorVisible = true;
    }

    private void restartBlink() {
        stopBlink();
        startBlink();
    }

    private void toggleCursorBlink() {
        blinkScheduled = false;
        if (!focused || !windowFocused) {
            cursorVisible = false;
            if (callback != null) {
                callback.onCursorBlinkTick(false);
            }
            return;
        }
        cursorVisible = !cursorVisible;
        if (callback != null) {
            callback.onCursorBlinkTick(cursorVisible);
        }
        startBlink();
    }
}
