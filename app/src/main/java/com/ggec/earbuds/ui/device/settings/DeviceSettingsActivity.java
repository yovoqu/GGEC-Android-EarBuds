package com.ggec.earbuds.ui.device.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.ui.common.YoUi;
import com.ggec.earbuds.ui.device.DeviceFailureText;
import com.ggec.earbuds.wiring.AppContainer;
import com.yohu.youi.api.feedback.YoToast;

import java.util.List;

/**
 * 二级设置页宿主：一页对应一个能力分组（{@link DeviceCapabilityGroup}）。
 * 写入失败只把分类翻成文案提示，不在页面里吞掉。
 */
public final class DeviceSettingsActivity extends AppCompatActivity {

    private static final String EXTRA_GROUP = "group";

    private DeviceSettingsViewModel viewModel;
    private DeviceSettingsScreen screen;

    @NonNull
    public static Intent intent(@NonNull Context context, @NonNull DeviceCapabilityGroup group) {
        Intent intent = new Intent(context, DeviceSettingsActivity.class);
        intent.putExtra(EXTRA_GROUP, group.name());
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YoUi.edgeToEdge(this);
        DeviceCapabilityGroup group = group();
        viewModel = new ViewModelProvider(this, container().device().settingsFactory(group))
                .get(DeviceSettingsViewModel.class);
        screen = new DeviceSettingsScreen(this, new SettingsListener());
        setContentView(screen.view());
        viewModel.uiState().observe(this, state -> {
            if (state != null) {
                screen.render(state);
            }
        });
        viewModel.events().observe(this, event -> {
            switch (event) {
                case null -> {
                }
                case DeviceSettingsEvent.RosterMissing ignored ->
                        YoToast.showWarning(this, getString(R.string.device_error_not_found));
                case DeviceSettingsEvent.Closed ignored -> finish();
                case DeviceSettingsEvent.Failure failure ->
                        YoToast.showWarning(this,
                                getString(DeviceFailureText.message(failure.failure())));
                case DeviceSettingsEvent.ControlOutcome outcome ->
                        screen.onControlOutcome(outcome.outcome());
            }
        });
        viewModel.start();
    }

    @NonNull
    private DeviceCapabilityGroup group() {
        String raw = getIntent().getStringExtra(EXTRA_GROUP);
        if (raw == null || raw.isEmpty()) {
            throw new IllegalStateException("DeviceSettingsActivity requires EXTRA_GROUP");
        }
        return DeviceCapabilityGroup.valueOf(raw);
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

    private final class SettingsListener implements DeviceSettingsScreen.Listener {

        @Override
        public void onBack() {
            finish();
        }

        @Override
        public void onToggle(@NonNull String capabilityId, boolean enabled) {
            viewModel.onToggle(capabilityId, enabled);
        }

        @Override
        public void onChoice(@NonNull String capabilityId, @NonNull String optionId) {
            viewModel.onChoice(capabilityId, optionId);
        }

        @Override
        public void onLevel(@NonNull String capabilityId, int level) {
            viewModel.onLevel(capabilityId, level);
        }

        @Override
        public void onCurve(@NonNull String capabilityId, @NonNull List<Integer> points) {
            viewModel.onCurve(capabilityId, points);
        }

        @Override
        public void onAction(@NonNull DeviceSettingRow.ActionKind kind) {
            viewModel.onAction(kind);
        }

        @Override
        public void onFindEarLeft() {
            viewModel.onFindEarLeft();
        }

        @Override
        public void onFindEarRight() {
            viewModel.onFindEarRight();
        }

        @Override
        public void onStopFindAlert() {
            viewModel.onStopFindAlert();
        }

        @Override
        public void onRename(@NonNull String newName) {
            viewModel.onRename(newName);
        }

        @Override
        public void onStartRecordLocal() {
            viewModel.onStartRecordLocal();
        }

        @Override
        public void onStopRecordLocal() {
            viewModel.onStopRecordLocal();
        }

        @Override
        public void onStartRecordStream() {
            viewModel.onStartRecordStream();
        }

        @Override
        public void onStopRecordStream() {
            viewModel.onStopRecordStream();
        }

        @Override
        public void onFileTransfer(@NonNull String fileId) {
            viewModel.onFileTransfer(fileId);
        }

        @Override
        public void onConnectPairedDevice(@NonNull String address) {
            viewModel.onConnectPairedDevice(address);
        }

        @Override
        public void onDisconnectPairedDevice(@NonNull String address) {
            viewModel.onDisconnectPairedDevice(address);
        }

        @Override
        public void onSetPriorityPairedDevice(@NonNull String address) {
            viewModel.onSetPriorityPairedDevice(address);
        }

        @Override
        public void onForgetPairedDevice(@NonNull String address) {
            viewModel.onForgetPairedDevice(address);
        }
    }
}
