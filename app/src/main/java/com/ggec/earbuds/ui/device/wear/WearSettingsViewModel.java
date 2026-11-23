package com.ggec.earbuds.ui.device.wear;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.domain.device.usecase.WriteDeviceControl;
import com.ggec.earbuds.ui.device.DeviceCapabilityIds;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.Objects;
import java.util.Optional;

/**
 * 佩戴设置页状态机：订阅连接与 {@code WEAR_DETECT} 状态，写入走 {@link WriteDeviceControl}。
 * 只服务「佩戴检测」一项，独立页承载，不在首页做内联开关。
 */
public final class WearSettingsViewModel extends ViewModel {

    private final ObserveDevice observeDevice;
    private final WriteDeviceControl writeControl;

    private final MutableLiveData<WearSettingsUiState> state =
            new MutableLiveData<>(WearSettingsUiState.idle());
    private final OneShotLiveData<DeviceFailure> failures = new OneShotLiveData<>();

    private DeviceSnapshot snapshot = DeviceSnapshot.idle();
    private Subscription subscription;
    private boolean inFlight;

    public WearSettingsViewModel(@NonNull ObserveDevice observeDevice,
                                 @NonNull WriteDeviceControl writeControl) {
        this.observeDevice = Objects.requireNonNull(observeDevice, "observeDevice");
        this.writeControl = Objects.requireNonNull(writeControl, "writeControl");
    }

    @NonNull
    public LiveData<WearSettingsUiState> state() {
        return state;
    }

    @NonNull
    public LiveData<DeviceFailure> failures() {
        return failures;
    }

    public void start() {
        if (subscription != null) {
            return;
        }
        snapshot = observeDevice.current();
        subscription = observeDevice.execute(next -> {
            snapshot = next;
            publish();
        });
        publish();
    }

    public void toggle(boolean enabled) {
        if (snapshot.connection() != ConnectionState.READY || inFlight) {
            return;
        }
        inFlight = true;
        publish();
        writeControl.execute(DeviceCapabilityIds.WEAR_DETECT, new ControlValue.Toggle(enabled),
                result -> {
                    inFlight = false;
                    if (result instanceof DeviceWriteResult.Failed failed) {
                        failures.postValue(failed.failure());
                    }
                    publish();
                });
    }

    private void publish() {
        boolean ready = snapshot.connection() == ConnectionState.READY;
        boolean checked = snapshot.state().value(DeviceCapabilityIds.WEAR_DETECT)
                .flatMap(WearSettingsViewModel::toggleValue)
                .orElse(false);
        state.postValue(new WearSettingsUiState(ready, checked, ready && !inFlight));
    }

    @NonNull
    private static Optional<Boolean> toggleValue(@NonNull ControlValue value) {
        return value instanceof ControlValue.Toggle toggle
                ? Optional.of(toggle.enabled())
                : Optional.empty();
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
