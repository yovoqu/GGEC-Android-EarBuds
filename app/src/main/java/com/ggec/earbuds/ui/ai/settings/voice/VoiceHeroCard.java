package com.ggec.earbuds.ui.ai.settings.voice;

import android.content.Context;
import android.graphics.Typeface;
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
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.settings.AiSettingsUiState;
import com.yohu.youi.api.controls.YoSelect;

/**
 * 语音设置的概览卡：一张卡里回答"用哪家、能不能用、连到哪、哪个模型"。
 *
 * <p>原来"服务商"是卡片外单独的一块瓦片区，与概览重复、又占一屏。现在服务商收进本卡：
 * 顶部是服务商名 + 下拉箭头（点击展开菜单切换），下面用一条整宽水平线分出事实网格，
 * 地址 / Key / 实时模型三列之间用竖线分隔，信息一眼扫完。</p>
 */
final class VoiceHeroCard {

    interface Listener {
        void onSelectProvider(@NonNull String modelId);
        void onEditUrl();
        void onEditKey();
    }

    private static final float ORB = 52f;

    private final Context context;
    private final AiTheme theme;
    private final Listener listener;
    private final LinearLayout card;
    private final ImageView orbIcon;
    private final YoSelect providerSelect;
    private final TextView status;
    private final TextView addressValue;
    private final TextView keyValue;
    private final TextView modelValue;

    @Nullable
    private AiSettingsUiState.Voice current;

