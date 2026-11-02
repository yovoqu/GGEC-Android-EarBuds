package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.settings.voice.VoiceSettingsView;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.controls.YoSegmentButton;
import com.yohu.youi.api.platform.YoSystemBar;

/**
 * 设置页视图。只负责布局、展示 UiState、把用户动作交给 ViewModel。
 *
 * <p>进入页面时只构建轻量的骨架（头部、模式分段、加载提示）；两个模式面板（含
 * {@code YoSelect} 下拉、{@code YoList} 列表等较重的 YoUI 组件）在数据到达、首次需要时才构建，
 * 这样 Activity 进场的首帧是轻的，跨窗口跳转不再因为同步装配整棵树而卡顿。面板只建一次。</p>
 */
public final class AiSettingsScreen {

    private static final int SEGMENT_TEXT = 0;
    private static final int SEGMENT_VOICE = 1;

    public interface Listener {
        void onClose();
        void onModeChanged(@NonNull AiInteractionMode mode);
        void onOpenPlatform();
        void onEditPrompt();
        void onSelectVoiceModel(@NonNull String modelId);
        void onSelectRealtimeModel(@NonNull String modelId);
        void onSelectDialect(@NonNull String dialectId);
        void onEditVoiceKey();
        void onEditVoiceUrl();
        void onFetchRealtimeModels();
    }

    private final Context context;
    private final AiTheme theme;
    private final YoComponents components;
    private final Listener listener;
    private final AiSettingsHeader header;
    private final NestedScrollView scroll;
    private final LinearLayout root;
    private final LinearLayout column;
    /**
     * 两个模式面板轮流占用这个槽：非当前模式的面板置 GONE（不参与测量），
     * 这样槽高永远等于"当前面板"的高度，不会因为另一块被测量而把页面撑高或跳一下。
     */
    private final FrameLayout contentHost;
    /**
     * 分段按钮只在「已加载的真实模式」确定后构建一次，并以该模式作为初始选择。分段未设初始
     * 选择时会默认选第一项；先建后改会让首帧先画默认项再跳到真实项（横向闪动），所以把「构建」
     * 推迟到状态已知，用真实模式做 opening selection，首帧即正确。
     */
    @Nullable private YoSegmentButton modes;
    private final TextView modeHint;
    private final TextView loadingHint;
    @Nullable private AiTextModePanel textPanel;
    @Nullable private VoiceSettingsView voicePanel;
    private boolean rendered;

    public AiSettingsScreen(@NonNull AppCompatActivity context, @NonNull AiTheme theme,
                            @NonNull YoComponents components, @NonNull Listener listener) {
        this.context = context;
        this.theme = theme;
        this.components = components;
        this.listener = listener;

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.page);

