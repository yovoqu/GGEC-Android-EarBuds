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
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.domain.device.usecase.RunDeviceAction;
import com.ggec.earbuds.domain.device.usecase.WriteDeviceControl;
import com.ggec.earbuds.ui.device.DeviceCapabilityIds;
import com.ggec.earbuds.ui.device.DeviceControlOutcome;

/**
 * 设置页状态机：订阅快照、从能力目录取描述、把界面的类型化意图交给用例。
 *
 * <p>写入只走 {@link WriteDeviceControl}（{@code DevicePort.write}），动作只走
 * {@link RunDeviceAction}（{@code DevicePort.perform}）。本类不拼协议字节、不推导值域、
 * 不吞失败：失败分类经 {@link #failures()} 交给宿主播文案。</p>
 */
public final class DeviceSettingsViewModel extends ViewModel {

    private final DeviceCapabilityGroup group;
    private final DeviceCapabilityCatalog catalog;
    private final ObserveDevice observeDevice;
    private final WriteDeviceControl writeControl;
    private final RunDeviceAction runAction;
    private final DeviceSettingsUiMapper mapper;

    private final MutableLiveData<DeviceSettingsUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<DeviceFailure> failures = new MutableLiveData<>();
    private final MutableLiveData<DeviceControlOutcome> controls = new MutableLiveData<>();

    private DeviceSnapshot snapshot = DeviceSnapshot.idle();
    private Subscription subscription;
    private boolean listDataRequested;

    public DeviceSettingsViewModel(@NonNull DeviceCapabilityGroup group,
                                   @NonNull DeviceCapabilityCatalog catalog,
                                   @NonNull ObserveDevice observeDevice,
                                   @NonNull WriteDeviceControl writeControl,
                                   @NonNull RunDeviceAction runAction,
                                   @NonNull DeviceSettingsUiMapper mapper) {
        this.group = group;
        this.catalog = catalog;
        this.observeDevice = observeDevice;
        this.writeControl = writeControl;
        this.runAction = runAction;
        this.mapper = mapper;
    }

    @NonNull
    public LiveData<DeviceSettingsUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<DeviceFailure> failures() {
        return failures;
    }

    /** 写入落地结果（按能力 id）：视图据此丢弃「未确认」呈现，不假装成功。 */
    @NonNull
    public LiveData<DeviceControlOutcome> controls() {
        return controls;
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
        writeControl.execute(capabilityId, value, result -> onResult(capabilityId, result));
    }

    /** 类型化动作：重启、清除配对、查找耳机、录音等。 */
    public void run(@NonNull DeviceAction action) {
        if (!isWritable()) {
            return;
        }
        runAction.execute(action, result -> onResult(null, result));
    }

    private boolean isWritable() {
        DeviceSnapshot current;
        synchronized (this) {
            current = snapshot;
        }
        return current.connection() == ConnectionState.READY;
    }

    private void onResult(@Nullable String capabilityId, DeviceWriteResult result) {
        if (result instanceof DeviceWriteResult.Failed failed) {
            failures.postValue(failed.failure());
            if (capabilityId != null) {
                controls.postValue(DeviceControlOutcome.failed(capabilityId));
            }
            return;
        }
        if (capabilityId != null) {
            controls.postValue(DeviceControlOutcome.ok(capabilityId));
        }
    }

    private void onSnapshot(DeviceSnapshot next) {
        synchronized (this) {
            snapshot = next;
        }
        publish();
    }

    private void publish() {
        DeviceSnapshot current;
        synchronized (this) {
            current = snapshot;
        }
        DeviceProfileView profile = current.modelId() == null ? null
                : catalog.model(current.modelId()).orElse(null);
        uiState.postValue(mapper.map(group, profile, current));
        requestListData(profile, current);
    }

    /**
     * 列表类组件的数据不在能力状态里：进页（连接就绪后）主动读一次，
     * 之后随设备事件更新快照。每次连接只读一次。
     */
    private void requestListData(DeviceProfileView profile, DeviceSnapshot current) {
        if (profile == null || current.connection() != ConnectionState.READY) {
            listDataRequested = false;
            return;
        }
        if (listDataRequested) {
            return;
        }
        listDataRequested = true;
        switch (group) {
            case DUAL_CONNECTION -> {
                if (profile.supports(DeviceCapabilityIds.DUAL_CONNECTION_DEVICES)) {
                    runAction.execute(new DeviceAction.ReadPairedDevices(), result -> onResult(null, result));
                }
            }
            case RECORD -> {
                if (profile.supports(DeviceCapabilityIds.RECORD_LIST)) {
                    runAction.execute(new DeviceAction.ReadRecordList(), result -> onResult(null, result));
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
