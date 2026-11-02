package com.ggec.earbuds.ui.ai.settings;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.platform.YoSystemBar;

/** 平台详情页视图：固定区块、状态绑定、动作转发。 */
public final class AiPlatformScreen {

    public interface Listener {
        void onClose();
        void onTestConnection();
        void onToggleAdvanced();
        void onRunOrStopSpeed();
        void onFetchModels();
        void onAddManualModel();
        void onSearch(@NonNull String query);
        void onSelectModel(@NonNull String modelId);
        void onModelMenu(@NonNull View anchor, @NonNull String modelId);
        void onDeletePlatform();
    }

    private final AiTheme theme;
    private final YoComponents components;
    private final Listener listener;
    private final FrameLayout root;
    private final PlatformConnectionSection connectionSection;
    private final PlatformModelsSection modelsSection;
    private final PlatformAdvancedSection advancedSection;
    private final PlatformDangerSection dangerSection;
    private final PlatformHeader header;

    public AiPlatformScreen(@NonNull AppCompatActivity activity, @NonNull AiTheme theme,
                            @NonNull YoComponents components, @NonNull Listener listener) {
        this.theme = theme;
        this.components = components;
        this.listener = listener;

        root = new FrameLayout(activity);
        root.setBackgroundColor(theme.page);
        LinearLayout outer = new LinearLayout(activity);
        outer.setOrientation(LinearLayout.VERTICAL);
        root.addView(outer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        header = new PlatformHeader(activity, theme, listener::onClose);
        outer.addView(header.view(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(AiTheme.HEADER_HEIGHT)));

        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, theme.dp(AiTheme.SPACE_S), 0, theme.dp(AiTheme.SPACE_XL));
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = YoSystemBar.systemBarsAndCutout(insets);
            view.setPadding(0, bars.top, 0, 0);
            scroll.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        connectionSection = new PlatformConnectionSection(activity, theme, components,
                new PlatformConnectionSection.Listener() {
                    @Override
                    public void onTestConnection() {
                        listener.onTestConnection();
                    }
                });
        modelsSection = new PlatformModelsSection(activity, theme, components,
                new PlatformModelsSection.Listener() {
                    @Override
                    public void onRunOrStopSpeed() {
                        listener.onRunOrStopSpeed();
                    }

                    @Override
                    public void onFetchModels() {
                        listener.onFetchModels();
                    }

                    @Override
                    public void onAddManualModel() {
                        listener.onAddManualModel();
                    }

                    @Override
                    public void onSearch(@NonNull String query) {
                        listener.onSearch(query);
                    }

                    @Override
                    public void onSelectModel(@NonNull String modelId) {
                        listener.onSelectModel(modelId);
                    }

                    @Override
                    public void onModelMenu(@NonNull View anchor, @NonNull String modelId) {
                        listener.onModelMenu(anchor, modelId);
                    }
                });
        advancedSection = new PlatformAdvancedSection(activity, theme, components,
                listener::onToggleAdvanced);
        dangerSection = new PlatformDangerSection(activity, theme, listener::onDeletePlatform);
        column.addView(connectionSection.view(), matchWrap());
        column.addView(modelsSection.view(), matchWrap());
        column.addView(advancedSection.view(), matchWrap());
        column.addView(dangerSection.view(), matchWrap());
    }

    @NonNull
    public View view() {
        return root;
    }

    public void render(@NonNull AiPlatformUiState state) {
        if (!state.loaded()) {
            return;
        }
        header.bind(state.title(), state.badge());
        connectionSection.bind(state.connection());
        modelsSection.bind(state.models());
        advancedSection.bind(state.advanced(), state.advancedExpanded());
        dangerSection.setVisible(state.canDelete());
    }

    @NonNull
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

}
