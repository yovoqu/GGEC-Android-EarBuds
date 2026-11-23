package com.ggec.earbuds.ui.device.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.usecase.ListDeviceEntries;
import com.ggec.earbuds.domain.device.usecase.ObserveDeviceRoster;
import com.ggec.earbuds.domain.device.usecase.OpenDevice;
import com.ggec.earbuds.domain.device.usecase.WriteDeviceControl;
import com.ggec.earbuds.ui.device.WriteState;
import com.ggec.earbuds.ui.device.roster.DeviceRosterEvent;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

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
    /** 单调写入序号：每次 toggle 生成，随 onStarted/onResult 闭包传递，把回调精确归属到这一次写入。 */
    private final AtomicLong writeSeq = new AtomicLong();
    /** 每个能力当前在途写入的序号（与 {@link #writes} 同锁）；断开连接时一并清空。 */
    private final Map<String, Long> activeSeq = new LinkedHashMap<>();

    private Subscription rosterSubscription;
    private Subscription failureSubscription;
    /** 台账快照：可能由蓝牙线程回调更新、主线程读取，用 volatile 保证可见性。 */
    private volatile DeviceRoster roster = DeviceRoster.empty();

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

    /**
     * 首页内联开关：与设置页一致的写入守卫与语义。
     *
     * <p>该能力已有一次写入在途（SENDING/PROCESSING）时忽略重复下发；否则落 {@code SENDING}
     * （带本次目标值，点击即先切换），链路受理后由 {@code onStarted} 转 {@code PROCESSING}，
     * 终态由 {@code onResult} 落 {@code CONFIRMED}（保留值）或 {@code FAILED}（丢弃 → 回退）。</p>
     */
    public void toggle(@NonNull String capabilityId, boolean enabled) {
        if (!isWritable()) {
            return;
        }
        final long seq;
        synchronized (this) {
            DeviceHomeUiState.WriteSlot existing = writes.get(capabilityId);
            if (existing != null && existing.state().inFlight()) {
                // 同一能力已有一次写入在途：忽略重复下发（双击/无障碍重复触发/程序化重复调用）。
                return;
            }
            seq = writeSeq.incrementAndGet();
            activeSeq.put(capabilityId, seq);
            writes.put(capabilityId, new DeviceHomeUiState.WriteSlot(
                    WriteState.SENDING, Optional.of(enabled)));
        }
        publish();
        writeControl.execute(capabilityId, new ControlValue.Toggle(enabled),
                () -> onStarted(capabilityId, enabled, seq),
                result -> onResult(capabilityId, enabled, seq, result));
    }

    /**
     * 链路写完成 / 设备受理：SENDING → PROCESSING（仍在途，仍禁用点击）。
     * 只认领「本次写入序号 且 槽仍为 SENDING」的信号，避免旧写入/迟到信号覆盖新写入或终态。
     */
    private void onStarted(@NonNull String capabilityId, boolean enabled, long seq) {
        synchronized (this) {
            if (!isActiveSeq(capabilityId, seq)) {
                return;
            }
            DeviceHomeUiState.WriteSlot slot = writes.get(capabilityId);
            if (slot == null || slot.state() != WriteState.SENDING) {
                return;
            }
            writes.put(capabilityId, new DeviceHomeUiState.WriteSlot(
                    WriteState.PROCESSING, Optional.of(enabled)));
        }
        publish();
    }

    /**
     * 写入落地：成功保留本次值（设备不回读时界面仍有确定呈现），失败丢弃本次值并投递事件。
     *
     * <p>守卫：只认领「本次写入序号 且 槽仍在途」的结果；迟到/乱序、掉线清空、被新写入替换的
     * 旧结果一律丢弃。外部事件在锁外投递。</p>
     */
    private void onResult(@NonNull String capabilityId, boolean enabled, long seq,
                          @NonNull DeviceWriteResult result) {
        DeviceFailure failureToPost = null;
        synchronized (this) {
            if (!isActiveSeq(capabilityId, seq)) {
                return;
            }
            DeviceHomeUiState.WriteSlot current = writes.get(capabilityId);
            if (current == null || !current.state().inFlight()) {
                return;
            }
            activeSeq.remove(capabilityId);
            DeviceHomeUiState.WriteSlot slot = switch (result) {
                case DeviceWriteResult.Ok ignored -> new DeviceHomeUiState.WriteSlot(
                        WriteState.CONFIRMED, Optional.of(enabled));
                case DeviceWriteResult.Failed failed -> new DeviceHomeUiState.WriteSlot(
                        WriteState.FAILED, Optional.empty());
            };
            writes.put(capabilityId, slot);
            if (result instanceof DeviceWriteResult.Failed failed) {
                failureToPost = failed.failure();
            }
        }
        if (failureToPost != null) {
            events.postValue(new DeviceRosterEvent.WriteFailed(failureToPost));
        }
        publish();
    }

    /** 该能力的当前在途写入是否就是 seq 这一次。 */
    private boolean isActiveSeq(@NonNull String capabilityId, long seq) {
        Long active = activeSeq.get(capabilityId);
        return active != null && active == seq;
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

    private synchronized Map<String, DeviceHomeUiState.WriteSlot> currentWrites() {
        return Map.copyOf(writes);
    }

    private void onRoster(@NonNull DeviceRoster next) {
        synchronized (this) {
            roster = next;
            boolean ready = next.activeRecord()
                    .map(record -> record.live() && record.status().ready()).orElse(false);
            if (!ready) {
                // 写入事实属于当前连接：断开后旧槽不得在新设备上复活；在途序号一并作废。
                writes.clear();
                activeSeq.clear();
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
