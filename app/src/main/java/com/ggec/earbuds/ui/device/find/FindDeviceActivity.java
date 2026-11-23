package com.ggec.earbuds.ui.device.find;

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

/** 查找设备独立页宿主：转发左右耳响铃 / 停止响铃。 */
public final class FindDeviceActivity extends AppCompatActivity {

    private FindDeviceViewModel viewModel;
    private FindDeviceScreen screen;

    @NonNull
    public static Intent intent(@NonNull Context context) {
        return new Intent(context, FindDeviceActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YoUi.edgeToEdge(this);
        viewModel = new ViewModelProvider(this, container().device().findFactory())
                .get(FindDeviceViewModel.class);
        screen = new FindDeviceScreen(this, new ScreenListener());
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

    @NonNull
    private AppContainer container() {
        AppContainer container = AppContainer.from(this);
        if (container == null) {
            throw new IllegalStateException("AppContainer is not installed");
        }
        return container;
    }

    private final class ScreenListener implements FindDeviceScreen.Listener {
        @Override
        public void onBack() {
            finish();
        }

        @Override
        public void onRingLeft() {
            viewModel.ringLeft();
        }

        @Override
        public void onRingRight() {
            viewModel.ringRight();
        }

        @Override
        public void onStop() {
            viewModel.stop();
        }
    }
}
