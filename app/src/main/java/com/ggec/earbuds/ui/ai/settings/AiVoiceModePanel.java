package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.controls.YoSelect;

import java.util.List;
import java.util.ArrayList;

/** 语音模式面板：厂商、ChatGPT 扩展设置与 Key。所有控件随页面创建一次。 */
final class AiVoiceModePanel {

    interface Listener {
        void onSelectVoiceModel(@NonNull String modelId);
        void onSelectRealtimeModel(@NonNull String modelId);
        void onSelectDialect(@NonNull String dialectId);
        void onEditVoiceKey();
        void onEditVoiceUrl();
        void onFetchRealtimeModels();
    }

    private static final int ROW_MODEL = 0;
    private static final int ROW_OTHER_KEY = 1;
    private static final int ROW_URL = 0;
    private static final int ROW_FETCH = 1;
    private static final int ROW_REALTIME = 2;
    private static final int ROW_DIALECT = 3;
    private static final int ROW_KEY = 4;

    private final Context context;
    private final AiTheme theme;
    private final Listener listener;
    private final AiVoiceRows rows;
    private final LinearLayout view;
    private final YoList commonList;
    private final YoList chatGptList;
    private final YoSelect vendorSelect;
    private final YoSelect realtimeSelect;
    private final YoSelect dialectSelect;

    @Nullable
    private String chatGptSignature;
    @Nullable
    private String otherVoiceSignature;
    @Nullable
    private String vendorOptions;
    @Nullable
    private String realtimeOptions;
    @Nullable
    private String dialectOptions;
    @Nullable
    private AiSettingsUiState.Voice current;

    AiVoiceModePanel(@NonNull Context context, @NonNull AiTheme theme,
                     @NonNull YoComponents components, @NonNull Listener listener) {
        this.context = context;
        this.theme = theme;
        this.listener = listener;
        this.rows = new AiVoiceRows(context, theme);
        this.view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);

        vendorSelect = createSelect((index, text, itemId) -> {
            AiSettingsUiState.Voice voice = current;
            if (voice != null && itemId >= 0 && itemId < voice.options().size()) {
                listener.onSelectVoiceModel(voice.options().get(itemId).id());
            }
        }, components);
        commonList = YoList.create(context)
                .setOnItemClickListener(this::onCommonRow)
                .build();
        components.own(commonList::destroy);
        commonList.addItem(rows.chatPrefix(), rows.modelTitle(),
                ListSuffixSpec.custom(vendorSelect.getView()));
        addCard(commonList, AiTheme.SPACE_L);

        realtimeSelect = createSelect((index, text, itemId) -> {
            AiSettingsUiState.Voice voice = current;
            if (voice != null && itemId >= 0 && itemId < voice.realtimeModels().size()) {
                listener.onSelectRealtimeModel(voice.realtimeModels().get(itemId));
            }
        }, components);
        dialectSelect = createSelect((index, text, itemId) -> {
            AiSettingsUiState.Voice voice = current;
            if (voice != null && itemId >= 0 && itemId < voice.dialects().size()) {
                listener.onSelectDialect(voice.dialects().get(itemId).id());
            }
        }, components);

