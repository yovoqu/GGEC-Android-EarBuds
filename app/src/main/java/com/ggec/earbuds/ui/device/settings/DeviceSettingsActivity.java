package com.ggec.earbuds.ui.device.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.ui.device.DevicePage;
import com.ggec.earbuds.wiring.AppContainer;

/** 二级设置页宿主：MUSIC / CONTROL / FUNCTION / DUAL / RECORD / ABOUT 共用。 */
public final class DeviceSettingsActivity extends AppCompatActivity {

    private static final String EXTRA_SECTION = "section";

    private DeviceSettingsViewModel viewModel;
    private DeviceSettingsScreen screen;

    @NonNull
    public static Intent intent(@NonNull Context context, @NonNull DevicePage page) {
        Intent intent = new Intent(context, DeviceSettingsActivity.class);
        intent.putExtra(EXTRA_SECTION, page.name());
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YoUi.edgeToEdge(this);
        DevicePage page = page();
        viewModel = new ViewModelProvider(this, container().device().settingsFactory(page))
                .get(DeviceSettingsViewModel.class);
        screen = new DeviceSettingsScreen(this, viewModel, this::finish);
        setContentView(screen.view());
        viewModel.uiState().observe(this, state -> {
            if (state != null) {
                screen.render(state);
            }
        });
        viewModel.start();
    }

    @NonNull
    private DevicePage page() {
        String raw = getIntent().getStringExtra(EXTRA_SECTION);
        if (raw == null) {
            return DevicePage.ABOUT;
        }
        return DevicePage.valueOf(raw);
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
}
