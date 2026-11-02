package com.ggec.earbuds.ui.ai.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.wiring.AppContainer;
import com.yohu.youi.api.feedback.YoToast;

import java.util.List;

/** AI 设置页宿主：创建 ViewModel/View，转发生命周期与一次性事件。 */
public final class AiSettingsActivity extends AppCompatActivity {

    private AiSettingsViewModel viewModel;
    private YoComponents components;
    private AiSettingsScreen screen;
    private AiSettingsDialogs dialogs;

    @Nullable
    private AiSettingsUiState current;

    @NonNull
    public static Intent intent(@NonNull Context context) {
        return new Intent(context, AiSettingsActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YoUi.edgeToEdge(this);
        viewModel = new ViewModelProvider(this, container().ai().factory())
                .get(AiSettingsViewModel.class);
        components = new YoComponents();
        dialogs = new AiSettingsDialogs(this, components);
        screen = new AiSettingsScreen(this, AiTheme.of(this), components, new ScreenListener());
        setContentView(screen.view());

        viewModel.uiState().observe(this, state -> {
            if (state == null) {
                return;
            }
            current = state;
            screen.render(state);
        });
        viewModel.events().observe(this, this::showEvent);
        viewModel.start();
    }

    @NonNull
    private AppContainer container() {
        AppContainer container = AppContainer.from(this);
        if (container == null) {
            throw new IllegalStateException("AppContainer is not installed");
        }
        return container;
    }

    private void showEvent(@NonNull AiSettingsEvent event) {
        if (screen.rendered()) {
            YoToast.show(this, getString(event.message()));
        }
    }

    @Override
    protected void onDestroy() {
        components.close();
        super.onDestroy();
    }

    private final class ScreenListener implements AiSettingsScreen.Listener,
            AiSettingsDialogs.Listener {

        @Override
        public void onClose() {
            finish();
        }

        @Override
        public void onModeChanged(@NonNull AiInteractionMode mode) {
            viewModel.selectMode(mode);
        }

        @Override
        public void onOpenPlatform() {
            AiSettingsUiState state = current;
            if (state == null) {
                return;
            }
            dialogs.showPlatformMenu(screen.identityAnchor(), state.platforms(), this);
        }

        @Override
        public void onEditPrompt() {
            AiSettingsUiState state = current;
            if (state != null) {
                dialogs.editPrompt(state.style().systemPrompt(), this);
            }
        }

        @Override
        public void onSelectVoiceModel(@NonNull String modelId) {
            viewModel.selectVoiceModel(modelId);
        }

        @Override
        public void onSelectRealtimeModel(@NonNull String modelId) {
            viewModel.selectChatGptModel(modelId);
        }

        @Override
        public void onSelectDialect(@NonNull String dialectId) {
            viewModel.selectChatGptDialect(dialectId);
        }

        @Override
        public void onEditVoiceKey() {
            AiSettingsUiState state = current;
            if (state != null) {
                dialogs.editVoiceKey(state.voice(), this);
            }
        }

        @Override
        public void onEditVoiceUrl() {
            AiSettingsUiState state = current;
            if (state != null) {
                dialogs.editVoiceUrl(state.voice(), this);
            }
        }

        @Override
        public void onFetchRealtimeModels() {
            viewModel.fetchVoiceModels();
        }

        @Override
        public void onOpenPlatform(@NonNull String platformId) {
            startActivity(AiPlatformActivity.intent(AiSettingsActivity.this, platformId));
        }

        @Override
        public void onCreatePlatform(@NonNull String templateId) {
            startActivity(AiPlatformActivity.createIntent(AiSettingsActivity.this, templateId));
        }

        @Override
        public void onAddPlatform() {
            List<AiPlatformTemplate> templates = viewModel.templates().getValue();
            dialogs.showAddPlatform(templates == null ? List.of() : templates, this);
        }

        @Override
        public void onVoiceKey(@NonNull String key) {
            viewModel.saveVoiceApiKey(key);
        }

        @Override
        public void onVoiceBaseUrl(@NonNull String baseUrl) {
            viewModel.saveVoiceBaseUrl(baseUrl);
        }

        @Override
        public void onPrompt(@NonNull String prompt) {
            viewModel.saveSystemPrompt(prompt);
        }
    }
}
