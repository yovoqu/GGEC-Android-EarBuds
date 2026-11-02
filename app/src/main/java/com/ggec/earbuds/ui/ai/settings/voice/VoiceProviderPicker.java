package com.ggec.earbuds.ui.ai.settings.voice;

import android.content.Context;
import android.view.Gravity;
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
 * 服务商选择：两张并排的选择瓦片。
 *
 * <p>只有两三家时，瓦片能在一次扫视里同时给出"有哪几家、当前哪家、各家 Key 状态"；
 * 下拉会把这三件事拆成"点开才知道"，列表行则要三行。选中用品牌描边 + 实心圆点。</p>
 */
final class VoiceProviderPicker {

    interface Listener {
        void onSelectProvider(@NonNull String modelId);
    }

    private final Context context;
    private final AiTheme theme;
    private final Listener listener;
    private final LinearLayout view;
    private final LinearLayout tiles;
    @Nullable
    private String signature;

    VoiceProviderPicker(@NonNull Context context, @NonNull AiTheme theme,
                        @NonNull Listener listener) {
        this.context = context;
        this.theme = theme;
        this.listener = listener;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.addView(VoiceSectionHeader.titleOnly(theme, context,
                context.getString(R.string.ai_settings_voice_section_provider)), VoiceCard.matchWrap());
        tiles = new LinearLayout(context);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = VoiceCard.matchWrap();
        params.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        params.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        view.addView(tiles, params);
    }

    @NonNull
    View view() {
        return view;
    }

    void bind(@NonNull AiSettingsUiState.Voice voice) {
        StringBuilder next = new StringBuilder();
        for (AiSettingsUiState.VoiceOption option : voice.options()) {
            next.append(option.id()).append(':').append(option.name()).append(':')
                    .append(option.current()).append(':').append(option.configured()).append('\u0000');
        }
        if (next.toString().equals(signature)) {
            return;
        }
        signature = next.toString();
        tiles.removeAllViews();
        List<AiSettingsUiState.VoiceOption> options = voice.options();
        for (int i = 0; i < options.size(); i++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) {
                params.setMarginStart(theme.dp(AiTheme.SPACE_M));
            }
            tiles.addView(tile(options.get(i)), params);
        }
    }

    @NonNull
    private View tile(@NonNull AiSettingsUiState.VoiceOption option) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(theme.dp(AiTheme.SPACE_L), theme.dp(14f),
                theme.dp(AiTheme.SPACE_L), theme.dp(14f));
        tile.setBackground(option.current()
                ? theme.pressable(theme.roundedStroke(theme.brandSoft, AiTheme.RADIUS_ROW,
                theme.brand, 1.5f), AiTheme.RADIUS_ROW)
                : theme.pressable(theme.roundedStroke(theme.fill, AiTheme.RADIUS_ROW,
                theme.cardStroke, 1), AiTheme.RADIUS_ROW));
        tile.setContentDescription(option.name() + " " + context.getString(option.configured()
                ? R.string.ai_settings_voice_provider_ready
                : R.string.ai_settings_voice_provider_needs_key));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        tile.addView(top, VoiceCard.matchWrap());
        top.addView(VoiceCard.dot(context, theme, option.current()));
        TextView name = theme.text(AiTheme.TEXT_BODY, theme.text1, AiTheme.Weight.MEDIUM);
        name.setText(option.name());
        top.addView(name, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView keyState = theme.text(AiTheme.TEXT_CAPTION,
                option.configured() ? theme.text2 : theme.warning);
        keyState.setText(option.configured()
                ? R.string.ai_settings_voice_provider_ready
                : R.string.ai_settings_voice_provider_needs_key);
        LinearLayout.LayoutParams keyParams = VoiceCard.matchWrap();
        keyParams.topMargin = theme.dp(6f);
        keyParams.setMarginStart(theme.dp(14f + AiTheme.SPACE_M));
        tile.addView(keyState, keyParams);

        tile.setOnClickListener(v -> {
            if (!option.current()) {
                listener.onSelectProvider(option.id());
            }
        });
        return tile;
    }
}
