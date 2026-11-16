package com.ggec.earbuds.ui.ai.settings.voice;

import android.content.Context;
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
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.feedback.YoLoadingProgress;

import java.util.List;

/**
 * 语音模型卡：展示当前服务商的实时语音模型列表或空状态。
 *
 * <p>架构设计原则：
 * <ul>
 *   <li>状态分支分离：空状态展示专属自适应卡片（图标 + 居中提示 + 水波纹引导点击），
 *       而非将空态包装为带右箭头的伪列表项；</li>
 *   <li>高度自适应：移除死写 240dp 固定高度，列表与空态均采用 {@code WRAP_CONTENT}，
 *       与宿主 {@code NestedScrollView} 平滑融合无嵌套滚动冲突；</li>
 *   <li>加载状态协同：拉取中时卡片与标题栏同步切换为 Loading 状态。</li>
 * </ul>
 * </p>
 */
final class VoiceModelCard {

    interface Listener {
        void onFetch();
        void onSelectModel(@NonNull String modelId);
    }

    private static final float ICON_CONTAINER_SIZE_DP = 36f;
    private static final float EMPTY_ICON_SIZE_DP = 24f;

    private final Context context;
    private final AiTheme theme;
    private final Listener listener;
    private final LinearLayout view;
    private final TextView title;
    private final TextView action;
    private final LinearLayout emptyCard;
    private final ImageView emptyIcon;
    private final YoLoadingProgress loadingProgress;
    private final TextView emptyTitle;
    private final TextView emptyHint;
    private final YoList list;
    private boolean updatingSelection;
    @Nullable
    private AiSettingsUiState.Voice current;
    @Nullable
    private String signature;

    VoiceModelCard(@NonNull Context context, @NonNull AiTheme theme,
                   @NonNull YoComponents components,
                   @NonNull Listener listener) {
        this.context = context;
        this.theme = theme;
        this.listener = listener;
        view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);

        title = VoiceSectionHeader.title(theme);
        action = VoiceSectionHeader.action(theme);
        view.addView(VoiceSectionHeader.withAction(theme, context, title, action, () -> {
            AiSettingsUiState.Voice voice = current;
            if (voice != null && !voice.fetching()) {
                listener.onFetch();
            }
        }), VoiceCard.matchWrap());

        // 专属空状态卡片：自适应高、居中排版、带轻度点击水波纹
        emptyCard = new LinearLayout(context);
        emptyCard.setOrientation(LinearLayout.VERTICAL);
        emptyCard.setGravity(Gravity.CENTER);
        emptyCard.setPadding(theme.dp(16f), theme.dp(24f), theme.dp(16f), theme.dp(24f));
        emptyCard.setBackground(theme.pressable(
                theme.rounded(theme.card, AiTheme.RADIUS_SHEET), AiTheme.RADIUS_SHEET));
        emptyCard.setClipToOutline(true);
        emptyCard.setOnClickListener(v -> {
            AiSettingsUiState.Voice voice = current;
            if (voice != null && !voice.fetching()) {
                listener.onFetch();
            }
        });

