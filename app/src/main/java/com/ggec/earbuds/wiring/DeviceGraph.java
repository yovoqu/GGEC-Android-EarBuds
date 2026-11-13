package com.ggec.earbuds.wiring;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.ggec.earbuds.data.device.json.DeviceRosterJson;
import com.ggec.earbuds.data.device.roster.DeviceRosterRepositoryImpl;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DeviceRosterRepository;
import com.ggec.earbuds.domain.device.port.ScanPermissionPort;
import com.ggec.earbuds.domain.device.session.DeviceSession;
import com.ggec.earbuds.domain.device.usecase.CloseDevice;
import com.ggec.earbuds.domain.device.usecase.ForgetDevice;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.domain.device.usecase.ObserveDeviceRoster;
import com.ggec.earbuds.domain.device.usecase.OpenDevice;
import com.ggec.earbuds.domain.device.usecase.RenameDevice;
import com.ggec.earbuds.domain.device.usecase.RunDeviceAction;
import com.ggec.earbuds.domain.device.usecase.ListDeviceEntries;
import com.ggec.earbuds.domain.device.usecase.ScanDevices;
import com.ggec.earbuds.domain.device.usecase.SyncActiveDevice;
import com.ggec.earbuds.domain.device.usecase.WriteDeviceControl;
import com.ggec.earbuds.provider.biz_ggec_hs.HsDeviceProviders;
import com.ggec.earbuds.domain.device.port.ScanPermissionRequest;
import com.ggec.earbuds.domain.device.port.ScanPermissionResult;
import com.ggec.earbuds.provider.lib_common.permission.BluetoothScanPermission;
import com.ggec.earbuds.provider.lib_common.permission.BluetoothScanPermissionRequest;
import com.ggec.earbuds.ui.device.home.DeviceHomeUiMapper;
import com.ggec.earbuds.ui.device.home.DeviceHomeViewModel;
import com.ggec.earbuds.ui.device.scan.ScanViewModel;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiMapper;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsViewModel;
import com.ggec.earbuds.ui.device.settings.SettingsSectionFactory;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * 设备功能装配根：唯一知道设备端口、台账仓库与用例实现的地方。
 *
 * <p>一条 BLE 客户端 → 一条链路端口 → 一个活动会话；台账在
 * {@link DeviceRosterRepositoryImpl} 里，落盘走注入的串行执行器。本类只持有领域端口与用例，
 * 不出现任何 SDK 类型。</p>
 */
public final class DeviceGraph {

    private final HsDeviceProviders providers;
    private final DeviceRosterRepository roster;
    private final DeviceSession session;
    private final DeviceCapabilityCatalog catalog;
    private final ScanDevices scanDevices;
    private final WriteDeviceControl writeControl;
    private final RunDeviceAction runAction;
    private final ObserveDevice observeDevice;
    private final ObserveDeviceRoster observeRoster;
    private final OpenDevice openDevice;
    private final CloseDevice closeDevice;
    private final RenameDevice renameDevice;
    private final ForgetDevice forgetDevice;
    private final ScanPermissionPort scanPermission;
    private final DeviceHomeUiMapper homeMapper;
    private final ListDeviceEntries listEntries;
    private final DeviceSettingsUiMapper settingsMapper =
            new DeviceSettingsUiMapper(new SettingsSectionFactory());

    public DeviceGraph(@NonNull Context appContext, @NonNull Executor storeExecutor,
                       @NonNull LongSupplier clock) {
        Context app = appContext.getApplicationContext();
        this.providers = HsDeviceProviders.open(app);
        this.roster = new DeviceRosterRepositoryImpl(
                new File(app.getFilesDir(), "devices/roster.json"), new DeviceRosterJson(),
                Objects.requireNonNull(storeExecutor, "storeExecutor"));
        this.session = new DeviceSession(providers.deviceLink());
        this.observeRoster = new ObserveDeviceRoster(roster, session, clock);
        this.openDevice = new OpenDevice(session);
        this.closeDevice = new CloseDevice(session);
        this.renameDevice = new RenameDevice(roster);
        this.forgetDevice = new ForgetDevice(roster, session);
        this.observeDevice = new ObserveDevice(session);
        this.catalog = providers.deviceCatalog();
        this.homeMapper = new DeviceHomeUiMapper(catalog);
        this.listEntries = new ListDeviceEntries(catalog);
        this.scanDevices = new ScanDevices(providers.deviceLink());
        this.writeControl = new WriteDeviceControl(providers.deviceLink());
        this.runAction = new RunDeviceAction(providers.deviceLink());
        this.scanPermission = new BluetoothScanPermission(app);
        new SyncActiveDevice(roster, session, clock).start();
    }

    @NonNull
    public ViewModelProvider.Factory homeFactory() {
        return new Factory<>(DeviceHomeViewModel.class,
                () -> new DeviceHomeViewModel(observeRoster, listEntries, openDevice,
                        renameDevice, forgetDevice, homeMapper));
    }

    @NonNull
    public ViewModelProvider.Factory scanFactory() {
        return new Factory<>(ScanViewModel.class,
                () -> new ScanViewModel(scanDevices, openDevice, catalog, scanPermission));
    }

    @NonNull
    public ViewModelProvider.Factory settingsFactory(@NonNull DeviceCapabilityGroup group) {
        return new Factory<>(DeviceSettingsViewModel.class,
                () -> new DeviceSettingsViewModel(group, catalog, observeDevice,
                        writeControl, runAction, forgetDevice, observeRoster,
                        settingsMapper));
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
