package com.ggec.earbuds.ui.ai.settings.voice;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.settings.AiSettingsUiState;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.controls.YoSegmentButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 线路方言卡：只有 ChatGPT 这条线有，二选一。
 *
 * <p>方言是同一条实时协议的两种握手细节，互斥、默认第一个——分段控件的语义正好，
 * 比下拉少一次展开。控件放在卡片里，与上面的 Key/地址保持同一套卡片节奏。</p>
 */
final class VoiceDialectCard {

    interface Listener {
        void onSelectDialect(@NonNull String dialectId);
    }

    private final Context context;
    private final AiTheme theme;
    private final YoComponents components;
    private final Listener listener;
    private final LinearLayout view;
    private final LinearLayout host;
    @Nullable
    private YoSegmentButton segment;
    private List<AiSettingsUiState.DialectOption> options = List.of();
    private boolean binding;

    VoiceDialectCard(@NonNull Context context, @NonNull AiTheme theme,
                     @NonNull YoComponents components, @NonNull Listener listener) {
        this.context = context;
        this.theme = theme;
        this.components = components;
        this.listener = listener;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.addView(VoiceSectionHeader.titleOnly(theme, context,
                context.getString(R.string.ai_settings_voice_dialect)), VoiceCard.matchWrap());
        LinearLayout card = VoiceCard.plate(context, theme);
        LinearLayout.LayoutParams cardParams = VoiceCard.matchWrap();
        cardParams.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        cardParams.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        view.addView(card, cardParams);
        host = new LinearLayout(context);
        host.setOrientation(LinearLayout.VERTICAL);
        host.setPadding(theme.dp(AiTheme.SPACE_S), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.SPACE_S), theme.dp(AiTheme.SPACE_S));
        card.addView(host, VoiceCard.matchWrap());
    }

    @NonNull
    View view() {
        return view;
    }

    void setVisible(boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    void bind(@NonNull AiSettingsUiState.Voice voice) {
        options = new ArrayList<>(voice.dialects());
        if (segment == null) {
            YoSegmentButton.Builder builder = YoSegmentButton.create(context)
                    .setType(YoSegmentButton.SegmentType.SINGLE_CHOICE)
                    .setSize(YoSegmentButton.Size.SMALL)
                    .setLayoutMode(YoSegmentButton.LayoutMode.DISTRIBUTE)
                    .setSelectedIndex(selectedIndex(voice.dialectId()))
                    .setOnSelectionChangeListener(index -> {
                        if (binding) {
                            return;
                        }
                        if (index >= 0 && index < options.size()) {
                            listener.onSelectDialect(options.get(index).id());
                        }
                    });
            for (int i = 0; i < options.size(); i++) {
                builder.addSegment(i, context.getString(options.get(i).label()));
            }
            segment = builder.build();
            components.own(segment::destroy);
            host.addView(segment.getView(), VoiceCard.matchWrap());
            return;
        }
        int index = selectedIndex(voice.dialectId());
        if (segment.getSelectedIndex() != index) {
            binding = true;
            segment.setSelectedIndex(index);
            binding = false;
        }
    }

    private int selectedIndex(@NonNull String dialectId) {
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).id().equals(dialectId)) {
                return i;
            }
        }
        return 0;
    }
}