        chatGptList = YoList.create(context)
                .setOnItemClickListener(this::onChatGptRow)
                .build();
        components.own(chatGptList::destroy);
        chatGptList.addItem(rows.chatPrefix(), rows.urlTitle(),
                ListSuffixSpec.textAndArrow(""));
        chatGptList.addItem(rows.chatPrefix(), rows.fetchTitle(), rows.fetchAction());
        chatGptList.addItem(rows.chatPrefix(), rows.realtimeTitle(),
                ListSuffixSpec.custom(realtimeSelect.getView()));
        chatGptList.addItem(rows.chatPrefix(), rows.dialectTitle(),
                ListSuffixSpec.custom(dialectSelect.getView()));
        chatGptList.addItem(rows.keyPrefix(), rows.keyTitle(),
                ListSuffixSpec.none());
        addCard(chatGptList, AiTheme.SPACE_S);
    }

    @NonNull
    View view() {
        return view;
    }

    void bind(@NonNull AiSettingsUiState.Voice voice) {
        current = voice;
        bindVendor(voice);
        chatGptList.getView().setVisibility(voice.chatGpt() ? View.VISIBLE : View.GONE);
        if (voice.chatGpt()) {
            bindChatGpt(voice);
        } else {
            bindOtherVoice(voice);
        }
    }

    void setVisible(boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /** 厂商行不重建；只更新已有下拉的选项与值。 */
    private void bindVendor(@NonNull AiSettingsUiState.Voice voice) {
        String nextVendorOptions = AiVoiceSelectBinder.signature(voice.options().size(), index ->
                voice.options().get(index).id() + ":" + voice.options().get(index).name());
        AiVoiceSelectBinder.bind(vendorSelect, vendorOptions, optionsForVoice(voice),
                voice.modelName(), nextVendorOptions);
        vendorOptions = nextVendorOptions;
    }

    private void bindChatGpt(@NonNull AiSettingsUiState.Voice voice) {
        String signature = voice.baseUrl() + "\u0000" + voice.realtimeModelId()
                + "\u0000" + voice.fetching() + "\u0000" + voice.dialectId()
                + "\u0000" + String.join("\u0000", voice.realtimeModels());
        if (signature.equals(chatGptSignature)) {
            return;
        }
        chatGptSignature = signature;
        String nextRealtimeOptions = AiVoiceSelectBinder.signature(voice.realtimeModels().size(), index ->
                voice.realtimeModels().get(index) + ":" + voice.realtimeModels().get(index));
        AiVoiceSelectBinder.bind(realtimeSelect, realtimeOptions,
                labels(voice.realtimeModels()), voice.realtimeModelId(), nextRealtimeOptions);
        realtimeOptions = nextRealtimeOptions;
        String nextDialectOptions = AiVoiceSelectBinder.signature(voice.dialects().size(), index ->
                voice.dialects().get(index).id() + ":" + voice.dialects().get(index).label());
        List<String> dialectLabels = new java.util.ArrayList<>();
        String selectedDialect = voice.dialectId();
        for (AiSettingsUiState.DialectOption option : voice.dialects()) {
            dialectLabels.add(context.getString(option.label()));
            if (option.id().equals(voice.dialectId())) {
                selectedDialect = context.getString(option.label());
            }
        }
        AiVoiceSelectBinder.bind(dialectSelect, dialectOptions, dialectLabels, selectedDialect,
                nextDialectOptions);
        dialectOptions = nextDialectOptions;
        AiVoiceRows.detach(realtimeSelect.getView());
        AiVoiceRows.detach(dialectSelect.getView());
        chatGptList.clear();
        chatGptList.addItem(rows.chatPrefix(), rows.urlTitle(),
                ListSuffixSpec.textAndArrow(voice.baseUrl()));
        chatGptList.addItem(rows.chatPrefix(), rows.fetchTitle(),
                voice.fetching() ? rows.fetching() : rows.fetchAction());
        chatGptList.addItem(rows.chatPrefix(), rows.realtimeTitle(),
                ListSuffixSpec.custom(realtimeSelect.getView()));
        chatGptList.addItem(rows.chatPrefix(), rows.dialectTitle(),
                ListSuffixSpec.custom(dialectSelect.getView()));
        chatGptList.addItem(rows.keyPrefix(), rows.keyTitle(), rows.keyState(voice));
    }

    /** 非 ChatGPT 厂商只需要“厂商 + Key”两行；切换厂商时只更新这一张卡的 Key 状态。 */
    private void bindOtherVoice(@NonNull AiSettingsUiState.Voice voice) {
        String signature = voice.modelId() + "\u0000" + voice.configured();
        if (signature.equals(otherVoiceSignature)) {
            return;
        }
        otherVoiceSignature = signature;
        AiVoiceRows.detach(vendorSelect.getView());
        commonList.clear();
        // 清空后必须重新挂回厂商下拉；这个控件本身不会重建。
        commonList.addItem(rows.chatPrefix(), rows.modelTitle(),
                ListSuffixSpec.custom(vendorSelect.getView()));
        commonList.addItem(rows.keyPrefix(), rows.keyTitle(), rows.keyState(voice));
    }

    private void onCommonRow(int position) {
        if (position == ROW_MODEL) {
            vendorSelect.toggle();
        } else if (position == ROW_OTHER_KEY) {
            listener.onEditVoiceKey();
        }
    }

    private void onChatGptRow(int position) {
        if (position == ROW_URL) {
            listener.onEditVoiceUrl();
        } else if (position == ROW_FETCH) {
            listener.onFetchRealtimeModels();
        } else if (position == ROW_REALTIME) {
            realtimeSelect.toggle();
        } else if (position == ROW_DIALECT) {
            dialectSelect.toggle();
        } else if (position == ROW_KEY) {
            listener.onEditVoiceKey();
        }
    }

    private void addCard(@NonNull YoList list, float topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(topMarginDp);
        view.addView(list.getView(), params);
    }

    @NonNull
    private YoSelect createSelect(@NonNull YoSelect.OnSelectListener onSelect,
                                  @NonNull YoComponents components) {
        YoSelect select = YoSelect.create(context)
                .setControlSize(YoSelect.ControlSize.SMALL)
                .setBackgroundColor(theme.fill)
                .setMenuAlign(YoSelect.MenuAlignType.END)
                .setOnSelectListener(onSelect)
                .build();
        components.own(select::destroy);
        return select;
    }

    @NonNull
    private static List<String> labels(@NonNull List<String> ids) {
        return List.copyOf(ids);
    }

    @NonNull
    private static List<String> optionsForVoice(@NonNull AiSettingsUiState.Voice voice) {
        List<String> labels = new ArrayList<>();
        for (AiSettingsUiState.VoiceOption option : voice.options()) {
            labels.add(option.name());
        }
        return labels;
    }

}
