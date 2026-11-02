package com.ggec.earbuds.ui.ai.history;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.yohu.youi.api.feedback.YoDialog;

/** 历史面板的破坏性确认。改名使用 {@link AiHistoryRenameDialog}。 */
final class AiHistoryDialogs {

    private final AppCompatActivity activity;

    AiHistoryDialogs(@NonNull AppCompatActivity activity) {
        this.activity = activity;
    }

    void confirmDelete(@NonNull Runnable onDelete) {
        YoDialog.create(activity)
                .setTitle(activity.getString(R.string.ai_history_delete_title))
                .setMessage(activity.getString(R.string.ai_history_delete_message))
                .setActions(
                        YoDialog.Action.negative(activity.getString(R.string.ai_cancel), null),
                        YoDialog.Action.positive(activity.getString(R.string.ai_delete), onDelete)
                                .alert())
                .show();
    }

    void confirmClearAll(@NonNull Runnable onClear) {
        YoDialog.create(activity)
                .setTitle(activity.getString(R.string.ai_history_clear_title))
                .setMessage(activity.getString(R.string.ai_history_clear_message))
                .setActions(
                        YoDialog.Action.negative(activity.getString(R.string.ai_cancel), null),
                        YoDialog.Action.positive(
                                activity.getString(R.string.ai_history_clear_all), onClear)
                                .alert())
                .show();
    }
}
