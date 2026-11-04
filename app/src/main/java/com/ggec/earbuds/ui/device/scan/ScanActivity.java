package com.ggec.earbuds.ui.device.scan;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.wiring.AppContainer;

/** 扫描连接页宿主。 */
public final class ScanActivity extends AppCompatActivity {

    private ScanViewModel viewModel;
    private ScanScreen screen;

    @NonNull
    public static Intent intent(@NonNull Context context) {
        return new Intent(context, ScanActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YoUi.edgeToEdge(this);
        viewModel = new ViewModelProvider(this, container().device().scanFactory())
                .get(ScanViewModel.class);
        screen = new ScanScreen(this, new ScanScreen.Listener() {
            @Override
            public void onBack() {
                finish();
            }

            @Override
            public void onScan() {
                ScanUiState state = viewModel.uiState().getValue();
                if (state != null && state.scanning()) {
                    viewModel.stopScan();
                } else {
                    viewModel.startScan();
                }
            }

            @Override
            public void onConnect(@NonNull ScanItem item) {
                viewModel.connect(item);
                finish();
            }
        });
        setContentView(screen.view());
        viewModel.uiState().observe(this, state -> {
            if (state != null) {
                screen.render(state);
            }
        });
        viewModel.startScan();
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
