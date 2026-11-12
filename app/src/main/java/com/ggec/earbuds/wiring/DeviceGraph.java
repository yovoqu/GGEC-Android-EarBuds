package com.ggec.earbuds.wiring;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.ggec.earbuds.data.device.DeviceProfileCatalogImpl;
import com.ggec.earbuds.data.device.EmptyDeviceControlsPort;
import com.ggec.earbuds.domain.device.port.DeviceControlsPort;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.ggec.earbuds.domain.device.port.DeviceProfileCatalog;
import com.ggec.earbuds.domain.device.port.ScanPermissionPort;
import com.ggec.earbuds.domain.device.usecase.ConnectDevice;
import com.ggec.earbuds.domain.device.usecase.EditDeviceControls;
import com.ggec.earbuds.domain.device.usecase.LoadDeviceProfiles;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.domain.device.usecase.ScanDevices;
import com.ggec.earbuds.provider.lib_bluetooth.HsDevicePort;
import com.ggec.earbuds.provider.lib_common.BluetoothScanPermission;
import com.ggec.earbuds.ui.device.DevicePage;
import com.ggec.earbuds.ui.device.home.DeviceHomeUiMapper;
import com.ggec.earbuds.ui.device.home.DeviceHomeViewModel;
import com.ggec.earbuds.ui.device.scan.ScanViewModel;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiMapper;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsViewModel;


/**
 * 设备功能装配根：唯一知道端口与目录实现的地方。
 * {@link HsDevicePort} 只投影 biz_ggec_hs 已识别的附近耳机和协议会话。
 */
public final class DeviceGraph {

    private final DevicePort port;
    private final LoadDeviceProfiles loadProfiles;
    private final ObserveDevice observeDevice;
    private final ConnectDevice connectDevice;
    private final ScanDevices scanDevices;
    private final EditDeviceControls editControls;
    private final ScanPermissionPort scanPermission;
    private final DeviceHomeUiMapper homeMapper = new DeviceHomeUiMapper();
    private final DeviceSettingsUiMapper settingsMapper = new DeviceSettingsUiMapper();

    public DeviceGraph(@NonNull Context appContext) {
        DeviceProfileCatalog catalog = new DeviceProfileCatalogImpl();
        this.port = new HsDevicePort(appContext);
        DeviceControlsPort controls = new EmptyDeviceControlsPort();
        this.loadProfiles = new LoadDeviceProfiles(catalog);
        this.observeDevice = new ObserveDevice(port);
        this.connectDevice = new ConnectDevice(port);
        this.scanDevices = new ScanDevices(port);
        this.editControls = new EditDeviceControls(controls);
        this.scanPermission = new BluetoothScanPermission(appContext);
    }

    @NonNull
    public ViewModelProvider.Factory homeFactory() {
        return new Factory<>(DeviceHomeViewModel.class,
                () -> new DeviceHomeViewModel(loadProfiles, observeDevice, connectDevice,
                        editControls, homeMapper));
    }

    @NonNull
    public ViewModelProvider.Factory scanFactory() {
        return new Factory<>(ScanViewModel.class,
                () -> new ScanViewModel(scanDevices, connectDevice, loadProfiles, scanPermission));
    }

    @NonNull
    public ViewModelProvider.Factory settingsFactory(@NonNull DevicePage page) {
        return new Factory<>(DeviceSettingsViewModel.class,
                () -> new DeviceSettingsViewModel(page, loadProfiles, observeDevice,
                        editControls, settingsMapper));
    }

    /** 释放设备子图持有的底层 BLE/协议资源（停止扫描、关闭会话、释放 BLE 客户端）。 */
    public void release() {
        port.close();
    }

    private interface Builder<T extends ViewModel> {
        T create();
    }

    private static final class Factory<T extends ViewModel> implements ViewModelProvider.Factory {

        private final Class<T> type;
        private final Builder<T> builder;

        private Factory(Class<T> type, Builder<T> builder) {
            this.type = type;
            this.builder = builder;
        }

        @NonNull
        @Override
        public <M extends ViewModel> M create(@NonNull Class<M> modelClass,
                                              @NonNull CreationExtras extras) {
            if (!type.equals(modelClass)) {
                throw new IllegalArgumentException("Unknown model: " + modelClass.getName());
            }
            return modelClass.cast(builder.create());
        }
    }
}
