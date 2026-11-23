package com.ggec.earbuds.ui.device.wear;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.ui.device.DeviceFailureText;
import com.ggec.earbuds.wiring.AppContainer;
import com.yohu.youi.api.feedback.YoToast;

/** 佩戴设置独立页宿主。 */
public final class WearSettingsActivity extends AppCompatActivity {

    private WearSettingsViewModel viewModel;
    private WearSettingsScreen screen;

    @NonNull
    public static Intent intent(@NonNull Context context) {
        return new Intent(context, WearSettingsActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YoUi.edgeToEdge(this);
        viewModel = new ViewModelProvider(this, container().device().wearFactory())
                .get(WearSettingsViewModel.class);
        screen = new WearSettingsScreen(this, new ScreenListener());
        setContentView(screen.view());
        viewModel.state().observe(this, state -> {
            if (state != null) {
                screen.render(state);
            }
        });
        viewModel.failures().observe(this, failure -> {
            if (failure != null) {
                YoToast.showWarning(this, getString(DeviceFailureText.message(failure)));
            }
        });
        viewModel.start();
    }

    @Override
    protected void onDestroy() {
        screen.destroy();
        super.onDestroy();
    }

    @NonNull
    private AppContainer container() {
        AppContainer container = AppContainer.from(this);
        if (container == null) {
            throw new IllegalStateException("AppContainer is not installed");
        }
        return container;
    }

    private final class ScreenListener implements WearSettingsScreen.Listener {
        @Override
        public void onBack() {
            finish();
        }

        @Override
        public void onToggle(boolean enabled) {
            viewModel.toggle(enabled);
        }
    }
}
