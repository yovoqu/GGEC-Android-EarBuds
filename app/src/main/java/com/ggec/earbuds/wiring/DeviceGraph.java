package com.ggec.earbuds.wiring;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.ggec.earbuds.domain.device.port.ScanPermissionPort;
import com.ggec.earbuds.domain.device.usecase.ConnectDevice;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.domain.device.usecase.RunDeviceAction;
import com.ggec.earbuds.domain.device.usecase.ScanDevices;
import com.ggec.earbuds.domain.device.usecase.WriteDeviceControl;
import com.ggec.earbuds.provider.biz_ggec_hs.HsDeviceProviders;
import com.ggec.earbuds.provider.lib_common.permission.BluetoothScanPermission;
import com.ggec.earbuds.provider.lib_common.permission.BluetoothScanPermissionRequest;
import com.ggec.earbuds.ui.device.home.DeviceHomeUiMapper;
import com.ggec.earbuds.ui.device.home.DeviceHomeViewModel;
import com.ggec.earbuds.ui.device.scan.ScanPermissionRequest;
import com.ggec.earbuds.ui.device.scan.ScanViewModel;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiMapper;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsViewModel;
import com.ggec.earbuds.ui.device.settings.SettingsSectionFactory;

/**
 * 设备功能装配根：唯一知道设备端口与能力目录实现的地方。
 *
 * <p>设备端口与能力目录都来自同一个 {@link HsDeviceProviders}（一条 BLE 客户端），
 * 本类只持有领域端口与用例，不出现任何 SDK 类型。</p>
 */
public final class DeviceGraph {

    private final HsDeviceProviders providers;
    private final DeviceCapabilityCatalog catalog;
    private final ObserveDevice observeDevice;
    private final ConnectDevice connectDevice;
    private final ScanDevices scanDevices;
    private final WriteDeviceControl writeControl;
    private final RunDeviceAction runAction;
    private final ScanPermissionPort scanPermission;
    private final DeviceHomeUiMapper homeMapper = new DeviceHomeUiMapper();
    private final DeviceSettingsUiMapper settingsMapper =
            new DeviceSettingsUiMapper(new SettingsSectionFactory());

    public DeviceGraph(@NonNull Context appContext) {
        Context app = appContext.getApplicationContext();
        this.providers = HsDeviceProviders.open(app);
        DevicePort port = providers.devicePort();
        this.catalog = providers.deviceCatalog();
        this.observeDevice = new ObserveDevice(port);
        this.connectDevice = new ConnectDevice(port);
        this.scanDevices = new ScanDevices(port);
        this.writeControl = new WriteDeviceControl(port);
        this.runAction = new RunDeviceAction(port);
        this.scanPermission = new BluetoothScanPermission(app);
    }

    @NonNull
    public ViewModelProvider.Factory homeFactory() {
        return new Factory<>(DeviceHomeViewModel.class,
                () -> new DeviceHomeViewModel(catalog, observeDevice, connectDevice,
                        writeControl, homeMapper));
    }

    @NonNull
    public ViewModelProvider.Factory scanFactory() {
        return new Factory<>(ScanViewModel.class,
                () -> new ScanViewModel(scanDevices, connectDevice, catalog, scanPermission));
    }

    @NonNull
    public ViewModelProvider.Factory settingsFactory(@NonNull DeviceCapabilityGroup group) {
        return new Factory<>(DeviceSettingsViewModel.class,
                () -> new DeviceSettingsViewModel(group, catalog, observeDevice,
                        writeControl, runAction, settingsMapper));
    }

    /**
     * 宿主权限能力缝：Android 运行时权限只能由 Activity 发起，实现按宿主构造，
     * 装配根不缓存 Activity 生命周期外的引用。
     */
    @NonNull
    public ScanPermissionRequest scanPermissionRequest(@NonNull Activity host) {
        return new BluetoothScanPermissionRequest(host);
    }

    /** 释放设备子图持有的底层 BLE/协议资源（停止扫描、关闭会话、释放 BLE 客户端）。 */
    public void release() {
        providers.close();
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
