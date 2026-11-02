package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.wiring.AppContainer;
import com.yohu.youi.api.feedback.YoToast;

/** 平台详情页宿主：只负责 ViewModel/View 生命周期、导航与一次性事件。 */
public final class AiPlatformActivity extends AppCompatActivity {

    private static final String EXTRA_PLATFORM_ID = "platformId";
    private static final String EXTRA_TEMPLATE_ID = "templateId";

    private AiPlatformViewModel viewModel;
    private YoComponents components;
    private AiPlatformScreen screen;
    private AiPlatformDialogs dialogs;

    @NonNull
    public static Intent intent(@NonNull Context context, @NonNull String platformId) {
        return new Intent(context, AiPlatformActivity.class)
                .putExtra(EXTRA_PLATFORM_ID, platformId);
    }

    @NonNull
    public static Intent createIntent(@NonNull Context context, @NonNull String templateId) {
        return new Intent(context, AiPlatformActivity.class)
                .putExtra(EXTRA_TEMPLATE_ID, templateId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this, container().ai().factory())
                .get(AiPlatformViewModel.class);
        components = new YoComponents();
        dialogs = new AiPlatformDialogs(this, components);
        screen = new AiPlatformScreen(this, AiTheme.of(this), components, new ScreenListener());
        setContentView(screen.view());

        String platformId = getIntent().getStringExtra(EXTRA_PLATFORM_ID);
        String templateId = getIntent().getStringExtra(EXTRA_TEMPLATE_ID);
        if (platformId != null) {
            viewModel.open(platformId);
        } else if (templateId != null) {
            viewModel.create(templateId);
        } else {
            finish();
            return;
        }
        viewModel.state().observe(this, screen::render);
        viewModel.events().observe(this, this::showEvent);
    }

    @NonNull
    private AppContainer container() {
        AppContainer container = AppContainer.from(this);
        if (container == null) {
            throw new IllegalStateException("AppContainer is not installed");
        }
        return container;
    }

    private void showEvent(@NonNull AiPlatformEvent event) {
        YoToast.show(this, getString(event.message()));
        if (event.closed()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        components.close();
        super.onDestroy();
    }

    private final class ScreenListener implements AiPlatformScreen.Listener,
            AiPlatformDialogs.Listener {

        @Override
        public void onClose() {
            finish();
        }

        @Override
        public void onTestConnection() {
            viewModel.testConnection();
        }

        @Override
        public void onToggleAdvanced() {
            viewModel.toggleAdvanced();
        }

        @Override
        public void onRunOrStopSpeed() {
            viewModel.toggleAllSpeed();
        }

        @Override
        public void onSearch(@NonNull String query) {
            viewModel.setSearchQuery(query);
        }

        @Override
        public void onSelectModel(@NonNull String modelId) {
            viewModel.selectModel(modelId);
        }

        @Override
        public void onModelMenu(@NonNull View anchor, @NonNull String modelId) {
            dialogs.showModelMenu(anchor, modelId, this);
        }

        @Override
        public void onDeletePlatform() {
            dialogs.confirmDelete(this);
        }

        @Override
        public void onFetchModels() {
            viewModel.fetchModels();
        }

        @Override
        public void onAddManualModel() {
            dialogs.showManualAdd(this);
        }

        @Override
        public void onAddManualModel(@NonNull String modelId) {
            viewModel.addManualModel(modelId);
        }

        @Override
        public void onTestModel(@NonNull String modelId) {
            viewModel.testModel(modelId);
        }

        @Override
        public void onDeleteModel(@NonNull String modelId) {
            viewModel.deleteModel(modelId);
        }
    }
}
