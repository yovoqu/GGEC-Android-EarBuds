package com.ggec.earbuds.ui.device.settings;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.ui.device.DeviceFormats;

import java.util.ArrayList;
import java.util.List;

/** 关于页只展示公共协议里的设备事实，不读取项目 SDK。 */
public final class AboutInfoRows {

    private AboutInfoRows() {
    }

    @NonNull
    public static List<DeviceSettingRow> build(@NonNull DeviceSnapshot snapshot) {
        List<DeviceSettingRow> rows = new ArrayList<>();
        rows.add(new DeviceSettingRow.Info(R.string.device_row_model,
                DeviceFormats.textOrUnknown(snapshot.modelId())));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_ble_name,
                DeviceFormats.textOrUnknown(snapshot.bleName())));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_firmware,
                DeviceFormats.textOrUnknown(snapshot.firmware())));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_ble_address,
                DeviceFormats.textOrUnknown(snapshot.bleAddress())));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_classic_name,
                DeviceFormats.textOrUnknown(snapshot.classicName())));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_classic_address,
                DeviceFormats.textOrUnknown(snapshot.classicAddress())));
        rows.add(new DeviceSettingRow.Info(R.string.device_row_color,
                DeviceFormats.earColor(snapshot.earColor())));
        return List.copyOf(rows);
    }
}
