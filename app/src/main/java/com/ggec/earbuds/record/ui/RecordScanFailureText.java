package com.ggec.earbuds.record.ui;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.record.model.RecordScanFailure;

/**
 * 扫描失败分类 → 用户文案的 UI 层映射。领域层只给类别，文案落在这里与 strings 资源。
 */
final class RecordScanFailureText {

    private RecordScanFailureText() {
    }

    @NonNull
    static String of(@NonNull Context context, @NonNull RecordScanFailure failure) {
        return switch (failure) {
            case BLUETOOTH_DISABLED -> context.getString(R.string.record_scan_fail_bluetooth_disabled);
            case PERMISSION_DENIED -> context.getString(R.string.record_scan_fail_permission_denied);
            case BLE_UNSUPPORTED -> context.getString(R.string.record_scan_fail_ble_unsupported);
            case THROTTLED -> context.getString(R.string.record_scan_fail_throttled);
            case FAILED -> context.getString(R.string.record_scan_fail_failed);
        };
    }
}
