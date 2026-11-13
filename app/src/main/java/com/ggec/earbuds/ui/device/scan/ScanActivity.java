package com.ggec.earbuds.ui.device.scan;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.port.ScanPermissionRequest;
import com.ggec.earbuds.domain.device.port.ScanPermissionResult;
import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.ui.device.DeviceFailureText;
import com.ggec.earbuds.wiring.AppContainer;
import com.yohu.youi.api.feedback.YoToast;

/**
 * 扫描连接页宿主。扫描的启停决策全在 {@link ScanViewModel}：
 * 本页只把用户动作转发给 VM、按 VM 事件申请权限并回传结果、渲染状态与提示文案。
 *
 * <p>权限请求走宿主能力接口 {@link ScanPermissionRequest}（由装配根注入），界面层不接触
 * 任何权限 SDK。</p>
 */
public final class ScanActivity extends AppCompatActivity {

    private ScanViewModel viewModel;
    private ScanPermissionRequest permissionRequest;
    private ScanScreen screen;

    @NonNull
    public static Intent intent(@NonNull Context context) {
        return new Intent(context, ScanActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YoUi.edgeToEdge(this);
        AppContainer container = container();
        viewModel = new ViewModelProvider(this, container.device().scanFactory())
                .get(ScanViewModel.class);
        permissionRequest = container.device().scanPermissionRequest(this);
        screen = new ScanScreen(this, new ScanScreen.Listener() {
            @Override
            public void onBack() {
                leaveUnlessConnecting();
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
        viewModel.connectOutcomes().observe(this, outcome -> {
            if (outcome instanceof ScanConnectOutcome.Succeeded) {
                finish();
            } else if (outcome instanceof ScanConnectOutcome.Failed failed) {
                YoToast.showWarning(this, getString(DeviceFailureText.message(failed.failure())));
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                leaveUnlessConnecting();
            }
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
        ScanPermissionResult result = permissionRequest.onResult(requestCode, permissions,
                grantResults);
        if (!result.handled()) {
            return;
        }
        viewModel.onPermissionAnswer(result.granted());
        if (result.granted()) {
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
    private void leaveUnlessConnecting() {
        if (viewModel.isConnecting()) {
            return;
        }
        finish();
    }

    private void requestScanPermissions() {
        if (permissionRequest.ensureGranted()) {
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
