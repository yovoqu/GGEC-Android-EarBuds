package com.ggec.earbuds.ui.device.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.usecase.ListDeviceEntries;
import com.ggec.earbuds.domain.device.usecase.ObserveDeviceRoster;
import com.ggec.earbuds.domain.device.usecase.OpenDevice;
import com.ggec.earbuds.domain.device.usecase.WriteDeviceControl;
import com.ggec.earbuds.ui.device.roster.DeviceRosterEvent;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 设备页视图模型：台账 → 标题栏、概览（电量/状态）、首页内联开关、卡片入口与列表入口。
 *
 * <p>只经用例触达领域：观察台账、列能力入口、打开设备、写内联开关。入口分流由
 * {@link DeviceHomeUiMapper} 决定，本类不判断分组。</p>
 */
public final class DeviceHomeViewModel extends ViewModel {

    private final ObserveDeviceRoster observeRoster;
    private final ListDeviceEntries listEntries;
    private final OpenDevice openDevice;
    private final WriteDeviceControl writeControl;
    private final DeviceHomeUiMapper mapper;

    private final MutableLiveData<DeviceHomeUiState> uiState = new MutableLiveData<>();
    private final OneShotLiveData<DeviceRosterEvent> events = new OneShotLiveData<>();

    /** 内联开关的写入槽，按能力 id 索引；断开连接时清空。 */
    private final Map<String, DeviceHomeUiState.WriteSlot> writes = new LinkedHashMap<>();

    private Subscription rosterSubscription;
    private Subscription failureSubscription;
    private DeviceRoster roster = DeviceRoster.empty();

    public DeviceHomeViewModel(@NonNull ObserveDeviceRoster observeRoster,
                               @NonNull ListDeviceEntries listEntries,
                               @NonNull OpenDevice openDevice,
                               @NonNull WriteDeviceControl writeControl,
                               @NonNull DeviceHomeUiMapper mapper) {
        this.observeRoster = Objects.requireNonNull(observeRoster, "observeRoster");
        this.listEntries = Objects.requireNonNull(listEntries, "listEntries");
        this.openDevice = Objects.requireNonNull(openDevice, "openDevice");
        this.writeControl = Objects.requireNonNull(writeControl, "writeControl");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @NonNull
    public LiveData<DeviceHomeUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<DeviceRosterEvent> events() {
        return events;
    }

    public void start() {
        if (rosterSubscription != null) {
            return;
        }
        failureSubscription = observeRoster.observeFailures(
                failure -> events.postValue(new DeviceRosterEvent.StoreFailed()));
        observeRoster.startupFailure().ifPresent(
                failure -> events.postValue(new DeviceRosterEvent.StoreFailed()));
        rosterSubscription = observeRoster.execute(this::onRoster);
    }

    /** 下拉里选中一台：先断开当前设备，再由用例连接目标（会话内部保证顺序）。 */
    public void select(@NonNull String key) {
        DeviceRecord record = find(key);
        if (record == null) {
            events.postValue(new DeviceRosterEvent.DeviceMissing());
            return;
        }
        openDevice.execute(DeviceTarget.of(record), settlement -> {
            if (settlement instanceof ConnectSettlement.Ready) {
                events.postValue(new DeviceRosterEvent.OpenSucceeded(record.id()));
                return;
            }
            if (settlement instanceof ConnectSettlement.Failed failed) {
                events.postValue(new DeviceRosterEvent.OpenFailed(failed.failure()));
            }
        });
    }

    /** 首页内联开关：先落 PENDING（带本次值），结果落 CONFIRMED 或丢弃回退。 */
    public void toggle(@NonNull String capabilityId, boolean enabled) {
        if (!isWritable()) {
            return;
        }
        putWrite(capabilityId, new DeviceHomeUiState.WriteSlot(
                DeviceHomeUiState.WriteState.PENDING, Optional.of(enabled)));
        publish();
        writeControl.execute(capabilityId, new ControlValue.Toggle(enabled),
                result -> onWriteResult(capabilityId, enabled, result));
    }

    private void onWriteResult(@NonNull String capabilityId, boolean enabled,
                               @NonNull DeviceWriteResult result) {
        switch (result) {
            case DeviceWriteResult.Ok ignored -> putWrite(capabilityId,
                    new DeviceHomeUiState.WriteSlot(
                            DeviceHomeUiState.WriteState.CONFIRMED, Optional.of(enabled)));
            case DeviceWriteResult.Failed failed -> {
                putWrite(capabilityId, new DeviceHomeUiState.WriteSlot(
                        DeviceHomeUiState.WriteState.FAILED, Optional.empty()));
                events.postValue(new DeviceRosterEvent.WriteFailed(failed.failure()));
            }
        }
        publish();
    }

    private boolean isWritable() {
        return roster.activeRecord().map(record -> record.live() && record.status().ready())
                .orElse(false);
    }

    private DeviceRecord find(String key) {
        for (DeviceRecord record : roster.devices()) {
            if (record.id().key().equals(key)) {
                return record;
            }
        }
        return null;
    }

    private synchronized void putWrite(@NonNull String capabilityId,
                                       @NonNull DeviceHomeUiState.WriteSlot slot) {
        writes.put(capabilityId, slot);
    }

    private synchronized Map<String, DeviceHomeUiState.WriteSlot> currentWrites() {
        return Map.copyOf(writes);
    }

    private void onRoster(@NonNull DeviceRoster next) {
        synchronized (this) {
            roster = next;
            boolean ready = next.activeRecord()
                    .map(record -> record.live() && record.status().ready()).orElse(false);
            if (!ready) {
                // 写入事实属于当前连接：断开后旧槽不得在新设备上复活。
                writes.clear();
            }
        }
        publish();
    }

    private void publish() {
        DeviceRoster current = roster;
        uiState.postValue(mapper.map(current,
                listEntries.execute(current.activeId().orElse(null)), currentWrites()));
    }

    @Override
    protected void onCleared() {
        if (rosterSubscription != null) {
            rosterSubscription.unsubscribe();
            rosterSubscription = null;
        }
        if (failureSubscription != null) {
            failureSubscription.unsubscribe();
            failureSubscription = null;
        }
        super.onCleared();
    }
}
