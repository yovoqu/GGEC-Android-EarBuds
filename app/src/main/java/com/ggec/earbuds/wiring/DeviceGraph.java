package com.ggec.earbuds.wiring;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.ggec.earbuds.data.device.roster.DeviceRosterRepositoryImpl;
import com.ggec.earbuds.data.store.FileDocumentStore;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DeviceRosterRepository;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.domain.device.port.ScanPermissionPort;
import com.ggec.earbuds.domain.device.session.DeviceSession;
import com.ggec.earbuds.domain.device.usecase.ForgetDevice;
import com.ggec.earbuds.domain.device.usecase.ListDeviceEntries;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.domain.device.usecase.ObserveDeviceRoster;
import com.ggec.earbuds.domain.device.usecase.OpenDevice;
import com.ggec.earbuds.domain.device.usecase.RunDeviceAction;
import com.ggec.earbuds.domain.device.usecase.ScanDevices;
import com.ggec.earbuds.domain.device.usecase.SyncActiveDevice;
import com.ggec.earbuds.domain.device.usecase.WriteDeviceControl;
import com.ggec.earbuds.platform.device.AndroidPcmPlayback;
import com.ggec.earbuds.provider.lib_common.permission.BluetoothScanPermission;
import com.ggec.earbuds.provider.lib_common.permission.BluetoothScanPermissionRequest;
import com.ggec.earbuds.domain.device.port.ScanPermissionRequest;
import com.ggec.earbuds.ui.device.home.DeviceHomeUiMapper;
import com.ggec.earbuds.ui.device.home.DeviceHomeViewModel;
import com.ggec.earbuds.ui.device.find.FindDeviceViewModel;
import com.ggec.earbuds.ui.device.wear.WearSettingsViewModel;
import com.ggec.earbuds.ui.device.record.RecordViewModel;
import com.ggec.earbuds.ui.device.scan.ScanViewModel;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiMapper;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsViewModel;
import com.ggec.earbuds.ui.device.settings.SettingsSectionFactory;
import com.ggec.earbuds.domain.device.model.DeviceTransport;
import com.ggec.earbuds.wiring.device.DeviceCatalog;
import com.ggec.earbuds.wiring.device.DeviceEndpoint;
import com.ggec.earbuds.wiring.device.DeviceEndpoints;
import com.ggec.earbuds.wiring.device.DeviceGateway;
import com.ggec.earbuds.wiring.device.HsDeviceEndpoint;
import com.ggec.earbuds.wiring.device.RecordDeviceEndpoint;
import com.ggec.earbuds.wiring.device.WyDeviceEndpoint;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * 设备功能装配根：唯一知道设备端点、台账仓库与用例实现的地方。
 *
 * <p>结构只有一套：<b>端点</b>（每 SDK 一个，按传输区分）→ <b>网关</b>（机型路由 + 扫描合并 +
 * 录音路由，同时实现 {@code DeviceLinkPort} 与 {@code RecordPort}）→ <b>会话</b>（显式状态机）
 * → <b>用例</b>。机型到端点的索引、合并目录都由 {@link DeviceEndpoints} 在建图时定下，
 * 本类不出现任何机型字面量，也没有「活跃驱动」这类并行状态。</p>
 *
 * <p>释放顺序固定：先关会话（发唯一一次 DISCONNECTED 并停止跟踪），再关网关（解订阅、
 * 关各端点 SDK），最后停台账同步。</p>
 */
public final class DeviceGraph {

    private final DeviceEndpoints endpoints;
    private final DeviceGateway gateway;
    private final DeviceSession session;
    private final Context appContext;
    private final SyncActiveDevice syncActiveDevice;
    private final DeviceCapabilityCatalog catalog;
    private final DeviceRosterRepository roster;
    private final ScanDevices scanDevices;
    private final WriteDeviceControl writeControl;
    private final RunDeviceAction runAction;
    private final ObserveDevice observeDevice;
    private final ObserveDeviceRoster observeRoster;
    private final OpenDevice openDevice;
    private final ForgetDevice forgetDevice;
    private final ScanPermissionPort scanPermission;
    private final RecordPort recordPort;
    private final DeviceHomeUiMapper homeMapper;
    private final ListDeviceEntries listEntries;
    private final DeviceSettingsUiMapper settingsMapper =
            new DeviceSettingsUiMapper(new SettingsSectionFactory());

