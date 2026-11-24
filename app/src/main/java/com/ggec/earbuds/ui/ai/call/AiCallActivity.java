package com.ggec.earbuds.ui.ai.call;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.WindowManager;
import com.yohu.youi.api.feedback.YoToast;

import androidx.activity.OnBackPressedCallback;
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
 * <p>遵循严格 MVVM 架构：Activity 不持有业务逻辑与控制状态，也不决定「该不该渲染」——
 * 权限被拒是 {@link AiCallUiState.Phase#PERMISSION_DENIED} 这一帧状态，页面照常按状态渲染；
 * 所有状态由 {@link AiCallViewModel} 持有并通过 LiveData 发布，视图事件直接单向委托给 ViewModel。</p>
 */
public final class AiCallActivity extends AppCompatActivity {

    private AiCallViewModel viewModel;
    private AiCallScreen screen;
    /** 本实例是否已经弹过系统权限对话框：只用于避免 onStart 反复弹窗，不参与渲染判定。 */
    private boolean permissionRequested;
    private boolean finishing;

    private final ActivityResultLauncher<String> microphone = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCall();
                } else {
                    viewModel.permissionDenied();
                }
            });

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
            if (!finishing) {
                screen.render(state);
            }
        });
        viewModel.toasts().observe(this, messageRes -> {
            if (messageRes != null) {
                YoToast.show(this, getString(messageRes));
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                exitPage();
            }
        });
    }

    @NonNull
    private AppContainer container() {
        AppContainer container = AppContainer.from(this);
        if (container == null) {
            throw new IllegalStateException("AppContainer is not installed");
        }
        return container;
    }

    private void handleStartCall() {
        if (hasMicrophone()) {
            startCall();
            return;
        }
        permissionRequested = true;
        microphone.launch(Manifest.permission.RECORD_AUDIO);
    }

    private void startCall() {
        if (!viewModel.isStarted()) {
            viewModel.start();
        }
    }

    private void exitPage() {
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
            exitPage();
        }

        @Override
        public void onStartCall() {
            handleStartCall();
        }

        @Override
        public void onInterrupt() {
            viewModel.interrupt();
        }

        @Override
        public void onAction(@NonNull AiCallUiState.Action action) {
            if (action == AiCallUiState.Action.RETRY) {
                handleStartCall();
            } else if (action == AiCallUiState.Action.SETTINGS) {
                exitPage();
                startActivity(AiSettingsActivity.intent(AiCallActivity.this));
            }
        }

        @Override
        public void onMicMuted(boolean micMuted) {
            viewModel.setMicMuted(micMuted);
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
        public void onToggleDuplex() {
            viewModel.toggleDuplex();
        }

        @Override
        public void onHangUp() {
            viewModel.hangUp();
        }
    }
}
