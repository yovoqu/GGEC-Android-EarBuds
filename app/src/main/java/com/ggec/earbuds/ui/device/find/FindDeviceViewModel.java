package com.ggec.earbuds.ui.device.find;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.domain.device.usecase.ObserveDeviceRoster;
import com.ggec.earbuds.domain.device.usecase.RunDeviceAction;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.Objects;
import java.util.Optional;

/**
 * 查找设备页状态机：订阅连接与台账，把「左耳响铃 / 右耳响铃 / 停止响铃」交给 {@link RunDeviceAction}。
 *
 * <p>只做一件事：读当前设备名与可写状态，转发 {@link DeviceAction.FindEar} /
 * {@link DeviceAction.StopFindAlert}。失败经 {@link #failures()} 交宿主提示，不在页面吞掉。</p>
 */
public final class FindDeviceViewModel extends ViewModel {

    private final ObserveDevice observeDevice;
    private final ObserveDeviceRoster observeRoster;
    private final RunDeviceAction runAction;

    private final MutableLiveData<FindDeviceUiState> state =
            new MutableLiveData<>(FindDeviceUiState.idle());
    private final OneShotLiveData<DeviceFailure> failures = new OneShotLiveData<>();

    private DeviceSnapshot snapshot = DeviceSnapshot.idle();
    private Subscription deviceSubscription;
    private Subscription rosterSubscription;

    public FindDeviceViewModel(@NonNull ObserveDevice observeDevice,
                               @NonNull ObserveDeviceRoster observeRoster,
                               @NonNull RunDeviceAction runAction) {
        this.observeDevice = Objects.requireNonNull(observeDevice, "observeDevice");
        this.observeRoster = Objects.requireNonNull(observeRoster, "observeRoster");
        this.runAction = Objects.requireNonNull(runAction, "runAction");
    }

    @NonNull
    public LiveData<FindDeviceUiState> state() {
        return state;
    }

    @NonNull
    public LiveData<DeviceFailure> failures() {
        return failures;
    }

    public void start() {
        if (deviceSubscription != null) {
            return;
        }
        snapshot = observeDevice.current();
        deviceSubscription = observeDevice.execute(next -> {
            snapshot = next;
            publish();
        });
        rosterSubscription = observeRoster.execute(roster -> publish());
        publish();
    }

    public void ringLeft() {
        ring(DeviceAction.FindEar.Side.LEFT);
    }

    public void ringRight() {
        ring(DeviceAction.FindEar.Side.RIGHT);
    }

    public void stop() {
        run(new DeviceAction.StopFindAlert());
    }

    private void ring(DeviceAction.FindEar.Side side) {
        run(new DeviceAction.FindEar(side));
    }

    private void run(@NonNull DeviceAction action) {
        if (snapshot.connection() != ConnectionState.READY) {
            return;
        }
        runAction.execute(action, result -> {
            if (result instanceof DeviceWriteResult.Failed failed) {
                failures.postValue(failed.failure());
            }
        });
    }

    private void publish() {
        boolean interactive = snapshot.connection() == ConnectionState.READY;
        state.postValue(new FindDeviceUiState(activeName(), interactive));
    }

    @NonNull
    private String activeName() {
        DeviceRoster roster = observeRoster.current();
        Optional<DeviceRecord> record = roster.activeId().flatMap(roster::find);
        return record.map(FindDeviceViewModel::nameOf).orElse("");
    }

    @NonNull
    private static String nameOf(@NonNull DeviceRecord record) {
        String classic = record.identity().classicName();
        if (!classic.isEmpty()) {
            return classic;
        }
        String ble = record.identity().bleName();
        return ble.isEmpty() ? record.displayName() : ble;
    }

    @Override
    protected void onCleared() {
        if (deviceSubscription != null) {
            deviceSubscription.unsubscribe();
            deviceSubscription = null;
        }
        if (rosterSubscription != null) {
            rosterSubscription.unsubscribe();
            rosterSubscription = null;
        }
        super.onCleared();
    }
}