    public DeviceGraph(@NonNull Context appContext, @NonNull Executor storeExecutor,
                       @NonNull LongSupplier clock) {
        Context app = appContext.getApplicationContext();
        this.appContext = app;
        this.endpoints = new DeviceEndpoints(List.<DeviceEndpoint>of(
                new HsDeviceEndpoint(app),
                new WyDeviceEndpoint(app),
                new RecordDeviceEndpoint(app)));
        this.gateway = new DeviceGateway(endpoints);
        this.catalog = new DeviceCatalog(endpoints.models());
        this.recordPort = gateway;

        this.roster = new DeviceRosterRepositoryImpl(
                new FileDocumentStore(new File(app.getFilesDir(), "devices")), "roster.json",
                Objects.requireNonNull(storeExecutor, "storeExecutor"));
        this.session = new DeviceSession(gateway);
        this.syncActiveDevice = new SyncActiveDevice(roster, session, clock);
        this.observeRoster = new ObserveDeviceRoster(roster, session, clock);
        this.openDevice = new OpenDevice(session);
        this.forgetDevice = new ForgetDevice(roster, session);
        this.observeDevice = new ObserveDevice(session);
        this.homeMapper = new DeviceHomeUiMapper(catalog);
        this.listEntries = new ListDeviceEntries(catalog);
        this.scanDevices = new ScanDevices(gateway);
        this.writeControl = new WriteDeviceControl(gateway);
        this.runAction = new RunDeviceAction(gateway);
        this.scanPermission = new BluetoothScanPermission(app);
    }

    /**
     * 启动活动会话的台账跟踪。由装配根在建成后显式调用一次：构造期不自行订阅，
     * 因此 {@link #release()} 的停止一定与这次启动成对，且重复调用为幂等 no-op。
     */
    public void start() {
        syncActiveDevice.start();
    }

    @NonNull
    public ViewModelProvider.Factory homeFactory() {
        return new Factory<>(DeviceHomeViewModel.class,
                () -> new DeviceHomeViewModel(observeRoster, listEntries, openDevice,
                        writeControl, homeMapper));
    }

    @NonNull
    public ViewModelProvider.Factory scanFactory() {
        return new Factory<>(ScanViewModel.class,
                () -> new ScanViewModel(scanDevices, openDevice, catalog, scanPermission,
                        recordLaunchModelIds()));
    }

    /** BLE_AV 端点上的机型：扫描页点连接时分流到 Record UI，不经 DeviceSession。 */
    @NonNull
    private Set<String> recordLaunchModelIds() {
        Set<String> ids = new LinkedHashSet<>();
        endpoints.of(DeviceTransport.BLE_AV).ifPresent(endpoint -> {
            for (var profile : endpoint.catalog().models()) {
                ids.add(profile.modelId());
            }
        });
        return Set.copyOf(ids);
    }

    @NonNull
    public ViewModelProvider.Factory recordFactory() {
        return new Factory<>(RecordViewModel.class,
                () -> new RecordViewModel(recordPort, observeDevice,
                        new AndroidPcmPlayback(appContext), new MainThreadTicker(),
                        SystemClock::elapsedRealtime));
    }

    @NonNull
    public ViewModelProvider.Factory settingsFactory(@NonNull DeviceCapabilityGroup group) {
        return new Factory<>(DeviceSettingsViewModel.class,
                () -> new DeviceSettingsViewModel(group, catalog, observeDevice,
                        writeControl, runAction, forgetDevice, observeRoster,
                        settingsMapper));
    }

    @NonNull
    public ViewModelProvider.Factory findFactory() {
        return new Factory<>(FindDeviceViewModel.class,
                () -> new FindDeviceViewModel(observeDevice, observeRoster, runAction));
    }

    @NonNull
    public ViewModelProvider.Factory wearFactory() {
        return new Factory<>(WearSettingsViewModel.class,
                () -> new WearSettingsViewModel(observeDevice, writeControl));
    }

    /**
     * 宿主权限能力缝：Android 运行时权限只能由 Activity 发起，实现按宿主构造，
     * 装配根不缓存 Activity 生命周期外的引用。
     */
    @NonNull
    public ScanPermissionRequest scanPermissionRequest(@NonNull Activity host) {
        return new BluetoothScanPermissionRequest(host);
    }

    /** 释放设备子图：会话 → 网关（端点 SDK）→ 台账同步。 */
    public void release() {
        session.close();
        gateway.close();
        syncActiveDevice.stop();
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
