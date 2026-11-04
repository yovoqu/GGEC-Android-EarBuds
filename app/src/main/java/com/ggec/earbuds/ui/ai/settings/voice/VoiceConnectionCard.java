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

/**
 * 连接卡：接口地址与 API Key。
 *
 * <p>Key 是这一页唯一的阻塞项，尾部值直说"已填写/未填写"，未填写用警示色。接口地址对每一家
 * 都出现（Qwen 也显示它预设的兼容模式地址），尾部一句"官方/自定义"回答"到底连的是哪"。</p>
 */
final class VoiceConnectionCard {

    interface Listener {
        void onEditBaseUrl();
        void onEditKey();
    }

    private final Context context;
    private final AiTheme theme;
    private final Listener listener;
    private final LinearLayout view;
    private final LinearLayout card;
    @Nullable
    private String signature;

    VoiceConnectionCard(@NonNull Context context, @NonNull AiTheme theme,
                        @NonNull Listener listener) {
        this.context = context;
        this.theme = theme;
        this.listener = listener;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.addView(VoiceSectionHeader.titleOnly(theme, context,
                context.getString(R.string.ai_settings_connection_section)), VoiceCard.matchWrap());
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

    void bind(@NonNull AiSettingsUiState.Voice voice) {
        String next = voice.baseUrl() + "\u0000"
                + voice.baseUrlCustom() + "\u0000" + voice.configured();
        if (next.equals(signature)) {
            return;
        }
        signature = next;
        card.removeAllViews();
        // 接口地址对每一家都显示：Qwen 走的是百炼兼容模式地址，用户同样要看得见自己连的是哪。
        card.addView(baseUrlRow(voice));
        VoiceCard.addDivider(card, context, theme);
        card.addView(keyRow(voice));
    }

    @NonNull
    private View baseUrlRow(@NonNull AiSettingsUiState.Voice voice) {
        VoiceCard.Row row = VoiceCard.row(context, theme, R.drawable.ic_ai_globe);
        row.title.setText(R.string.ai_settings_voice_url);
        row.subtitle.setVisibility(View.VISIBLE);
        row.subtitle.setText(host(voice.baseUrl()));
        row.trailing.addView(VoiceCard.chip(context, theme,
                context.getString(voice.baseUrlCustom()
                        ? R.string.ai_settings_voice_url_custom
                        : R.string.ai_settings_voice_url_official),
                voice.baseUrlCustom() ? theme.brand : theme.text3,
                voice.baseUrlCustom() ? theme.brandSoft : theme.fill));
        row.trailing.addView(VoiceCard.chevron(context, theme));
        row.view.setOnClickListener(v -> listener.onEditBaseUrl());
        row.view.setContentDescription(context.getString(R.string.ai_settings_voice_url)
                + " " + host(voice.baseUrl()));
        return row.view;
    }

    @NonNull
    private View keyRow(@NonNull AiSettingsUiState.Voice voice) {
        VoiceCard.Row row = VoiceCard.row(context, theme, R.drawable.ic_ai_key);
        row.title.setText(R.string.ai_settings_voice_key);
        row.subtitle.setVisibility(View.VISIBLE);
        row.subtitle.setText(voice.configured()
                ? R.string.ai_settings_voice_provider_ready
                : R.string.ai_settings_voice_provider_needs_key);
        row.subtitle.setTextColor(voice.configured() ? theme.text3 : theme.warning);
        row.trailing.addView(VoiceCard.chevron(context, theme));
        row.view.setOnClickListener(v -> listener.onEditKey());
        row.view.setContentDescription(context.getString(R.string.ai_settings_voice_key) + " "
                + context.getString(voice.configured()
                ? R.string.ai_settings_voice_provider_ready
                : R.string.ai_settings_voice_provider_needs_key));
        return row.view;
    }

    /** 副标题里不放协议头：一段 https:// 会把地址挤到看不见，而它从来不变。 */
    @NonNull
    static String host(@NonNull String baseUrl) {
        String value = baseUrl.strip();
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
