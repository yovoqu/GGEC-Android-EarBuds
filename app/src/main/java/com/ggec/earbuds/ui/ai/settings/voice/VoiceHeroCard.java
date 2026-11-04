package com.ggec.earbuds.ui.ai.settings.voice;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.settings.AiSettingsUiState;

/**
 * 语音设置顶部的身份卡：现在会连哪一个模型、能不能用。
 *
 * <p>先给身份面（品牌渐变底 + 声波图标 + 服务商名），再把"这次会连哪个模型、是否就绪"
 * 压在同一张卡里。模型 id 可能很长，所以它独占第二行并单行省略，不去和右侧状态胶囊抢宽度。</p>
 */
final class VoiceHeroCard {

    interface Listener {
        void onEditKey();
    }

    private static final float ORB = 52f;

    private final AiTheme theme;
    private final Listener listener;
    private final LinearLayout card;
    private final ImageView orbIcon;
    private final TextView name;
    private final TextView subtitle;
    private final TextView status;

    @Nullable
    private AiSettingsUiState.Voice current;
    @Nullable
    private String signature;

    VoiceHeroCard(@NonNull Context context, @NonNull AiTheme theme, @NonNull Listener listener) {
        this.theme = theme;
        this.listener = listener;
        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(theme.dp(18f), theme.dp(18f), theme.dp(18f), theme.dp(18f));
        card.setBackground(theme.gradientStroke(theme.brandSoft, theme.card,
                AiTheme.RADIUS_SHEET, theme.cardStroke, 1));
        card.setClipToOutline(true);
        card.setOnClickListener(view -> {
            if (current != null && !current.configured()) {
                listener.onEditKey();
            }
        });

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, VoiceCard.matchWrap());

        FrameLayout orb = new FrameLayout(context);
        orb.setBackground(theme.circle(theme.brand));
        orbIcon = theme.icon(R.drawable.ic_ai_call_entry, theme.onBrand);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                theme.dp(26f), theme.dp(26f));
        iconParams.gravity = Gravity.CENTER;
        orb.addView(orbIcon, iconParams);
        top.addView(orb, new LinearLayout.LayoutParams(theme.dp(ORB), theme.dp(ORB)));

        name = theme.text(AiTheme.TEXT_TITLE, theme.text1, AiTheme.Weight.MEDIUM);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.setMarginStart(theme.dp(14f));
        top.addView(name, nameParams);

        status = theme.text(AiTheme.TEXT_CAPTION, theme.brand, AiTheme.Weight.MEDIUM);
        status.setGravity(Gravity.CENTER);
        status.setPadding(theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_XS),
                theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_XS));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMarginStart(theme.dp(AiTheme.SPACE_S));
        top.addView(status, statusParams);

        subtitle = theme.text(AiTheme.TEXT_CAPTION, theme.text2);
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams subParams = VoiceCard.matchWrap();
        subParams.topMargin = theme.dp(4f);
        subParams.setMarginStart(theme.dp(ORB + 14f));
        card.addView(subtitle, subParams);
    }

    @NonNull
    View view() {
        return card;
    }

    void bind(@NonNull AiSettingsUiState.Voice voice) {
        current = voice;
        String next = voice.configured() + "\u0000" + voice.modelName()
                + "\u0000" + voice.realtimeModelId();
        if (next.equals(signature)) {
            return;
        }
        signature = next;
        name.setText(voice.modelName());
        if (voice.realtimeModelId().isEmpty()) {
            // 还没拉取/选中模型时，只说"尚未拉取"，不把厂商预设伪装成已选模型。
            subtitle.setText(R.string.ai_settings_voice_status_subtitle_empty);
        } else {
            subtitle.setText(theme.context.getString(R.string.ai_settings_voice_status_subtitle,
                    voice.realtimeModelId()));
        }
        if (voice.configured()) {
            status.setText(R.string.ai_settings_voice_status_ready);
            status.setTextColor(theme.brand);
            status.setBackground(theme.rounded(theme.brandSoft, 10f));
            orbIcon.setImageResource(R.drawable.ic_ai_call_entry);
        } else {
            status.setText(R.string.ai_settings_voice_status_key_missing);
            status.setTextColor(theme.warning);
            status.setBackground(theme.rounded(theme.warningSoft, 10f));
            orbIcon.setImageResource(R.drawable.ic_ai_warning);
        }
    }
}