    VoiceHeroCard(@NonNull Context context, @NonNull AiTheme theme, @NonNull Listener listener) {
        this.context = context;
        this.theme = theme;
        this.listener = listener;
        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(theme.dp(18f), theme.dp(18f), theme.dp(18f), theme.dp(16f));
        card.setBackground(theme.gradientStroke(theme.brandSoft, theme.card,
                AiTheme.RADIUS_SHEET, theme.cardStroke, 1));
        card.setClipToOutline(true);

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

        // 图标固定在最左；中间直接用 YoUI 的 YoSelect 下拉按钮；右侧是状态。
        providerSelect = YoSelect.create(context)
                .setControlSize(YoSelect.ControlSize.SMALL)
                .setArrowPosition(YoSelect.ArrowPosition.END)
                .setShowDefaultSelectedIcon(true)
                .setOnSelectListener((id, label, position) -> {
                    AiSettingsUiState.Voice voice = current;
                    if (voice != null && id >= 0 && id < voice.options().size()) {
                        listener.onSelectProvider(voice.options().get(id).id());
                    }
                })
                .build();
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        selectParams.setMarginStart(theme.dp(14f));
        top.addView(providerSelect.getView(), selectParams);

        View spacer = new View(context);
        top.addView(spacer, new LinearLayout.LayoutParams(
                0, 1, 1f));

        status = theme.text(AiTheme.TEXT_CAPTION, theme.brand, AiTheme.Weight.MEDIUM);
        status.setGravity(Gravity.CENTER);
        status.setPadding(theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_XS),
                theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_XS));
        top.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams lineParams = VoiceCard.matchWrap();
        lineParams.topMargin = theme.dp(14f);
        lineParams.bottomMargin = theme.dp(10f);
        card.addView(VoiceCard.horizontalLine(context, theme), lineParams);

        LinearLayout facts = new LinearLayout(context);
        facts.setOrientation(LinearLayout.VERTICAL);
        card.addView(facts, VoiceCard.matchWrap());

        LinearLayout firstRow = new LinearLayout(context);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        firstRow.setGravity(Gravity.TOP);
        facts.addView(firstRow, VoiceCard.matchWrap());

        addressValue = addFact(firstRow, R.string.ai_settings_voice_url, 1.7f, () -> listener.onEditUrl());
        firstRow.addView(verticalSpacer());
        keyValue = addFact(firstRow, R.string.ai_settings_voice_key, 1f, () -> listener.onEditKey());

        LinearLayout.LayoutParams rowLine = VoiceCard.matchWrap();
        rowLine.topMargin = theme.dp(8f);
        rowLine.bottomMargin = theme.dp(6f);
        facts.addView(VoiceCard.horizontalLine(context, theme), rowLine);

        LinearLayout secondRow = new LinearLayout(context);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        secondRow.setGravity(Gravity.TOP);
        facts.addView(secondRow, VoiceCard.matchWrap());
        modelValue = addFact(secondRow, R.string.ai_settings_voice_realtime_model, 1f, null);
    }

    @NonNull
    View view() {
        return card;
    }

    void bind(@NonNull AiSettingsUiState.Voice voice) {
        current = voice;
        providerSelect.clearOptions();
        int selectedId = -1;
        for (int i = 0; i < voice.options().size(); i++) {
            AiSettingsUiState.VoiceOption option = voice.options().get(i);
            providerSelect.addOption(i, option.name(), providerIcon(option.id()));
            if (option.current()) {
                selectedId = i;
            }
        }
        providerSelect.setValue(voice.modelName());
        if (selectedId >= 0) {
            providerSelect.setSelectedItemId(selectedId);
        }
        providerSelect.setEnabled(voice.options().size() > 1);
        providerSelect.getView().setContentDescription(
                context.getString(R.string.ai_settings_voice_section_provider)
                        + " " + voice.modelName());
        addressValue.setText(VoiceCard.host(voice.baseUrl()));
        keyValue.setText(voice.configured()
                ? R.string.ai_settings_voice_key_set
                : R.string.ai_settings_voice_key_empty);
        keyValue.setTextColor(voice.configured() ? theme.text1 : theme.warning);
        modelValue.setText(voice.realtimeModelId().isEmpty()
                ? context.getString(R.string.ai_settings_voice_models_empty)
                : voice.realtimeModelId());
        orbIcon.setImageResource(providerIcon(voice.modelId()));

        if (voice.configured()) {
            status.setText(R.string.ai_settings_voice_status_ready);
            status.setTextColor(theme.brand);
            status.setBackground(theme.rounded(theme.brandSoft, 10f));
        } else {
            status.setText(R.string.ai_settings_voice_status_key_missing);
            status.setTextColor(theme.warning);
            status.setBackground(theme.rounded(theme.warningSoft, 10f));
        }
    }

    /** 服务商图标：对话/语音设置里的身份标记；未知服务商退回通用地球图标。 */
    private static int providerIcon(@NonNull String modelId) {
        return switch (modelId) {
            case "chatgpt" -> R.drawable.ic_ai_provider_chatgpt;
            case "qwen" -> R.drawable.ic_ai_provider_qwen;
            default -> R.drawable.ic_ai_globe;
        };
    }

    @NonNull
    private TextView addFact(@NonNull LinearLayout parent, @StringRes int labelRes,
                             float weight, @Nullable Runnable onClick) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(theme.dp(AiTheme.SPACE_S), theme.dp(2f),
                theme.dp(AiTheme.SPACE_S), theme.dp(2f));
        if (onClick != null) {
            column.setBackground(theme.pressable(AiTheme.RADIUS_ROW));
            column.setOnClickListener(view -> onClick.run());
        }

        TextView label = theme.text(11f, theme.text3);
        label.setText(labelRes);
        column.addView(label, VoiceCard.matchWrap());

        TextView value = theme.text(AiTheme.TEXT_CAPTION, theme.text1, AiTheme.Weight.MEDIUM);
        value.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        value.setMaxLines(2);
        value.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams valueParams = VoiceCard.matchWrap();
        valueParams.topMargin = theme.dp(3f);
        column.addView(value, valueParams);

        parent.addView(column, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
        return value;
    }

    @NonNull
    private View verticalSpacer() {
        View line = VoiceCard.verticalLine(context, theme);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.max(1, theme.dp(1)), theme.dp(44f));
        params.topMargin = theme.dp(2f);
        line.setLayoutParams(params);
        return line;
    }
}
