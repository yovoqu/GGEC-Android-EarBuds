package com.ggec.earbuds.ui.ai.history;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoDialog;

/**
 * 重命名对话框：一个输入框 + 取消/保存。
 *
 * <p>为什么放在 history 包里自己写，而不是复用 {@code ui/ai/AiDialogs}：那里的 {@code confirm}
 * 是对话页删除单条消息用的，没有输入框这一路；把历史面板的需求塞进去会让一个只服务对话页的
 * 工具类开始承担第二个调用方的形状。</p>
 *
 * <p>有意<b>不</b>在输入为空时禁用"保存"按钮：{@code YoDialog} 的托管组件只有在
 * {@code Builder.show()} 这条路径上才会被登记释放，先 {@code build()} 再调 {@code setActionEnabled}
 * 会漏掉这一步。空标题改由 ViewModel 挡下并提示（见 {@link AiHistoryViewModel#rename}）。</p>
 */
final class AiHistoryRenameDialog {

    /** 标题长度上限与领域里的 {@code AiTitlePolicy.MAX_RENAMED_CODE_POINTS} 一致。 */
    private static final int MAX_TITLE_LENGTH = 40;

    private AiHistoryRenameDialog() {
    }

    /** 用户确认后的回调：拿到的是原始输入，压缩空白、截断长度都由会话侧负责。 */
    interface Sink {
        void onTitle(@NonNull String title);
    }

    /**
     * @param initialTitle 输入框初值。会话还没起名时传空串——让用户从头写，
     *                     而不是先看到"未命名对话"再删掉它
     */
    static void show(@NonNull Context context, @Nullable String initialTitle, @NonNull Sink sink) {
        YoTextInput field = YoTextInput.create(context)
                .setText(initialTitle == null ? "" : initialTitle)
                .setPlaceholder(context.getString(R.string.ai_history_rename_hint))
                .setMaxLength(MAX_TITLE_LENGTH)
                .setShowCounter(true)
                .setShowErrorEnabled(true)
                .build();
        YoDialog.create(context)
                .kind(YoDialog.Kind.INPUT)
                .hosted(field)
                .setTitle(context.getString(R.string.ai_history_rename_title))
                .setActions(
                        YoDialog.Action.negative(context.getString(R.string.ai_cancel), null),
                        YoDialog.Action.positive(context.getString(R.string.ai_settings_save),
                                () -> sink.onTitle(String.valueOf(field.getText()))))
                .show();
    }
}
