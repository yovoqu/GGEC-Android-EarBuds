package com.ggec.earbuds.ui.device.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.usecase.ForgetDevice;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.domain.device.usecase.ObserveDeviceRoster;
import com.ggec.earbuds.domain.device.usecase.RunDeviceAction;
import com.ggec.earbuds.domain.device.usecase.WriteDeviceControl;
import com.ggec.earbuds.ui.device.DeviceCapabilityIds;
import com.ggec.earbuds.ui.device.settings.section.SettingsInteractionListener;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 设置页状态机：订阅快照、从能力目录取描述、把界面的类型化意图交给用例。
 *
 * <p>写入只走 {@link WriteDeviceControl}（{@code DevicePort.write}），动作只走
 * {@link RunDeviceAction}（{@code DevicePort.perform}）。本类不拼协议字节、不推导值域、
 * 不吞失败：失败分类经 {@link #events()} 交给宿主播文案。</p>
 *
 * <p><b>写入状态的唯一来源</b>是 {@link #writes}：每次写入先落 {@code SENDING}（带本次值），
 * 链路写完成转 {@code PROCESSING}，端口结果落 {@code CONFIRMED}（保留本次值，设备不回读也能显示）
 * 或 {@code FAILED}（丢弃本次值 → 呈现自动回退到设备上报值）。视图层不再保存任何「未确认」副本。</p>
 */
public final class DeviceSettingsViewModel extends ViewModel implements SettingsInteractionListener {

    private final DeviceCapabilityGroup group;
    private final DeviceCapabilityCatalog catalog;
    private final ObserveDevice observeDevice;
    private final WriteDeviceControl writeControl;
    private final RunDeviceAction runAction;
    private final ForgetDevice forgetDevice;
    private final ObserveDeviceRoster observeRoster;
    private final DeviceSettingsUiMapper mapper;

    private final MutableLiveData<DeviceSettingsUiState> uiState = new MutableLiveData<>();
    private final OneShotLiveData<DeviceSettingsEvent> events = new OneShotLiveData<>();

    /** 本次连接内的写入槽，按能力 id 索引。 */
    private final Map<String, DeviceSettingsUiState.WriteSlot> writes = new LinkedHashMap<>();

    private DeviceSnapshot snapshot = DeviceSnapshot.idle();
    private Subscription subscription;
    private boolean listDataRequested;

    public DeviceSettingsViewModel(@NonNull DeviceCapabilityGroup group,
                                   @NonNull DeviceCapabilityCatalog catalog,
                                   @NonNull ObserveDevice observeDevice,
                                   @NonNull WriteDeviceControl writeControl,
                                   @NonNull RunDeviceAction runAction,
                                   @NonNull ForgetDevice forgetDevice,
                                   @NonNull ObserveDeviceRoster observeRoster,
                                   @NonNull DeviceSettingsUiMapper mapper) {
        this.group = Objects.requireNonNull(group, "group");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.observeDevice = Objects.requireNonNull(observeDevice, "observeDevice");
        this.writeControl = Objects.requireNonNull(writeControl, "writeControl");
        this.runAction = Objects.requireNonNull(runAction, "runAction");
        this.forgetDevice = Objects.requireNonNull(forgetDevice, "forgetDevice");
        this.observeRoster = Objects.requireNonNull(observeRoster, "observeRoster");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @NonNull
    public LiveData<DeviceSettingsUiState> uiState() {
        return uiState;
    }

    /** 写入失败、动作结果与页面级通知：一次性事件，页面消费后立即清空。 */
    @NonNull
    public LiveData<DeviceSettingsEvent> events() {
        return events;
    }

    @NonNull
    public DeviceCapabilityGroup group() {
        return group;
    }

    public void start() {
        if (subscription != null) {
            return;
        }
        snapshot = observeDevice.current();
        subscription = observeDevice.execute(this::onSnapshot);
        publish();
    }

    /** 控件写入：只看连接是否可写，值域与协议全部在域层。 */
    public void write(@NonNull String capabilityId, @NonNull ControlValue value) {
        if (!isWritable()) {
            return;
        }
        // 点击即先切换：立即落 SENDING 并带本次目标值，视图据此先翻到目标位置并禁用重复点击。
        putWrite(capabilityId,
                new DeviceSettingsUiState.WriteSlot(
                        DeviceSettingsUiState.WriteState.SENDING, Optional.of(value)));
        publish();
        writeControl.execute(capabilityId, value,
                () -> onWriteStarted(capabilityId, value),
                result -> onWriteResult(capabilityId, value, result));
    }

    /**
     * 链路写完成 / 设备已受理：SENDING → PROCESSING（仍在途，仍禁用点击）。
     * 只有当前槽仍是「本次值 + SENDING」时才推进，避免旧写入的迟到信号覆盖新写入或终态。
     */
    private void onWriteStarted(@NonNull String capabilityId, @NonNull ControlValue value) {
        synchronized (this) {
            DeviceSettingsUiState.WriteSlot slot = writes.get(capabilityId);
            boolean stillSending = slot != null
                    && slot.state() == DeviceSettingsUiState.WriteState.SENDING
                    && slot.pendingValue().filter(v -> v.equals(value)).isPresent();
            if (!stillSending) {
                return;
            }
            writes.put(capabilityId, new DeviceSettingsUiState.WriteSlot(
                    DeviceSettingsUiState.WriteState.PROCESSING, Optional.of(value)));
        }
        publish();
    }

    /** 类型化动作：重启、清除配对、查找耳机、录音等。 */
    public void run(@NonNull DeviceAction action) {
        if (!isWritable()) {
            return;
        }
        runAction.execute(action, this::onActionResult);
    }

    @Override
    public void onToggle(@NonNull String capabilityId, boolean enabled) {
        write(capabilityId, new ControlValue.Toggle(enabled));
    }

    @Override
    public void onChoice(@NonNull String capabilityId, @NonNull String optionId) {
        write(capabilityId, new ControlValue.Choice(optionId));
    }

    @Override
    public void onLevel(@NonNull String capabilityId, int level) {
        write(capabilityId, new ControlValue.Level(level));
    }

    @Override
    public void onCurve(@NonNull String capabilityId, @NonNull String name,
                        @NonNull List<Integer> points) {
        write(capabilityId, new ControlValue.Curve(name, points));
    }

    /**
     * 行级动作。穷尽 {@link DeviceSettingRow.ActionKind}，没有 {@code default} 空分支：
     *
     * <ul>
     *   <li>无载荷的动作直接路由到对应的 {@link DeviceAction}；</li>
     *   <li>{@code RENAME} / {@code RECORD_LOCAL} / {@code RECORD_STREAM} 必须带载荷
     *       （名称、开始或结束），行级通道无法表达，落成显式
     *       {@link DeviceSettingsEvent.ActionUnsupported}，由宿主提示；载荷版本分别由
     *       {@link #onRename} / {@link #onStartRecordLocal} / {@link #onStopRecordLocal} /
     *       {@link #onStartRecordStream} / {@link #onStopRecordStream} 承担。</li>
     * </ul>
     */
    @Override
    public void onAction(@NonNull DeviceSettingRow.ActionKind kind) {
        switch (kind) {
            case REBOOT -> run(new DeviceAction.Reboot());
            case CLEAR_PAIRING -> run(new DeviceAction.ClearPairing());
            case RESET_CONTROL -> run(new DeviceAction.ResetControl());
            case DEVICE_MANAGEMENT -> forgetActive();
            case FIND_EAR -> run(new DeviceAction.FindEar(DeviceAction.FindEar.Side.BOTH));
            case RECORD_TRANSFER -> transferNewestRecord();
            case RENAME, RECORD_LOCAL, RECORD_STREAM ->
                    events.postValue(new DeviceSettingsEvent.ActionUnsupported(kind));
        }
    }

    @Override
    public void onFindEarLeft() {
        run(new DeviceAction.FindEar(DeviceAction.FindEar.Side.LEFT));
    }

    @Override
    public void onFindEarRight() {
        run(new DeviceAction.FindEar(DeviceAction.FindEar.Side.RIGHT));
    }

    @Override
    public void onStopFindAlert() {
        run(new DeviceAction.StopFindAlert());
    }

    @Override
    public void onRename(@NonNull String newName) {
        run(new DeviceAction.Rename(newName));
    }

    @Override
    public void onReboot() {
        run(new DeviceAction.Reboot());
    }

    @Override
    public void onClearPairing() {
        run(new DeviceAction.ClearPairing());
    }

    @Override
    public void onResetControl() {
        run(new DeviceAction.ResetControl());
    }

    @Override
    public void onStartRecordLocal() {
        run(new DeviceAction.StartRecord(DeviceAction.RecordMode.LOCAL));
    }

    @Override
    public void onStopRecordLocal() {
        run(new DeviceAction.StopRecord(DeviceAction.RecordMode.LOCAL));
    }

    @Override
    public void onStartRecordStream() {
        run(new DeviceAction.StartRecord(DeviceAction.RecordMode.STREAM));
    }

    @Override
    public void onStopRecordStream() {
        run(new DeviceAction.StopRecord(DeviceAction.RecordMode.STREAM));
    }

    @Override
    public void onFileTransfer(@NonNull String fileId) {
        run(new DeviceAction.TransferRecord(fileId, true));
    }

    @Override
    public void onForgetActive() {
        forgetActive();
    }

    @Override
    public void onConnectPairedDevice(@NonNull String address) {
        run(new DeviceAction.ConnectPairedDevice(address));
    }

    @Override
    public void onDisconnectPairedDevice(@NonNull String address) {
        run(new DeviceAction.DisconnectPairedDevice(address));
    }

    @Override
    public void onSetPriorityPairedDevice(@NonNull String address) {
        run(new DeviceAction.SetPriorityPairedDevice(address));
    }

    @Override
    public void onForgetPairedDevice(@NonNull String address) {
        run(new DeviceAction.ForgetPairedDevice(address));
    }

    /** 设备管理：忘记设备（先断开活动会话、再从台账移除）；成功后由宿主关闭本页。 */
    public void forgetActive() {
        DeviceId id = activeId();
        if (id == null) {
            events.postValue(new DeviceSettingsEvent.RosterMissing());
            return;
        }
        if (!forgetDevice.execute(id)) {
            events.postValue(new DeviceSettingsEvent.RosterMissing());
            return;
        }
        events.postValue(new DeviceSettingsEvent.Closed());
    }

    @Nullable
    private DeviceId activeId() {
        return observeRoster.current().activeId().orElse(null);
    }

    private boolean isWritable() {
        return currentSnapshot().connection() == ConnectionState.READY;
    }

    /** 行级「同步最后一条」：载荷取当前快照里的第一条录音；没有录音时显式拒绝，不发空指令。 */
    private void transferNewestRecord() {
        List<RecordFileView> files = currentSnapshot().recordFiles();
        if (files.isEmpty()) {
            events.postValue(
                    new DeviceSettingsEvent.ActionUnsupported(
                            DeviceSettingRow.ActionKind.RECORD_TRANSFER));
            return;
        }
        run(new DeviceAction.TransferRecord(files.get(0).fileId(), true));
    }

    /**
     * 写入落地：成功保留本次值（设备不回读时界面仍有确定呈现），失败丢弃本次值。
     *
     * <p>守卫：只有当前槽仍属于「本次 value 且在途（SENDING/PROCESSING）」时才落终态，
     * 迟到结果、掉线清空后的旧结果、已被新写入替换的旧结果一律丢弃，避免污染当前槽。</p>
     */
    private void onWriteResult(@NonNull String capabilityId, @NonNull ControlValue value,
                               @NonNull DeviceWriteResult result) {
        synchronized (this) {
            DeviceSettingsUiState.WriteSlot current = writes.get(capabilityId);
            boolean stillPending = current != null
                    && current.state().inFlight()
                    && current.pendingValue().filter(v -> v.equals(value)).isPresent();
            if (!stillPending) {
                return;
            }
            DeviceSettingsUiState.WriteSlot slot = switch (result) {
                case DeviceWriteResult.Ok ignored -> new DeviceSettingsUiState.WriteSlot(
                        DeviceSettingsUiState.WriteState.CONFIRMED, Optional.of(value));
                case DeviceWriteResult.Failed failed -> new DeviceSettingsUiState.WriteSlot(
                        DeviceSettingsUiState.WriteState.FAILED, Optional.empty());
            };
            writes.put(capabilityId, slot);
            if (result instanceof DeviceWriteResult.Failed failed) {
                events.postValue(new DeviceSettingsEvent.Failure(failed.failure()));
            }
        }
        publish();
    }

    private void onActionResult(@NonNull DeviceWriteResult result) {
        switch (result) {
            case DeviceWriteResult.Ok ignored -> {
            }
            case DeviceWriteResult.Failed failed ->
                    events.postValue(new DeviceSettingsEvent.Failure(failed.failure()));
        }
    }

    private synchronized void putWrite(@NonNull String capabilityId,
                                       @NonNull DeviceSettingsUiState.WriteSlot slot) {
        writes.put(capabilityId, slot);
    }

    private synchronized Map<String, DeviceSettingsUiState.WriteSlot> currentWrites() {
        return Map.copyOf(writes);
    }

    private synchronized DeviceSnapshot currentSnapshot() {
        return snapshot;
    }

    private void onSnapshot(DeviceSnapshot next) {
        synchronized (this) {
            snapshot = next;
            if (next.connection() != ConnectionState.READY) {
                // 写入事实属于当前连接：连接不在就绪态时，旧槽不得在新设备上复活。
                writes.clear();
            }
        }
        publish();
    }

    private void publish() {
        DeviceSnapshot current = currentSnapshot();
        Optional<DeviceProfileView> profile = current.modelId() == null
                ? Optional.empty()
                : catalog.model(current.modelId());
        uiState.postValue(mapper.map(group, profile, current, currentWrites()));
        requestListData(profile, current);
    }

    /**
     * 列表类组件的数据不在能力状态里：进页（连接就绪后）主动读一次，
     * 之后随设备事件更新快照。每次连接只读一次。
     */
    private void requestListData(@NonNull Optional<DeviceProfileView> profile,
                                 @NonNull DeviceSnapshot current) {
        if (profile.isEmpty() || current.connection() != ConnectionState.READY) {
            listDataRequested = false;
            return;
        }
        if (listDataRequested) {
            return;
        }
        listDataRequested = true;
        switch (group) {
            case DUAL_CONNECTION -> {
                if (profile.get().supports(DeviceCapabilityIds.DUAL_CONNECTION_DEVICES)) {
                    runAction.execute(new DeviceAction.ReadPairedDevices(),
                            this::onActionResult);
                }
            }
            case RECORD -> {
                if (profile.get().supports(DeviceCapabilityIds.RECORD_LIST)) {
                    runAction.execute(new DeviceAction.ReadRecordList(), this::onActionResult);
                }
            }
            case MUSIC, CONTROL, FUNCTION, ABOUT -> {
            }
        }
    }

    @Override
    protected void onCleared() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
        super.onCleared();
    }
}