        FrameLayout iconContainer = new FrameLayout(context);
        emptyIcon = theme.icon(R.drawable.ic_ai_refresh, theme.text3);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                theme.dp(EMPTY_ICON_SIZE_DP), theme.dp(EMPTY_ICON_SIZE_DP), Gravity.CENTER);
        iconContainer.addView(emptyIcon, iconParams);

        loadingProgress = YoLoadingProgress.create(context)
                .setSize(YoLoadingProgress.Size.SMALL)
                .build();
        components.own(loadingProgress::destroy);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        iconContainer.addView(loadingProgress.getView(), loadingParams);

        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                theme.dp(ICON_CONTAINER_SIZE_DP), theme.dp(ICON_CONTAINER_SIZE_DP));
        emptyCard.addView(iconContainer, containerParams);

        emptyTitle = theme.text(AiTheme.TEXT_BODY, theme.text2, AiTheme.Weight.MEDIUM);
        emptyTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = theme.dp(AiTheme.SPACE_M);
        emptyCard.addView(emptyTitle, titleParams);

        emptyHint = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        emptyHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = theme.dp(AiTheme.SPACE_XS);
        emptyCard.addView(emptyHint, hintParams);

        LinearLayout.LayoutParams emptyCardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        emptyCardParams.leftMargin = theme.dp(AiTheme.LIST_EDGE);
        emptyCardParams.rightMargin = theme.dp(AiTheme.LIST_EDGE);
        view.addView(emptyCard, emptyCardParams);

        // 真实模型单选列表：高度 WRAP_CONTENT，由父级 NestedScrollView 统一滚动
        list = YoList.create(context)
                .setSelectable(true)
                .setSelectionIndicator(YoList.SelectionIndicator.RADIO)
                .build();
        components.own(list::destroy);

        list.setOnSelectionChangeListener((oldId, newId) -> {
            if (updatingSelection) {
                return;
            }
            AiSettingsUiState.Voice voice = current;
            if (voice == null) {
                return;
            }
            int index = list.indexOfId(newId);
            if (index >= 0 && index < voice.models().size()) {
                listener.onSelectModel(voice.models().get(index).id());
            }
        });

        view.addView(list.getView(), VoiceCard.matchWrap());
    }

    @NonNull
    View view() {
        return view;
    }

    void setVisible(boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    void bind(@NonNull AiSettingsUiState.Voice voice) {
        current = voice;
        action.setEnabled(!voice.fetching());
        if (voice.fetching()) {
            title.setText(R.string.ai_settings_voice_model_section);
            action.setText(R.string.ai_settings_voice_models_loading);
        } else {
            title.setText(context.getString(R.string.ai_settings_voice_model_section_count,
                    voice.models().size()));
            action.setText(voice.models().isEmpty()
                    ? R.string.ai_settings_voice_fetch
                    : R.string.ai_settings_voice_fetch_retry);
        }

        List<AiSettingsUiState.VoiceModelOption> models = voice.models();
        boolean empty = models.isEmpty();
        boolean fetching = voice.fetching();

        if (empty) {
            list.getView().setVisibility(View.GONE);
            emptyCard.setVisibility(View.VISIBLE);

            emptyIcon.setVisibility(fetching ? View.GONE : View.VISIBLE);
            loadingProgress.getView().setVisibility(fetching ? View.VISIBLE : View.GONE);
            emptyTitle.setText(fetching
                    ? R.string.ai_settings_voice_models_fetching_title
                    : R.string.ai_settings_voice_models_empty_title);
            emptyHint.setText(fetching
                    ? R.string.ai_settings_voice_models_fetching_hint
                    : R.string.ai_settings_voice_models_empty_hint);
            emptyCard.setClickable(!fetching);

            signature = null;
            list.clear();
        } else {
            emptyCard.setVisibility(View.GONE);
            list.getView().setVisibility(View.VISIBLE);

            String modelsSig = rowsSignature(models);
            if (!modelsSig.equals(signature)) {
                signature = modelsSig;
                list.clear();
                list.setSelectable(true);
                list.setSelectionIndicator(YoList.SelectionIndicator.RADIO);
                for (AiSettingsUiState.VoiceModelOption model : models) {
                    list.addItem(ListTextSpec.of(model.id()));
                }
            }
            applySelection(voice);
        }
    }

    private void applySelection(@NonNull AiSettingsUiState.Voice voice) {
        List<AiSettingsUiState.VoiceModelOption> models = voice.models();
        if (models.isEmpty()) {
            return;
        }
        int selectedIndex = -1;
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).id().equals(voice.realtimeModelId())) {
                selectedIndex = i;
                break;
            }
        }
        updatingSelection = true;
        try {
            if (selectedIndex >= 0 && selectedIndex < list.getItemCount()) {
                long stableId = list.getStableIdAt(selectedIndex);
                if (list.getSelectedStableId() != stableId) {
                    list.setSelectedStableId(stableId);
                }
            } else {
                list.clearSelection();
            }
        } finally {
            updatingSelection = false;
        }
    }

    @NonNull
    private static String rowsSignature(@NonNull List<AiSettingsUiState.VoiceModelOption> models) {
        StringBuilder result = new StringBuilder();
        for (AiSettingsUiState.VoiceModelOption model : models) {
            result.append(model.id()).append('\u0000');
        }
        return result.toString();
    }
}
