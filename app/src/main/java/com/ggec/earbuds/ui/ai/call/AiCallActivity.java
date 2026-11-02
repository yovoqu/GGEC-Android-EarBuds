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
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.settings.AiSettingsActivity;
import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.wiring.AppContainer;

/**
 * 通话页宿主：只处理权限、生命周期和跨页面导航。
 *
 * <p>快照到页面表现的取舍在 ViewModel/Mapper；页头、舞台、控制排各自是独立 View。
 * Activity 不持有静音、失败出口等业务状态。</p>
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
        public void onHangUp() {
            hangUp();
        }
    }
}
