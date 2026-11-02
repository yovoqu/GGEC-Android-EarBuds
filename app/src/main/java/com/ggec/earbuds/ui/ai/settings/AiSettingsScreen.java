package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.controls.YoSegmentButton;
import com.yohu.youi.api.platform.YoSystemBar;

/** 设置页视图。只负责布局、展示 UiState、把用户动作交给 ViewModel。 */
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
    private final NestedScrollView scroll;
    private final LinearLayout root;
    private final YoSegmentButton modes;
    private final TextView modeHint;
    private final TextView loadingHint;
    private final TextView saveHint;
    private final AiTextModePanel textPanel;
    private final AiVoiceModePanel voicePanel;
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

        AiSettingsHeader header = new AiSettingsHeader(context, theme,
                R.string.ai_settings_title, listener::onClose);
        root.addView(header.view(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll = new NestedScrollView(context);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout column = new LinearLayout(context);
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

        modes = YoSegmentButton.create(context)
                .addSegment(SEGMENT_TEXT, context.getString(R.string.ai_settings_mode_text))
                .addSegment(SEGMENT_VOICE, context.getString(R.string.ai_settings_mode_voice))
                .setType(YoSegmentButton.SegmentType.SINGLE_CHOICE)
                .setSize(YoSegmentButton.Size.MEDIUM)
                .setLayoutMode(YoSegmentButton.LayoutMode.DISTRIBUTE)
                .setSelectedIndex(SEGMENT_TEXT)
                .setEnabled(false)
                .setOnSelectionChangeListener(index -> listener.onModeChanged(
                        index == SEGMENT_VOICE ? AiInteractionMode.VOICE : AiInteractionMode.TEXT))
                .build();
        components.own(modes::destroy);
        column.addView(modes.getView(), insetParams(AiTheme.SPACE_L));

        modeHint = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        modeHint.setVisibility(View.GONE);
        modeHint.setLineSpacing(theme.dpf(3), 1f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = theme.dp(AiTheme.SPACE_S);
        hintParams.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        hintParams.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        column.addView(modeHint, hintParams);

        saveHint = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        saveHint.setVisibility(View.GONE);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveParams.topMargin = theme.dp(AiTheme.SPACE_XS);
        saveParams.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        saveParams.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        column.addView(saveHint, saveParams);

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
        column.addView(textPanel.view(), matchWrap());
        textPanel.setVisible(false);

        voicePanel = new AiVoiceModePanel(context, theme, components, new AiVoiceModePanel.Listener() {
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
            public void onFetchRealtimeModels() {
                listener.onFetchRealtimeModels();
            }
        });
        column.addView(voicePanel.view(), matchWrap());
        voicePanel.setVisible(false);

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
        return textPanel.anchor();
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
        modes.setEnabled(true);
        modeHint.setVisibility(View.VISIBLE);
        boolean voice = state.mode() == AiInteractionMode.VOICE;
        int selected = voice ? SEGMENT_VOICE : SEGMENT_TEXT;
        if (modes.getSelectedIndex() != selected) {
            modes.setSelectedIndex(selected);
        }
        modeHint.setText(state.modeHint().resolve(context));
        textPanel.setVisible(!voice);
        voicePanel.setVisible(voice);
        textPanel.bind(state.identity(), state.style());
        voicePanel.bind(state.voice());
        bindPersistence(state.persistence());
    }

    private void bindPersistence(AiSettingsPersistenceStatus status) {
        if (status == AiSettingsPersistenceStatus.FAILED) {
            saveHint.setText(R.string.ai_settings_save_failed);
            saveHint.setTextColor(theme.alert);
            saveHint.setVisibility(View.VISIBLE);
        } else if (status == AiSettingsPersistenceStatus.SAVING) {
            saveHint.setText(R.string.ai_settings_saving);
            saveHint.setTextColor(theme.text3);
            saveHint.setVisibility(View.VISIBLE);
        } else {
            saveHint.setVisibility(View.GONE);
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
