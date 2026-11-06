package com.ggec.earbuds.ui.ai.call;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.settings.AiSettingsActivity;
import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.wiring.AppContainer;

/**
 * 通话页宿主：只处理权限、生命周期和跨页面导航。
 *
 * <p>遵循严格 MVVM 架构：Activity 不持有业务逻辑与控制状态，
 * 所有状态由 {@link AiCallViewModel} 持有并通过 LiveData 发布；
 * 视图事件直接单向委托给 ViewModel 执行。</p>
 */
public final class AiCallActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String> microphone = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCall();
                } else {
                    showPermissionDenied();
                }
            });

    private AiCallViewModel viewModel;
    private AiCallScreen screen;
    private boolean askedForMicrophone;
    private boolean denied;
    private boolean finishing;

    @NonNull
    public static Intent intent(@NonNull Context context) {
        return new Intent(context, AiCallActivity.class);
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AiTheme theme = AiTheme.of(this);
        YoUi.edgeToEdge(this);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            boolean dark = theme.isDark();
            controller.setAppearanceLightStatusBars(!dark);
            controller.setAppearanceLightNavigationBars(!dark);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        viewModel = new ViewModelProvider(this, container().ai().factory())
                .get(AiCallViewModel.class);
        screen = new AiCallScreen(theme, new ScreenListener());
        setContentView(screen.view());

        viewModel.uiState().observe(this, state -> {
            if (!finishing && !denied && state != null) {
                screen.render(state);
            }
        });
        viewModel.meter().observe(this, screen::setLevel);
        viewModel.transcript().observe(this, screen::setTranscript);
        viewModel.setSpeakerphone(true);
    }

    @NonNull
    private AppContainer container() {
        AppContainer container = AppContainer.from(this);
        if (container == null) {
            throw new IllegalStateException("AppContainer is not installed");
        }
        return container;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!hasMicrophone()) {
            if (askedForMicrophone) {
                showPermissionDenied();
            } else {
                askedForMicrophone = true;
                microphone.launch(Manifest.permission.RECORD_AUDIO);
            }
            return;
        }
        startCall();
    }

    private void startCall() {
        denied = false;
        if (!viewModel.isStarted()) {
            viewModel.start();
        }
    }

    private void showPermissionDenied() {
        denied = true;
        viewModel.permissionDenied();
    }

    private void hangUp() {
        if (finishing) {
            return;
        }
        finishing = true;
        viewModel.hangUp();
        finish();
    }

    private boolean hasMicrophone() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private final class ScreenListener implements AiCallScreen.Listener {

        @Override
        public void onClose() {
            hangUp();
        }

        @Override
        public void onInterrupt() {
            viewModel.interrupt();
        }

        @Override
        public void onAction(@NonNull AiCallUiState.Action action) {
            if (action == AiCallUiState.Action.RETRY) {
                viewModel.hangUp();
                viewModel.start();
            } else if (action == AiCallUiState.Action.SETTINGS) {
                finishing = true;
                viewModel.hangUp();
                startActivity(AiSettingsActivity.intent(AiCallActivity.this));
                finish();
            }
        }

        @Override
        public void onMuted(boolean muted) {
            viewModel.setMuted(muted);
        }

        @Override
        public void onSpeaker(boolean speaker) {
            viewModel.setSpeakerphone(speaker);
        }

        @Override
        public void onToggleSubtitles() {
            viewModel.toggleCaptions();
        }

        @Override
        public void onHangUp() {
            hangUp();
        }
    }
}
