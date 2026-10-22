package com.ggec.earbuds.ui.ai;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.yohu.youi.api.feedback.YoDialog;

/**
 * AI 页面用到的确认对话框。
 *
 * <p>配置界面撤掉之后，剩下的对话框只有"确认删除"这一种；输入框、滑块都随设置页与平台页一起删了。</p>
 */
public final class AiDialogs {

    private AiDialogs() {
    }

    public static void confirm(@NonNull Context context, @NonNull CharSequence title, @NonNull CharSequence message,
                               @NonNull CharSequence positive, boolean destructive, @NonNull Runnable onConfirm) {
        YoDialog.Action ok = YoDialog.Action.positive(positive, onConfirm);
        YoDialog.create(context)
                .setTitle(title)
                .setMessage(message)
                .setActions(
                        YoDialog.Action.negative(context.getString(R.string.ai_cancel), null),
                        destructive ? ok.alert() : ok)
                .show();
    }
}
