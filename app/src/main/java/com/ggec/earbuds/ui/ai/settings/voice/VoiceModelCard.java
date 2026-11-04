package com.ggec.earbuds.ui.ai.settings.voice;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.settings.AiSettingsUiState;

import java.util.List;

/**
 * 语音模型卡：一个「拉取/重新拉取」动作 + 拉回来的实时模型。
 *
 * <p>只列能接实时会话的模型（服务商目录里的聊天/向量/合成等已在网关筛掉）。
 * 每一行右侧的胶囊是 SDK 类别。选中行以实心圆点作主信号，胶囊不重复表示选中。</p>
 */
final class VoiceModelCard {

    interface Listener {
        void onFetch();
        void onSelectModel(@NonNull String modelId);
    }

    private final Context context;
    private final AiTheme theme;
    private final Listener listener;
    private final LinearLayout view;
    private final TextView title;
    private final TextView action;
    private final LinearLayout card;
    @Nullable
    private AiSettingsUiState.Voice current;
    @Nullable
    private String signature;

    VoiceModelCard(@NonNull Context context, @NonNull AiTheme theme, @NonNull Listener listener) {
        this.context = context;
        this.theme = theme;
        this.listener = listener;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        title = VoiceSectionHeader.title(theme);
        action = VoiceSectionHeader.action(theme);
        view.addView(VoiceSectionHeader.withAction(theme, context, title, action, () -> {
            AiSettingsUiState.Voice voice = current;
            if (voice != null && !voice.fetching()) {
                listener.onFetch();
            }
        }), VoiceCard.matchWrap());
        card = VoiceCard.plate(context, theme);
        LinearLayout.LayoutParams params = VoiceCard.matchWrap();
        params.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        params.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        view.addView(card, params);
    }

    @NonNull
    View view() {
        return view;
    }

    void setVisible(boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    void bind(@NonNull AiSettingsUiState.Voice voice) {
        current = voice;
        // 标题/动作随拉取状态变化，只改文字与可用性，不动卡片内容。
        action.setEnabled(!voice.fetching());
        if (voice.fetching()) {
            title.setText(R.string.ai_settings_voice_model_section);
            action.setText(R.string.ai_settings_voice_models_loading);
        } else {
            title.setText(context.getString(R.string.ai_settings_voice_model_section_count,
                    voice.models().size()));
            action.setText(voice.models().isEmpty()
                    ? R.string.ai_settings_voice_fetch
                    : R.string.ai_settings_voice_fetch_retry);
        }
        // 只有"行集合"真的变了才重建卡片：拉取进行/结束不再整块 remove/add，避免高度来回跳。
        String rows = voice.realtimeModelId() + "\u0000" + rowsSignature(voice.models());
        if (rows.equals(signature)) {
            return;
        }
        signature = rows;
        card.removeAllViews();
        List<AiSettingsUiState.VoiceModelOption> models = voice.models();
        if (models.isEmpty()) {
            card.addView(emptyRow());
            return;
        }
        for (int i = 0; i < models.size(); i++) {
            card.addView(modelRow(models.get(i), models.get(i).id().equals(voice.realtimeModelId())));
            if (i < models.size() - 1) {
                VoiceCard.addDivider(card, context, theme);
            }
        }
    }

    @NonNull
    private View modelRow(@NonNull AiSettingsUiState.VoiceModelOption model, boolean selected) {
        VoiceCard.Row row = VoiceCard.row(context, theme, 0);
        row.view.addView(VoiceCard.dot(context, theme, selected), 0);
        row.title.setText(model.id());
        row.view.setOnClickListener(v -> listener.onSelectModel(model.id()));
        row.view.setContentDescription(model.id());
        return row.view;
    }

    @NonNull
    private View emptyRow() {
        VoiceCard.Row row = VoiceCard.row(context, theme, R.drawable.ic_ai_refresh);
        row.title.setText(R.string.ai_settings_voice_models_empty);
        row.subtitle.setVisibility(View.VISIBLE);
        row.subtitle.setText(R.string.ai_settings_voice_models_empty_hint);
        row.trailing.addView(VoiceCard.chevron(context, theme));
        row.view.setOnClickListener(v -> {
            if (current != null && !current.fetching()) {
                listener.onFetch();
            }
        });
        row.view.setContentDescription(context.getString(R.string.ai_settings_voice_models_empty));
        return row.view;
    }

    @NonNull
    private static String rowsSignature(@NonNull List<AiSettingsUiState.VoiceModelOption> models) {
        StringBuilder result = new StringBuilder();
        for (AiSettingsUiState.VoiceModelOption model : models) {
            result.append(model.id()).append('\u0000');
        }
        return result.toString();
    }
}
