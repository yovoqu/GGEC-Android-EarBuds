package com.yovo.lib_bluetooth.internal.classic.profiles.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpProfileController;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpRuntimeState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetAudioState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetProfileController;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtScoAudioStateTracker;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtEventHub;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtEvents;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;

import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 将 Android 系统 Profile 广播映射并应用到 Profile 状态仓库。
 */

public final class YoCbtProfileBroadcastApplier {

    private static final String TAG = "YoCbtProfileBroadcastApplier";

    private final YoCbtProfileStateRepository stateRepository;
    @Nullable
    private final YoCbtA2dpProfileController a2dpController;
    @Nullable
    private final YoCbtHeadsetProfileController headsetController;
    private final YoCbtScoAudioStateTracker scoAudioStateTracker;
    private final YoCbtEventHub eventHub;
    private final YoCbtRuntime runtime;

    public YoCbtProfileBroadcastApplier(
            @NonNull YoCbtProfileStateRepository stateRepository,
            @Nullable YoCbtA2dpProfileController a2dpController,
            @Nullable YoCbtHeadsetProfileController headsetController,
            @NonNull YoCbtScoAudioStateTracker scoAudioStateTracker,
            @NonNull YoCbtEventHub eventHub,
            @NonNull YoCbtRuntime runtime) {
        this.stateRepository = stateRepository;
        this.a2dpController = a2dpController;
        this.headsetController = headsetController;
        this.scoAudioStateTracker = scoAudioStateTracker;
        this.eventHub = eventHub;
        this.runtime = runtime;
    }

    public void applyA2dpConnection(@NonNull YoCbtDeviceIdentity identity,
                                    @NonNull YoCbtProfileConnectionState connectionState,
                                    @NonNull YoCbtA2dpRuntimeState runtimeState,
                                    @NonNull List<YoCbtProfileStateObserver> observers) {
        long now = System.currentTimeMillis();
        YoCbtDeviceId deviceId = ensureDevice(identity, now);

        YoCbtDeviceProfileState profileState = new YoCbtDeviceProfileState(
                deviceId,
                YoCbtProfileType.A2DP,
                connectionState,
                YoCbtProfileStateSource.PROFILE_BROADCAST,
                now);
        if (stateRepository.applyProfileState(profileState) == null) return;

        YoCbtA2dpDeviceState a2dpState = new YoCbtA2dpDeviceState(
                deviceId,
                connectionState,
                runtimeState,
                now,
                YoCbtProfileStateSource.PROFILE_BROADCAST);
        if (stateRepository.applyA2dpState(a2dpState) == null) return;

        if (a2dpController != null) {
            a2dpController.notifyStateChanged(a2dpState);
        }
        notifyObservers(observers, profileState);
    }

    public void applyHeadsetConnection(@NonNull YoCbtDeviceIdentity identity,
                                       @NonNull YoCbtProfileConnectionState connectionState,
                                       @NonNull List<YoCbtProfileStateObserver> observers) {
        long now = System.currentTimeMillis();
        YoCbtDeviceId deviceId = ensureDevice(identity, now);

        YoCbtDeviceProfileState profileState = new YoCbtDeviceProfileState(
                deviceId,
                YoCbtProfileType.HEADSET,
                connectionState,
                YoCbtProfileStateSource.PROFILE_BROADCAST,
                now);
        if (stateRepository.applyProfileState(profileState) == null) return;

        YoCbtHeadsetDeviceState headsetState = new YoCbtHeadsetDeviceState(
                deviceId,
                connectionState,
                YoCbtHeadsetAudioState.UNKNOWN,
                false,
                now,
                YoCbtProfileStateSource.PROFILE_BROADCAST);
        if (stateRepository.applyHeadsetState(headsetState) == null) return;

        if (headsetController != null) {
            headsetController.notifyStateChanged(headsetState);
        }
        notifyObservers(observers, profileState);
    }

    public void applyHeadsetAudio(@NonNull YoCbtDeviceIdentity identity,
                                 @NonNull YoCbtHeadsetAudioState audioState,
                                 @NonNull List<YoCbtProfileStateObserver> observers) {
        long now = System.currentTimeMillis();
        YoCbtDeviceId deviceId = ensureDevice(identity, now);
        YoCbtProfileConnectionState profileConnectionState = resolveHeadsetConnectionState(deviceId);

        scoAudioStateTracker.recordAudioState(deviceId, audioState);
        YoCbtHeadsetDeviceState headsetState = new YoCbtHeadsetDeviceState(
                deviceId,
                profileConnectionState,
                audioState,
                false,
                now,
                YoCbtProfileStateSource.PROFILE_BROADCAST);
        if (stateRepository.applyHeadsetState(headsetState) == null) return;

        YoCbtDeviceProfileState profileState = new YoCbtDeviceProfileState(
                deviceId,
                YoCbtProfileType.HEADSET,
                profileConnectionState,
                YoCbtProfileStateSource.PROFILE_BROADCAST,
                now);
        stateRepository.applyProfileState(profileState);

        if (headsetController != null) {
            headsetController.notifyStateChanged(headsetState);
        }
        notifyObservers(observers, profileState);
    }

    @NonNull
    private YoCbtDeviceId ensureDevice(@NonNull YoCbtDeviceIdentity identity, long timestampMillis) {
        stateRepository.ensureDeviceObserved(identity, timestampMillis);
        return identity.getDeviceId();
    }

    @NonNull
    private YoCbtProfileConnectionState resolveHeadsetConnectionState(@NonNull YoCbtDeviceId deviceId) {
        YoCbtDeviceSnapshot existing = stateRepository.getDeviceSnapshot(deviceId);
        if (existing != null) {
            YoCbtDeviceProfileState profileState =
                    existing.getProfileStates().get(YoCbtProfileType.HEADSET);
            if (profileState != null) {
                return profileState.getConnectionState();
            }
            if (existing.getHeadsetState() != null) {
                return existing.getHeadsetState().getProfileState();
            }
        }
        return YoCbtProfileConnectionState.CONNECTED;
    }

    private void notifyObservers(@NonNull List<YoCbtProfileStateObserver> observers,
                                 @NonNull YoCbtDeviceProfileState state) {
        eventHub.publish(YoCbtEvents.PROFILE_STATE_CHANGED, state);
        for (YoCbtProfileStateObserver observer : observers) {
            runtime.callback(() -> {
                try {
                    observer.onProfileStateChanged(state);
                } catch (Exception e) {
                    YoBluetoothLogger.e(TAG, "观察者抛出异常", e);
                }
            });
        }
    }
}
