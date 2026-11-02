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
 *   <li>{@link VoiceHeroCard} —— 现在会连到哪、能不能用（唯一的阻塞项直接可点）；</li>
 *   <li>{@link VoiceProviderPicker} —— 服务商二选一瓦片；</li>
 *   <li>{@link VoiceConnectionCard} —— 接口地址（仅 ChatGPT）与 API Key；</li>
 *   <li>{@link VoiceModelCard} —— 拉取全部模型并挑一个；</li>
 *   <li>{@link VoiceDialectCard} —— 线路方言（仅 ChatGPT）。</li>
 * </ol>
 *
 * <p>视图只做"状态 → 图纸"，不包含任何业务判断；状态来自 MVVM 的 {@code Voice} 不可变快照。
 * 每个区块只建一次控件、只在自身签名变化时重绑；模式切换只切可见性，不重建子树。</p>
 */
public final class VoiceSettingsView {

    public interface Listener {
        void onSelectVoiceModel(@NonNull String modelId);
        void onSelectRealtimeModel(@NonNull String modelId);
        void onSelectDialect(@NonNull String dialectId);
        void onEditVoiceKey();
        void onEditVoiceUrl();
        void onFetchVoiceModels();
    }

    private final AiTheme theme;
    private final LinearLayout view;
    private final VoiceHeroCard heroCard;
    private final VoiceProviderPicker providerPicker;
    private final VoiceConnectionCard connectionCard;
    private final VoiceModelCard modelCard;
    private final VoiceDialectCard dialectCard;

    public VoiceSettingsView(@NonNull Context context, @NonNull AiTheme theme,
                             @NonNull YoComponents components, @NonNull Listener listener) {
        this.theme = theme;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);

        heroCard = new VoiceHeroCard(context, theme, listener::onEditVoiceKey);
        addInset(heroCard.view(), AiTheme.SPACE_L);

        providerPicker = new VoiceProviderPicker(context, theme, listener::onSelectVoiceModel);
        view.addView(providerPicker.view(), VoiceCard.matchWrap());

        connectionCard = new VoiceConnectionCard(context, theme, new VoiceConnectionCard.Listener() {
            @Override
            public void onEditBaseUrl() {
                listener.onEditVoiceUrl();
            }

            @Override
            public void onEditKey() {
                listener.onEditVoiceKey();
            }
        });
        view.addView(connectionCard.view(), VoiceCard.matchWrap());

        modelCard = new VoiceModelCard(context, theme, new VoiceModelCard.Listener() {
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

        dialectCard = new VoiceDialectCard(context, theme, components, listener::onSelectDialect);
        view.addView(dialectCard.view(), VoiceCard.matchWrap());

        View tail = new View(context);
        tail.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(AiTheme.SPACE_L)));
        view.addView(tail);
    }

    @NonNull
    public View view() {
        return view;
    }

    public void setVisible(boolean visible) {
        // GONE：非当前面板不参与测量，共用槽的高度只等于当前面板，避免被另一块撑高。
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void bind(@NonNull AiSettingsUiState.Voice voice) {
        heroCard.bind(voice);
        providerPicker.bind(voice);
        connectionCard.bind(voice);
        modelCard.bind(voice);
        modelCard.setVisible(true);
        boolean chatGpt = voice.chatGpt();
        dialectCard.setVisible(chatGpt);
        if (chatGpt) {
            dialectCard.bind(voice);
        }
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
