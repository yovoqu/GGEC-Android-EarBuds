package com.ggec.earbuds.ui.device.settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.DeviceControls;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceSwitch;
import com.ggec.earbuds.domain.device.model.EqPreset;
import com.ggec.earbuds.domain.device.model.GestureAction;
import com.ggec.earbuds.domain.device.model.GestureSlot;
import com.ggec.earbuds.domain.device.model.SoundQuality;
import com.ggec.earbuds.domain.device.policy.DeviceControlPolicy;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.ggec.earbuds.domain.device.usecase.EditDeviceControls;
import com.ggec.earbuds.domain.device.usecase.LoadDeviceProfiles;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.ui.device.DevicePage;

import java.util.List;

/**
 * 设置页状态机：订阅快照与控件，把界面的语义意图翻译成领域操作。
 * View 只提交 SettingId / 索引，不接触任何领域枚举。
 */
public final class DeviceSettingsViewModel extends ViewModel {

    private final DevicePage page;
    private final LoadDeviceProfiles loadProfiles;
    private final ObserveDevice observeDevice;
    private final EditDeviceControls controls;
    private final DeviceSettingsUiMapper mapper;

    private final MutableLiveData<DeviceSettingsUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<Integer> message = new MutableLiveData<>();

    private DeviceSnapshot snapshot = DeviceSnapshot.idle();
    private DeviceControls controlState = DeviceControls.initial();
    private Subscription deviceSubscription;
    private Subscription controlSubscription;

    public DeviceSettingsViewModel(@NonNull DevicePage page,
                                   @NonNull LoadDeviceProfiles loadProfiles,
                                   @NonNull ObserveDevice observeDevice,
                                   @NonNull EditDeviceControls controls,
                                   @NonNull DeviceSettingsUiMapper mapper) {
        this.page = page;
        this.loadProfiles = loadProfiles;
        this.observeDevice = observeDevice;
        this.controls = controls;
        this.mapper = mapper;
    }

    @NonNull
    public LiveData<DeviceSettingsUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<Integer> message() {
        return message;
    }

    @NonNull
    public DevicePage page() {
        return page;
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

    // ---- 音乐与音效 ----

    public void choosePreset(int index) {
        List<EqPreset> presets = DeviceControlPolicy.presets(loadProfiles.selectedProfile());
        if (index < 0 || index >= presets.size()) {
            return;
        }
        EqPreset preset = presets.get(index);
        controls.update(current -> current.withEqPreset(preset));
    }

    public void selectCustomEq() {
        controls.update(current -> current.withEqPreset(EqPreset.CUSTOM));
    }

    public void chooseQuality(int index) {
        List<SoundQuality> qualities = DeviceControlPolicy.qualities();
        if (index < 0 || index >= qualities.size()) {
            return;
        }
        SoundQuality quality = qualities.get(index);
        controls.update(current -> current.withSoundQuality(quality));
    }

    public void saveCustomEq(@NonNull String name, @NonNull float[] gains) {
        controls.update(current -> current.withCustomEq(name, gains));
        message.setValue(R.string.device_toast_eq);
    }

    public void setLdac(boolean enabled) {
        controls.update(current -> current.withSwitch(DeviceSwitch.LDAC, enabled));
    }

    public void setVolumeAdaptive(boolean enabled) {
        controls.update(current -> current.withSwitch(DeviceSwitch.VOLUME_ADAPTIVE, enabled));
    }

    // ---- 其他页的语义意图 ----

    public void onChoice(@NonNull SettingId id, int optionIndex) {
        GestureSlot slot = gestureSlotOf(id);
        if (slot == null) {
            return;
        }
        List<GestureAction> options = DeviceControlPolicy.actions(slot);
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return;
        }
        GestureAction action = options.get(optionIndex);
        controls.update(current -> current.withGesture(slot, action));
    }

    public void onToggle(@NonNull SettingId id, boolean checked) {
        DeviceSwitch key = switchOf(id);
        if (key == null) {
            return;
        }
        controls.update(current -> current.withSwitch(key, checked));
    }

    public void onAction(@NonNull SettingId id) {
        switch (id) {
            case RESET_CONTROL -> {
                controls.update(current -> DeviceControls.initial());
                message.setValue(R.string.device_toast_reset_done);
            }
            case FIND_LEFT, FIND_RIGHT, FIND_STOP -> message.setValue(R.string.device_toast_find);
            case RECORD_START, RECORD_STOP, RECORD_SYNC -> message.setValue(R.string.device_toast_record);
            case CLEAR_PAIRING -> message.setValue(R.string.device_toast_cleared);
            default -> {
            }
        }
    }

    private static GestureSlot gestureSlotOf(SettingId id) {
        for (GestureSlot slot : GestureSlot.values()) {
            if (slot.name().equals(id.name())) {
                return slot;
            }
        }
        return null;
    }

    private static DeviceSwitch switchOf(SettingId id) {
        for (DeviceSwitch key : DeviceSwitch.values()) {
            if (key.name().equals(id.name())) {
                return key;
            }
        }
        return null;
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
        uiState.postValue(mapper.map(page, loadProfiles.selectedProfile(), shot, current));
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