        header = new AiSettingsHeader(context, theme,
                R.string.ai_settings_title, listener::onClose);
        root.addView(header.view(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll = new NestedScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = YoSystemBar.systemBarsAndCutout(insets);
            view.setPadding(0, bars.top, 0, 0);
            scroll.setPadding(0, 0, 0, bars.bottom + theme.dp(AiTheme.SPACE_XL));
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        // 分段按钮不在这里建：持久化模式要等异步加载才知道，见 ensureModeSegment()。

        modeHint = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        modeHint.setVisibility(View.GONE);
        modeHint.setLineSpacing(theme.dpf(3), 1f);
        modeHint.setMaxLines(2);
        modeHint.setMinLines(2);
        modeHint.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = theme.dp(AiTheme.SPACE_S);
        hintParams.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        hintParams.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        column.addView(modeHint, hintParams);

        contentHost = new FrameLayout(context);
        column.addView(contentHost, matchWrap());

        loadingHint = theme.text(AiTheme.TEXT_SUBHEAD, theme.text3);
        loadingHint.setText(R.string.ai_settings_loading);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        loadingParams.topMargin = theme.dp(AiTheme.SPACE_XL);
        loadingParams.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        column.addView(loadingHint, loadingParams);
    }

    @NonNull
    public View view() {
        return root;
    }

    @NonNull
    public View identityAnchor() {
        return textPanel().anchor();
    }

    public boolean rendered() {
        return rendered;
    }

    public void render(@NonNull AiSettingsUiState state) {
        if (state.loadFailed()) {
            loadingHint.setText(R.string.ai_settings_load_failed);
            loadingHint.setTextColor(theme.alert);
            loadingHint.setVisibility(View.VISIBLE);
            return;
        }
        if (!state.loaded()) {
            return;
        }
        rendered = true;
        loadingHint.setVisibility(View.GONE);
        loadingHint.setTextColor(theme.text3);
        boolean voice = state.mode() == AiInteractionMode.VOICE;
        int selected = voice ? SEGMENT_VOICE : SEGMENT_TEXT;
        ensureModeSegment(selected);
        CharSequence nextHint = state.modeHint().resolve(context);
        if (modeHint.getVisibility() != View.VISIBLE) {
            modeHint.setVisibility(View.VISIBLE);
        }
        if (!nextHint.toString().contentEquals(modeHint.getText())) {
            modeHint.setText(nextHint);
        }
        // 两个模式面板都在数据到达时建一次，之后切换只做显隐；非当前面板置 GONE、不参与测量，
        // 槽高永远等于当前面板，切换只换来一次正确的高度，不会反复被另一块撑高。
        AiTextModePanel text = textPanel();
        VoiceSettingsView voiceContent = voicePanel();
        text.setVisible(!voice);
        voiceContent.setVisible(voice);
        if (voice) {
            voiceContent.bind(state.voice());
        } else {
            text.bind(state.identity(), state.style());
        }
        bindPersistence(state.persistence());
    }

    /**
     * 按已加载的真实模式构建分段（仅一次）：把真实模式作为 opening selection，首帧即落在正确项，
     * 不会先画默认项再跳过去。
     */
    private void ensureModeSegment(int selectedIndex) {
        if (modes == null) {
            modes = YoSegmentButton.create(context)
                    .addSegment(SEGMENT_TEXT, context.getString(R.string.ai_settings_mode_text))
                    .addSegment(SEGMENT_VOICE, context.getString(R.string.ai_settings_mode_voice))
                    .setType(YoSegmentButton.SegmentType.SINGLE_CHOICE)
                    .setSize(YoSegmentButton.Size.MEDIUM)
                    .setLayoutMode(YoSegmentButton.LayoutMode.DISTRIBUTE)
                    .setSelectedIndex(selectedIndex)
                    .setOnSelectionChangeListener(index -> listener.onModeChanged(
                            index == SEGMENT_VOICE ? AiInteractionMode.VOICE : AiInteractionMode.TEXT))
                    .build();
            components.own(modes::destroy);
            column.addView(modes.getView(), 0, insetParams(AiTheme.SPACE_L));
            return;
        }
        if (modes.getSelectedIndex() != selectedIndex) {
            modes.setSelectedIndex(selectedIndex);
        }
    }

    /** 文本模式面板，首次需要时才构建（含列表/下拉等较重组件）。 */
    @NonNull
    private AiTextModePanel textPanel() {
        if (textPanel == null) {
            textPanel = new AiTextModePanel(context, theme, components, new AiTextModePanel.Listener() {
                @Override
                public void onOpenPlatform() {
                    listener.onOpenPlatform();
                }

                @Override
                public void onEditPrompt() {
                    listener.onEditPrompt();
                }
            });
            mountPanel(textPanel.view());
        }
        return textPanel;
    }

    /** 语音模式面板，首次需要时才构建。 */
    @NonNull
    private VoiceSettingsView voicePanel() {
        if (voicePanel == null) {
            voicePanel = new VoiceSettingsView(context, theme, components,
                    new VoiceSettingsView.Listener() {
                @Override
                public void onSelectVoiceModel(@NonNull String modelId) {
                    listener.onSelectVoiceModel(modelId);
                }

                @Override
                public void onSelectRealtimeModel(@NonNull String modelId) {
                    listener.onSelectRealtimeModel(modelId);
                }

                @Override
                public void onSelectDialect(@NonNull String dialectId) {
                    listener.onSelectDialect(dialectId);
                }

                @Override
                public void onEditVoiceKey() {
                    listener.onEditVoiceKey();
                }

                @Override
                public void onEditVoiceUrl() {
                    listener.onEditVoiceUrl();
                }

                @Override
                public void onFetchVoiceModels() {
                    listener.onFetchRealtimeModels();
                }
            });
            mountPanel(voicePanel.view());
        }
        return voicePanel;
    }

    /** 把面板挂进叠加槽；只有当前模式那块参与测量，另一块 GONE。 */
    private void mountPanel(@NonNull View panelView) {
        panelView.setVisibility(View.GONE);
        contentHost.addView(panelView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /**
     * 保存状态只写在页头右侧的固定状态位，不占正文的竖向空间。每一次改值都会先发 SAVING、
     * 写盘后再发 SAVED；把这两条放进正文流里，就等于每次改值都把页面往下推一下再弹回来。
     */
    private void bindPersistence(AiSettingsPersistenceStatus status) {
        if (status == AiSettingsPersistenceStatus.FAILED) {
            header.setStatus(context.getString(R.string.ai_settings_save_failed), theme.alert);
        } else if (status == AiSettingsPersistenceStatus.SAVING) {
            header.setStatus(context.getString(R.string.ai_settings_saving), theme.text3);
        } else {
            header.setStatus(null, theme.text3);
        }
    }

    @NonNull
    private LinearLayout.LayoutParams insetParams(float topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(topMarginDp);
        params.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        params.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        return params;
    }

    @NonNull
    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
