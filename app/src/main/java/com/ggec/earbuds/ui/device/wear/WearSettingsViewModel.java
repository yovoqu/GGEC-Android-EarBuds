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
    /**
     * 本次写入的用户目标值（点击即先切换、成功后保持）。空表示没有进行中/刚成功的写入，回落到设备上报值。
     * 失败或连接断开时清空。
     */
    private Optional<Boolean> pendingTarget = Optional.empty();

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
            if (next.connection() != ConnectionState.READY) {
                // 连接不在就绪态：丢弃未确认目标，避免把上一台设备的意图带到新会话。
                pendingTarget = Optional.empty();
                inFlight = false;
            }
            publish();
        });
        publish();
    }

    public void toggle(boolean enabled) {
        if (snapshot.connection() != ConnectionState.READY || inFlight) {
            return;
        }
        inFlight = true;
        // 点击即先切换：先呈现目标值，禁用重复点击。
        pendingTarget = Optional.of(enabled);
        publish();
        writeControl.execute(DeviceCapabilityIds.WEAR_DETECT, new ControlValue.Toggle(enabled),
                result -> {
                    inFlight = false;
                    if (result instanceof DeviceWriteResult.Failed failed) {
                        // 失败回退到设备上报值。
                        pendingTarget = Optional.empty();
                        failures.postValue(failed.failure());
                    }
                    // 成功：保留 pendingTarget，开关保持用户目标值（设备上报可能滞后/不回读）。
                    publish();
                });
    }

    private void publish() {
        boolean ready = snapshot.connection() == ConnectionState.READY;
        boolean reported = snapshot.state().value(DeviceCapabilityIds.WEAR_DETECT)
                .flatMap(WearSettingsViewModel::toggleValue)
                .orElse(false);
        // 写入中/成功后以用户目标值为准；否则以设备上报值为准。
        boolean checked = pendingTarget.orElse(reported);
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
