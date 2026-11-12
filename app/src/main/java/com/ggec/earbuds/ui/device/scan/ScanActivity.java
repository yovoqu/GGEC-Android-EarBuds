package com.ggec.earbuds.ui.device.scan;

import android.content.Context;
import android.content.Intent;
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

/**
 * 扫描连接页宿主。扫描的启停决策全在 {@link ScanViewModel}：
 * 本页只把用户动作转发给 VM、按 VM 事件申请权限并回传结果、渲染状态与提示文案。
 */
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
                viewModel.toggleScan();
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
        viewModel.events().observe(this, event -> {
            if (event == null) {
                return;
            }
            if (event.requiresPermissionRequest()) {
                requestScanPermissions();
                return;
            }
            YoToast.showWarning(this, getString(event.messageRes()));
        });
        // 进页即请求一次扫描：是否可扫由 VM 判定，权限缺失时它会发 PERMISSION_REQUIRED。
        // 走 enterScan() 而不是 toggleScan()——后者是翻转语义，配置变更重建会把正在跑的扫描翻停。
        viewModel.enterScan();
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
        viewModel.onPermissionAnswer(result.allGranted());
        if (result.allGranted()) {
            return;
        }
        YoToast.showWarning(this, getString(result.permanentlyDenied()
                ? R.string.device_scan_error_permission_settings
                : R.string.device_scan_error_permission));
    }

    @Override
    protected void onDestroy() {
        screen.destroy();
        super.onDestroy();
    }

    /**
     * 发起系统权限请求并把「已授权」这一确定结果回传 VM。
     * 这里不做扫描决策：请求返回 true 只说明权限已具备，仍由 VM 决定是否开扫。
     */
    private void requestScanPermissions() {
        if (PermissionProvider.requestBluetoothIfNeeded(this)) {
            viewModel.onPermissionAnswer(true);
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
