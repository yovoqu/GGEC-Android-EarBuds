package com.ggec.earbuds.ui.device.settings.section;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;

import java.util.List;

/**
 * 设置页类型化 UI 交互监听器：View 层向外派发用户意图，禁止直接操作 ViewModel 或构造领域模型。
 */
public interface SettingsInteractionListener {

    void onToggle(@NonNull String capabilityId, boolean enabled);

    void onChoice(@NonNull String capabilityId, @NonNull String optionId);

    void onLevel(@NonNull String capabilityId, int level);

    void onCurve(@NonNull String capabilityId, @NonNull String name,
                 @NonNull List<Integer> points);

    void onAction(@NonNull DeviceSettingRow.ActionKind kind);

    default void onReboot() {
        onAction(DeviceSettingRow.ActionKind.REBOOT);
    }

    default void onClearPairing() {
        onAction(DeviceSettingRow.ActionKind.CLEAR_PAIRING);
    }

    default void onResetControl() {
        onAction(DeviceSettingRow.ActionKind.RESET_CONTROL);
    }

    default void onForgetActive() {
        onAction(DeviceSettingRow.ActionKind.DEVICE_MANAGEMENT);
    }

    void onFindEarLeft();

    void onFindEarRight();

    void onStopFindAlert();

    void onRename(@NonNull String newName);

    void onStartRecordLocal();

    void onStopRecordLocal();

    void onStartRecordStream();

    void onStopRecordStream();

    void onFileTransfer(@NonNull String fileId);

    void onConnectPairedDevice(@NonNull String address);

    void onDisconnectPairedDevice(@NonNull String address);

    void onSetPriorityPairedDevice(@NonNull String address);

    void onForgetPairedDevice(@NonNull String address);
}
