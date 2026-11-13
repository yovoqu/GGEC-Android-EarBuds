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
        void onEditVoiceKey();
        void onEditVoiceUrl();
        void onFetchRealtimeModels();
        void onSelectDefaultDuplex(@NonNull String duplexId);
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
    private final ModePanelSwitcher contentHost;
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
    @Nullable private AiSettingsUiState lastState;
    private boolean prewarmed;
    /** 预热的延时装配任务：页面销毁时必须撤销，不能在已分离的视图树上继续建控件。 */
    private final Runnable prewarmTask = this::prewarmInactivePanel;
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

        contentHost = new ModePanelSwitcher(context);
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

    /** 撤销延时装配；组件释放由宿主统一的 {@link YoComponents} 负责。 */
    void destroy() {
        contentHost.removeCallbacks(prewarmTask);
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
        // 只构建/绑定当前模式的面板：另一块不创建、不测量。切换模式才按需建一次，
        // 避免"进语音页却顺手把文本页的 YoList 全装配一遍"这种首帧卡顿。
        if (voice) {
            VoiceSettingsView voiceContent = voicePanel();
            contentHost.setActiveView(voiceContent.view());
            voiceContent.bind(state.voice());
        } else {
            AiTextModePanel text = textPanel();
            contentHost.setActiveView(text.view());
            text.bind(state.identity(), state.style());
        }
        lastState = state;
        // 首次渲染完成后再预热另一块面板：把 YoList/控件树的装配挪出用户的第一次点击路径。
        if (!prewarmed) {
            prewarmed = true;
            contentHost.postDelayed(prewarmTask, 120L);
        }
    }

    /**
     * 空闲时把非当前模式的面板预先建好并绑上当前状态，并保持 GONE。
     * 这样"进入页面后的第一次切换"只剩一次可见性切换，不再在点击回调里同步装配整棵控件树。
     */
    private void prewarmInactivePanel() {
        AiSettingsUiState state = lastState;
        if (state == null || !state.loaded()) {
            prewarmed = false;
            return;
        }
        if (state.mode() == AiInteractionMode.VOICE) {
            if (textPanel == null) {
                textPanel().bind(state.identity(), state.style());
            }
            if (textPanel != null) {
                premeasure(textPanel.view());
            }
        } else {
            if (voicePanel == null) {
                voicePanel().bind(state.voice());
            }
            if (voicePanel != null) {
                premeasure(voicePanel.view());
            }
        }
    }

    /**
     * 隐藏面板也先按真实宽度量一次并布局：列表/控件树内部的一次性布局在空闲时完成，
     * 切换时只剩可见性翻转，避免把首次整树布局压在点击帧上。
     */
    private void premeasure(@NonNull View panel) {
        int width = contentHost.getWidth() > 0 ? contentHost.getWidth() : root.getWidth();
        if (width <= 0) {
            return;
        }
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        panel.layout(0, 0, width, panel.getMeasuredHeight());
    }

    /** 持久化状态独立于 UiState：只有失败/恢复才需要页头显示，不再触发整页重绑。 */
    public void showSaveStatus(@NonNull AiSettingsPersistenceStatus status) {
        if (status == AiSettingsPersistenceStatus.FAILED) {
            header.setStatus(context.getString(R.string.ai_settings_save_failed), theme.alert);
        } else {
            header.setStatus(null, theme.text3);
        }
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
        }
        // 之后不再由状态回写分段：模式变化的唯一来源就是用户点分段本身，
        // 回写只会重复触发一次选中动画，让"首次切换"看起来卡一下。
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

                @Override
                public void onSelectDefaultDuplex(@NonNull String duplexId) {
                    listener.onSelectDefaultDuplex(duplexId);
                }
            });
            mountPanel(voicePanel.view());
        }
        return voicePanel;
    }

    /** 把面板挂进切换宿主：两块都常驻测量，切换只改当前项。 */
    private void mountPanel(@NonNull View panelView) {
        contentHost.addPanel(panelView);
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
