package com.ggec.earbuds.ui.ai.settings.voice;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.settings.AiSettingsUiState;
import com.ggec.earbuds.ui.common.YoComponents;

/**
 * 语音交流模式的设置视图（整页右侧面板）。
 *
 * <p>信息结构自上而下：</p>
 * <ol>
 *   <li>{@link VoiceHeroCard} —— 概览：服务商下拉、状态，以及用横/竖线组织的地址、Key、实时模型；</li>
 *   <li>{@link VoiceModelCard} —— 拉取实时模型并挑一个；</li>
 *   <li>{@link VoiceDuplexCard} —— 通话设置：交流模式（全双工 / 半双工）切换。</li>
 * </ol>
 *
 * <p>视图只做"状态 → 图纸"，不包含任何业务判断；状态来自 MVVM 的 {@code Voice} 不可变快照。</p>
 */
public final class VoiceSettingsView {

    public interface Listener {
        void onSelectVoiceModel(@NonNull String modelId);
        void onSelectRealtimeModel(@NonNull String modelId);
        void onEditVoiceKey();
        void onEditVoiceUrl();
        void onFetchVoiceModels();
        void onSelectDefaultDuplex(@NonNull String duplexId);
    }

    private final AiTheme theme;
    private final LinearLayout view;
    private final VoiceHeroCard heroCard;
    private final VoiceModelCard modelCard;
    private final VoiceDuplexCard duplexCard;

    public VoiceSettingsView(@NonNull Context context, @NonNull AiTheme theme,
                             @NonNull YoComponents components,
                             @NonNull Listener listener) {
        this.theme = theme;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);

        heroCard = new VoiceHeroCard(context, theme, components, new VoiceHeroCard.Listener() {
            @Override
            public void onSelectProvider(@NonNull String modelId) {
                listener.onSelectVoiceModel(modelId);
            }

            @Override
            public void onEditUrl() {
                listener.onEditVoiceUrl();
            }

            @Override
            public void onEditKey() {
                listener.onEditVoiceKey();
            }
        });
        addInset(heroCard.view(), AiTheme.SPACE_L);

        modelCard = new VoiceModelCard(context, theme, components, new VoiceModelCard.Listener() {
            @Override
            public void onFetch() {
                listener.onFetchVoiceModels();
            }

            @Override
            public void onSelectModel(@NonNull String modelId) {
                listener.onSelectRealtimeModel(modelId);
            }
        });
        view.addView(modelCard.view(), VoiceCard.matchWrap());

        duplexCard = new VoiceDuplexCard(context, theme, components, listener::onSelectDefaultDuplex);
        view.addView(duplexCard.view(), VoiceCard.matchWrap());

        View tail = new View(context);
        tail.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(AiTheme.SPACE_L)));
        view.addView(tail);
    }

    public VoiceSettingsView(@NonNull Context context, @NonNull AiTheme theme,
                             @NonNull Listener listener) {
        this(context, theme, new YoComponents(), listener);
    }

    @NonNull
    public View view() {
        return view;
    }

    public void setVisible(boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void bind(@NonNull AiSettingsUiState.Voice voice) {
        heroCard.bind(voice);
        modelCard.bind(voice);
        modelCard.setVisible(true);
        duplexCard.bind(voice);
    }

    private void addInset(@NonNull View child, float topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(topMarginDp);
        params.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        params.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        view.addView(child, params);
    }
}
