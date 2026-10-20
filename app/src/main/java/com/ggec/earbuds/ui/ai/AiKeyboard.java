package com.ggec.earbuds.ui.ai;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** 键盘收起。对话框和输入区共用一份实现。 */
public final class AiKeyboard {

    private AiKeyboard() {
    }

    /** 从对话框上下文收起输入法（对话框关闭后键盘不应留在页面上）。 */
    public static void hide(@NonNull Context context) {
        Activity activity = activityOf(context);
        if (activity == null) {
            return;
        }
        View decor = activity.getWindow().getDecorView();
        WindowCompat.getInsetsController(activity.getWindow(), decor).hide(WindowInsetsCompat.Type.ime());
    }

    /** 从某个输入控件收起输入法并清掉焦点。 */
    public static void hide(@NonNull View input) {
        InputMethodManager manager =
                (InputMethodManager) input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
        input.clearFocus();
    }

    @Nullable
    private static Activity activityOf(@NonNull Context context) {
        Context current = context;
        while (current instanceof ContextWrapper wrapper) {
            if (current instanceof Activity activity) {
                return activity;
            }
            current = wrapper.getBaseContext();
        }
        return null;
    }
}
