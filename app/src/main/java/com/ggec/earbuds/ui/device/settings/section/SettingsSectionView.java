package com.ggec.earbuds.ui.device.settings.section;

import android.view.View;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiState;

/** 设置页内一个功能区块：自管布局与释放。 */
public interface SettingsSectionView {

    @NonNull
    View view();

    void render(@NonNull DeviceSettingsUiState state);

    void destroy();
}
