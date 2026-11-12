package com.ggec.earbuds.ui.device.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.usecase.ConnectDevice;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.domain.device.usecase.WriteDeviceControl;
import com.ggec.earbuds.ui.device.DeviceControlOutcome;

/**
 * 设备页签状态机。只消费公共协议：订阅快照、按能力目录取描述、把界面意图交给写入用例。
 * 不调用任何项目 SDK。
 */
public final class DeviceHomeViewModel extends ViewModel {

    private final DeviceCapabilityCatalog catalog;
    private final ObserveDevice observeDevice;
    private final ConnectDevice connectDevice;
    private final WriteDeviceControl writeControl;
    private final DeviceHomeUiMapper mapper;

    private final MutableLiveData<DeviceHomeUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<DeviceFailure> failures = new MutableLiveData<>();
    private final MutableLiveData<DeviceControlOutcome> controls = new MutableLiveData<>();

    private DeviceSnapshot snapshot = DeviceSnapshot.idle();
    private Subscription deviceSubscription;

    public DeviceHomeViewModel(@NonNull DeviceCapabilityCatalog catalog,
                               @NonNull ObserveDevice observeDevice,
                               @NonNull ConnectDevice connectDevice,
                               @NonNull WriteDeviceControl writeControl,
                               @NonNull DeviceHomeUiMapper mapper) {
        this.catalog = catalog;
        this.observeDevice = observeDevice;
        this.connectDevice = connectDevice;
        this.writeControl = writeControl;
        this.mapper = mapper;
    }

    @NonNull
    public LiveData<DeviceHomeUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<DeviceFailure> failures() {
        return failures;
    }

    @NonNull
    public LiveData<DeviceControlOutcome> controls() {
        return controls;
    }

    public void start() {
        if (deviceSubscription != null) {
            return;
        }
        snapshot = observeDevice.current();
        deviceSubscription = observeDevice.execute(this::onSnapshot);
        publish();
    }

    public void setToggle(@NonNull String capabilityId, boolean value) {
        write(capabilityId, new ControlValue.Toggle(value));
    }

    public void selectOption(@NonNull String capabilityId, @NonNull String optionId) {
        write(capabilityId, new ControlValue.Choice(optionId));
    }

    public void disconnect() {
        connectDevice.disconnect();
    }

    private void write(String capabilityId, ControlValue value) {
        if (!isWritable()) {
            return;
        }
        writeControl.execute(capabilityId, value, result -> onWriteResult(capabilityId, result));
    }

    private boolean isWritable() {
        DeviceSnapshot current;
        synchronized (this) {
            current = snapshot;
        }
        return current.connection() == ConnectionState.READY;
    }

    private void onWriteResult(String capabilityId, DeviceWriteResult result) {
        if (result instanceof DeviceWriteResult.Failed failed) {
            failures.postValue(failed.failure());
            controls.postValue(DeviceControlOutcome.failed(capabilityId));
            return;
        }
        controls.postValue(DeviceControlOutcome.ok(capabilityId));
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
        uiState.postValue(mapper.map(profile, current));
    }

    @Override
    protected void onCleared() {
        if (deviceSubscription != null) {
            deviceSubscription.unsubscribe();
            deviceSubscription = null;
        }
        super.onCleared();
    }
}
