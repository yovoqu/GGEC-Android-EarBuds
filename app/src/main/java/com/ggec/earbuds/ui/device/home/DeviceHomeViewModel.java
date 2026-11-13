package com.ggec.earbuds.ui.device.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.usecase.ForgetDevice;
import com.ggec.earbuds.domain.device.usecase.ListDeviceEntries;
import com.ggec.earbuds.domain.device.usecase.ObserveDeviceRoster;
import com.ggec.earbuds.domain.device.usecase.OpenDevice;
import com.ggec.earbuds.domain.device.usecase.RenameDevice;
import com.ggec.earbuds.ui.device.roster.DeviceRosterEvent;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.Objects;

/**
 * 设备页视图模型：台账 → 标题栏（名称 + MAC·型号 + 下拉）与能力入口。
 *
 * <p>只经用例触达领域：观察台账、列能力入口、打开设备、改名、忘记。能力入口不在界面里
 * 推断，全部来自 {@link ListDeviceEntries}（数据源是 biz 设备目录）。</p>
 */
public final class DeviceHomeViewModel extends ViewModel {

    private final ObserveDeviceRoster observeRoster;
    private final ListDeviceEntries listEntries;
    private final OpenDevice openDevice;
    private final RenameDevice renameDevice;
    private final ForgetDevice forgetDevice;
    private final DeviceHomeUiMapper mapper;

    private final MutableLiveData<DeviceHomeUiState> uiState = new MutableLiveData<>();
    private final OneShotLiveData<DeviceRosterEvent> events = new OneShotLiveData<>();

    private Subscription rosterSubscription;
    private Subscription failureSubscription;
    private DeviceRoster roster = DeviceRoster.empty();

    public DeviceHomeViewModel(@NonNull ObserveDeviceRoster observeRoster,
                               @NonNull ListDeviceEntries listEntries,
                               @NonNull OpenDevice openDevice,
                               @NonNull RenameDevice renameDevice,
                               @NonNull ForgetDevice forgetDevice,
                               @NonNull DeviceHomeUiMapper mapper) {
        this.observeRoster = Objects.requireNonNull(observeRoster, "observeRoster");
        this.listEntries = Objects.requireNonNull(listEntries, "listEntries");
        this.openDevice = Objects.requireNonNull(openDevice, "openDevice");
        this.renameDevice = Objects.requireNonNull(renameDevice, "renameDevice");
        this.forgetDevice = Objects.requireNonNull(forgetDevice, "forgetDevice");
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

    public void renameActive(@NonNull String alias) {
        DeviceRecord active = activeRecord();
        if (active == null) {
            events.postValue(new DeviceRosterEvent.DeviceMissing());
            return;
        }
        if (!renameDevice.execute(active.id(), alias)) {
            events.postValue(new DeviceRosterEvent.DeviceMissing());
        }
    }

    public void forgetActive() {
        DeviceRecord active = activeRecord();
        if (active == null) {
            events.postValue(new DeviceRosterEvent.DeviceMissing());
            return;
        }
        if (!forgetDevice.execute(active.id())) {
            events.postValue(new DeviceRosterEvent.DeviceMissing());
        }
    }

    @NonNull
    public String activeName() {
        DeviceRecord active = activeRecord();
        return active == null ? "" : active.displayName();
    }

    private DeviceRecord activeRecord() {
        return roster.activeId().flatMap(roster::find).orElse(null);
    }

    private DeviceRecord find(String key) {
        for (DeviceRecord record : roster.devices()) {
            if (record.id().key().equals(key)) {
                return record;
            }
        }
        return null;
    }

    private void onRoster(@NonNull DeviceRoster next) {
        roster = next;
        uiState.postValue(mapper.map(next, listEntries.execute(next.activeId().orElse(null))));
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

    /** 供页面在“忘记设备”时确认文案使用；key 由 {@link DeviceId#key()} 生成。 */
    @NonNull
    static DeviceId idOf(@NonNull String key) {
        int split = key.lastIndexOf('@');
        if (split <= 0 || split == key.length() - 1) {
            throw new IllegalArgumentException("device key is broken: " + key);
        }
        return new DeviceId(key.substring(0, split), key.substring(split + 1));
    }
}
