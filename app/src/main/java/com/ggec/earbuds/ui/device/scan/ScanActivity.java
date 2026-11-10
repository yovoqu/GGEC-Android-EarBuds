package com.ggec.earbuds.ui.device.scan;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.wiring.AppContainer;
import com.yohu.youi.api.feedback.YoToast;
import com.ggec.earbuds.provider.lib_common.PermissionProvider;

import java.util.List;

/** 扫描连接页宿主：负责蓝牙扫描权限申请、扫描结果渲染与连接跳转。 */
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
                    startScanWithPermission();
                }
            }

            @Override
            public void onSelectFilter(@NonNull String modelId) {
                viewModel.selectFilter(modelId);
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
        viewModel.events().observe(this, messageRes -> {
            if (messageRes != null && messageRes != 0) {
                YoToast.showWarning(this, getString(messageRes));
            }
        });
        startScanWithPermission();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionProvider.Result result = PermissionProvider.onRequestResult(
                this, requestCode, permissions, grantResults);
        if (!result.handled()) {
            return;
        }
        if (result.allGranted()) {
            viewModel.startScan();
        } else if (result.permanentlyDenied()) {
            YoToast.showWarning(this, getString(R.string.device_scan_error_permission_settings));
        } else {
            YoToast.showWarning(this, getString(R.string.device_scan_error_permission));
        }
    }

    @Override
    protected void onDestroy() {
        screen.destroy();
        super.onDestroy();
    }

    private void startScanWithPermission() {
        if (PermissionProvider.requestBluetoothIfNeeded(this)) {
            viewModel.startScan();
        }
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
