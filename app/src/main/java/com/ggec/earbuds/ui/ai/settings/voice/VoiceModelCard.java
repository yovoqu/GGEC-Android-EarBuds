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
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;

import java.util.List;

/**
 * 语音模型卡：一个「拉取/重新拉取」动作 + 实时模型单选列表（YoList，固定高度，内部上下滑动）。
 */
final class VoiceModelCard {

    interface Listener {
        void onFetch();
        void onSelectModel(@NonNull String modelId);
    }

    private static final float LIST_FIXED_HEIGHT_DP = 240f;

    private final Context context;
    private final AiTheme theme;
    private final Listener listener;
    private final LinearLayout view;
    private final TextView title;
    private final TextView action;
    private final YoList list;
    private boolean updatingSelection;
    @Nullable
    private AiSettingsUiState.Voice current;
    @Nullable
    private String signature;

    VoiceModelCard(@NonNull Context context, @NonNull AiTheme theme,
                   @NonNull YoComponents components,
                   @NonNull Listener listener) {
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

        list = YoList.create(context)
                .setSelectable(true)
                .setSelectionIndicator(YoList.SelectionIndicator.RADIO)
                .setScrollBarEnabled(true)
                .build();
        components.own(list::destroy);

        list.setOnItemClickListener(position -> {
            AiSettingsUiState.Voice voice = current;
            if (voice != null && voice.models().isEmpty()) {
                if (!voice.fetching()) {
                    listener.onFetch();
                }
            }
        });

        list.setOnSelectionChangeListener((oldId, newId) -> {
            if (updatingSelection) {
                return;
            }
            AiSettingsUiState.Voice voice = current;
            if (voice == null) {
                return;
            }
            int index = list.indexOfId(newId);
            if (index >= 0 && index < voice.models().size()) {
                listener.onSelectModel(voice.models().get(index).id());
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(LIST_FIXED_HEIGHT_DP));
        view.addView(list.getView(), params);
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

        List<AiSettingsUiState.VoiceModelOption> models = voice.models();
        String modelsSig = rowsSignature(models);
        if (!modelsSig.equals(signature)) {
            signature = modelsSig;
            list.clear();
            if (models.isEmpty()) {
                list.setSelectable(false);
                list.setSelectionIndicator(YoList.SelectionIndicator.NONE);
                list.addItem(ListPrefixSpec.iconTinted(R.drawable.ic_ai_refresh, theme.text2),
                        ListTextSpec.of(context.getString(R.string.ai_settings_voice_models_empty),
                                context.getString(R.string.ai_settings_voice_models_empty_hint)),
                        ListSuffixSpec.arrow());
            } else {
                list.setSelectable(true);
                list.setSelectionIndicator(YoList.SelectionIndicator.RADIO);
                for (AiSettingsUiState.VoiceModelOption model : models) {
                    list.addItem(ListTextSpec.of(model.id()));
                }
            }
        }
        applySelection(voice);
    }

    private void applySelection(@NonNull AiSettingsUiState.Voice voice) {
        List<AiSettingsUiState.VoiceModelOption> models = voice.models();
        if (models.isEmpty()) {
            return;
        }
        int selectedIndex = -1;
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).id().equals(voice.realtimeModelId())) {
                selectedIndex = i;
                break;
            }
        }
        updatingSelection = true;
        try {
            if (selectedIndex >= 0 && selectedIndex < list.getItemCount()) {
                long stableId = list.getStableIdAt(selectedIndex);
                if (list.getSelectedStableId() != stableId) {
                    list.setSelectedStableId(stableId);
                }
            } else {
                list.clearSelection();
            }
        } finally {
            updatingSelection = false;
        }
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
