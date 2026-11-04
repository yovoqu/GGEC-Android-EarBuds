package com.ggec.earbuds.ui.device.home;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.model.DeviceModel;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceSwitch;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.usecase.ConnectDevice;
import com.ggec.earbuds.domain.device.usecase.EditDeviceControls;
import com.ggec.earbuds.domain.device.usecase.LoadDeviceProfiles;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;

import java.util.ArrayList;
import java.util.List;

/** 设备页签：订阅设备与控件，发布一份界面状态。 */
public final class DeviceHomeViewModel extends ViewModel {

    private final LoadDeviceProfiles loadProfiles;
    private final ObserveDevice observeDevice;
    private final ConnectDevice connectDevice;
    private final EditDeviceControls controls;
    private final DeviceHomeUiMapper mapper;

    private final MutableLiveData<DeviceHomeUiState> uiState = new MutableLiveData<>();

    private DeviceSnapshot snapshot = DeviceSnapshot.idle();
    private DeviceControls controlState = DeviceControls.initial();
    private Subscription deviceSubscription;
    private Subscription controlSubscription;

    public DeviceHomeViewModel(@NonNull LoadDeviceProfiles loadProfiles,
                               @NonNull ObserveDevice observeDevice,
                               @NonNull ConnectDevice connectDevice,
                               @NonNull EditDeviceControls controls,
                               @NonNull DeviceHomeUiMapper mapper) {
        this.loadProfiles = loadProfiles;
        this.observeDevice = observeDevice;
        this.connectDevice = connectDevice;
        this.controls = controls;
        this.mapper = mapper;
    }

    @NonNull
    public LiveData<DeviceHomeUiState> uiState() {
        return uiState;
    }

    public void start() {
        if (deviceSubscription != null) {
            return;
        }
        snapshot = observeDevice.current();
        controlState = controls.current();
        deviceSubscription = observeDevice.execute(this::onSnapshot);
        controlSubscription = controls.observe(this::onControls);
    }

    public void refresh() {
        synchronized (this) {
            snapshot = observeDevice.current();
            controlState = controls.current();
        }
        publish();
    }

    public void selectModel(@NonNull String modelId) {
        loadProfiles.selectModel(modelId);
        refresh();
    }

    @NonNull
    public List<String> modelIds() {
        List<String> ids = new ArrayList<>();
        for (DeviceModel model : loadProfiles.models()) {
            ids.add(model.id());
        }
        return ids;
    }

    public void setToggle(@NonNull HomeToggle key, boolean value) {
        controls.update(current -> current.withSwitch(DeviceSwitch.valueOf(key.name()), value));
    }

    public void disconnect() {
        connectDevice.disconnect();
    }

    private void onSnapshot(DeviceSnapshot next) {
        synchronized (this) {
            snapshot = next;
        }
        publish();
    }

    private void onControls(DeviceControls next) {
        synchronized (this) {
            controlState = next;
        }
        publish();
    }

    private void publish() {
        DeviceSnapshot shot;
        DeviceControls current;
        synchronized (this) {
            shot = snapshot;
            current = controlState;
        }
        uiState.postValue(mapper.map(loadProfiles.selectedProfile(), shot, current));
    }

    @Override
    protected void onCleared() {
        if (deviceSubscription != null) {
            deviceSubscription.unsubscribe();
            deviceSubscription = null;
        }
        if (controlSubscription != null) {
            controlSubscription.unsubscribe();
            controlSubscription = null;
        }
        super.onCleared();
    }
}
