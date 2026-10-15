package com.yovo.lib_bluetooth.internal.classic.profiles.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpProfileController;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpRuntimeState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetAudioState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetProfileController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Profile 状态存储与查询仓库，维护各设备 Profile 状态并通知观察者。
 */

public final class YoCbtProfileStateRepository {

    private static final String TAG = "YoCbtProfileStateRepository";

    private final YoCbtDeviceSnapshotStore snapshotStore;
    @Nullable
    private final YoCbtA2dpProfileController a2dpController;
    @Nullable
    private final YoCbtHeadsetProfileController headsetController;

    public YoCbtProfileStateRepository(@NonNull YoCbtDeviceSnapshotStore snapshotStore) {
        this(snapshotStore, null, null);
    }

    public YoCbtProfileStateRepository(
            @NonNull YoCbtDeviceSnapshotStore snapshotStore,
            @Nullable YoCbtA2dpProfileController a2dpController,
            @Nullable YoCbtHeadsetProfileController headsetController) {
        this.snapshotStore = snapshotStore;
        this.a2dpController = a2dpController;
        this.headsetController = headsetController;
    }

    @Nullable
    public YoCbtDeviceSnapshot getDeviceSnapshot(@NonNull YoCbtDeviceId deviceId) {
        return snapshotStore.get(deviceId);
    }

    public void ensureDeviceObserved(@NonNull YoCbtDeviceIdentity identity, long timestampMillis) {
        if (snapshotStore.get(identity.getDeviceId()) == null) {
            snapshotStore.updateObserved(identity, Integer.MIN_VALUE, timestampMillis);
        }
    }

    @Nullable
    public YoCbtDeviceProfileState applyProfileState(@NonNull YoCbtDeviceProfileState state) {
        YoCbtDeviceSnapshot current = snapshotStore.get(state.getDeviceId());
        if (current != null) {
            YoCbtDeviceProfileState previous =
                    current.getProfileStates().get(state.getProfileType());
            if (previous != null
                    && previous.getLastChangedAtMillis() > state.getLastChangedAtMillis()) {
                return null;
            }
        }
        YoCbtDeviceSnapshot snapshot = snapshotStore.updateProfile(state);
        return snapshot != null ? state : null;
    }

    @Nullable
    public YoCbtA2dpDeviceState applyA2dpState(@NonNull YoCbtA2dpDeviceState state) {
        YoCbtDeviceSnapshot current = snapshotStore.get(state.getDeviceId());
        if (current != null && current.getA2dpState() != null
                && current.getA2dpState().getLastChangedAtMillis()
                > state.getLastChangedAtMillis()) {
            return null;
        }
        YoCbtDeviceSnapshot snapshot = snapshotStore.updateA2dp(state);
        return snapshot != null ? state : null;
    }

    @Nullable
    public YoCbtHeadsetDeviceState applyHeadsetState(@NonNull YoCbtHeadsetDeviceState state) {
        YoCbtDeviceSnapshot current = snapshotStore.get(state.getDeviceId());
        if (current != null && current.getHeadsetState() != null
                && current.getHeadsetState().getLastChangedAtMillis()
                > state.getLastChangedAtMillis()) {
            return null;
        }
        YoCbtDeviceSnapshot snapshot = snapshotStore.updateHeadset(state);
        return snapshot != null ? state : null;
    }

    public void persistTypedProfileState(@NonNull YoCbtDeviceId deviceId,
                                         @NonNull YoCbtProfileType type,
                                         @NonNull YoCbtProfileConnectionState connectionState,
                                         long timestampMillis) {
        if (type == YoCbtProfileType.A2DP) {
            if (connectionState == YoCbtProfileConnectionState.DISCONNECTED) {
                applyA2dpState(new YoCbtA2dpDeviceState(
                        deviceId, connectionState,
                        YoCbtA2dpRuntimeState.PROFILE_DISCONNECTED,
                        timestampMillis,
                        YoCbtProfileStateSource.ANDROID_PROFILE_PROXY));
                return;
            }
            persistA2dpState(deviceId, connectionState, timestampMillis);
        } else if (isHeadsetFamily(type)) {
            if (connectionState == YoCbtProfileConnectionState.DISCONNECTED) {
                applyHeadsetState(new YoCbtHeadsetDeviceState(
                        deviceId, connectionState,
                        YoCbtHeadsetAudioState.UNKNOWN,
                        false,
                        timestampMillis,
                        YoCbtProfileStateSource.ANDROID_PROFILE_PROXY));
                return;
            }
            persistHeadsetState(deviceId, connectionState, timestampMillis);
        }
    }

    @NonNull
    public List<YoCbtDeviceProfileState> refreshOnAclDisconnected(
            @NonNull YoCbtDeviceId deviceId) {
        List<YoCbtDeviceProfileState> changedStates = new ArrayList<>();
        YoCbtDeviceSnapshot snapshot = snapshotStore.get(deviceId);
        if (snapshot == null) return changedStates;

        long now = System.currentTimeMillis();
        for (Map.Entry<YoCbtProfileType, YoCbtDeviceProfileState> entry
                : snapshot.getProfileStates().entrySet()) {
            YoCbtProfileConnectionState current = entry.getValue().getConnectionState();
            if (current == YoCbtProfileConnectionState.CONNECTED
                    || current == YoCbtProfileConnectionState.CONNECTING
                    || current == YoCbtProfileConnectionState.DISCONNECTING) {
                YoCbtDeviceProfileState disconnected = new YoCbtDeviceProfileState(
                        deviceId,
                        entry.getKey(),
                        YoCbtProfileConnectionState.DISCONNECTED,
                        YoCbtProfileStateSource.ACL_BROADCAST_INFERRED,
                        now);
                if (applyProfileState(disconnected) != null) {
                    changedStates.add(disconnected);
                }
            }
        }

        YoCbtA2dpDeviceState a2dpState = snapshot.getA2dpState();
        if (a2dpState != null
                && a2dpState.getProfileState() != YoCbtProfileConnectionState.DISCONNECTED) {
            applyA2dpState(new YoCbtA2dpDeviceState(
                    deviceId,
                    YoCbtProfileConnectionState.DISCONNECTED,
                    YoCbtA2dpRuntimeState.PROFILE_DISCONNECTED,
                    now,
                    YoCbtProfileStateSource.ACL_BROADCAST_INFERRED));
        }

        YoCbtHeadsetDeviceState headsetState = snapshot.getHeadsetState();
        if (headsetState != null
                && headsetState.getProfileState() != YoCbtProfileConnectionState.DISCONNECTED) {
            applyHeadsetState(new YoCbtHeadsetDeviceState(
                    deviceId,
                    YoCbtProfileConnectionState.DISCONNECTED,
                    YoCbtHeadsetAudioState.AUDIO_DISCONNECTED,
                    false,
                    now,
                    YoCbtProfileStateSource.ACL_BROADCAST_INFERRED));
        }
        return changedStates;
    }

    /**
     * Reconciles a complete Android profile-proxy snapshot with cached per-device state.
     * A device absent from the proxy result is disconnected only when that profile had a
     * previously active state. The supplied timestamp is when the query started, so a newer
     * broadcast cannot be overwritten by a late proxy callback.
     */
    @NonNull
    public List<YoCbtDeviceProfileState> reconcileProfileSnapshot(
            @NonNull YoCbtProfileType profileType,
            @NonNull Set<YoCbtDeviceId> connectedDeviceIds,
            long observedAtMillis) {
        List<YoCbtDeviceProfileState> changedStates = new ArrayList<>();
        for (YoCbtDeviceSnapshot snapshot : snapshotStore.snapshot()) {
            YoCbtDeviceProfileState previous = snapshot.getProfileStates().get(profileType);
            if (previous == null || connectedDeviceIds.contains(snapshot.getDeviceId())) continue;
            YoCbtProfileConnectionState previousState = previous.getConnectionState();
            if (previousState == YoCbtProfileConnectionState.DISCONNECTED
                    || previousState == YoCbtProfileConnectionState.UNKNOWN) continue;
            YoCbtDeviceProfileState disconnected = new YoCbtDeviceProfileState(
                    snapshot.getDeviceId(), profileType,
                    YoCbtProfileConnectionState.DISCONNECTED,
                    YoCbtProfileStateSource.ANDROID_PROFILE_PROXY,
                    observedAtMillis);
            if (applyProfileState(disconnected) == null) continue;
            persistTypedProfileState(snapshot.getDeviceId(), profileType,
                    YoCbtProfileConnectionState.DISCONNECTED, observedAtMillis);
            changedStates.add(disconnected);
        }
        return changedStates;
    }

    @NonNull
    public YoResult<YoCbtDeviceProfileSnapshot> getDeviceProfileSnapshot(
            @NonNull YoCbtDeviceId deviceId) {
        YoCbtDeviceSnapshot snapshot = snapshotStore.get(deviceId);
        if (snapshot == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_STATE_QUERY_FAILED,
                    "Device not found in snapshot store: " + deviceId,
                    TAG));
        }
        return YoResult.success(new YoCbtDeviceProfileSnapshot(snapshot));
    }

    @NonNull
    public YoResult<List<YoCbtProfileType>> getConnectedProfiles(@NonNull YoCbtDeviceId deviceId) {
        YoCbtDeviceSnapshot snapshot = snapshotStore.get(deviceId);
        if (snapshot == null) {
            return YoResult.failure(YoCbtFailure.of(
                    YoCbtErrorCode.YO_CBT_PROFILE_STATE_QUERY_FAILED,
                    "Device not found in snapshot store: " + deviceId,
                    TAG));
        }

        List<YoCbtProfileType> connected = new ArrayList<>();
        for (Map.Entry<YoCbtProfileType, YoCbtDeviceProfileState> entry
                : snapshot.getProfileStates().entrySet()) {
            if (entry.getValue().getConnectionState() == YoCbtProfileConnectionState.CONNECTED) {
                connected.add(entry.getKey());
            }
        }
        return YoResult.success(connected);
    }

    private void persistA2dpState(@NonNull YoCbtDeviceId deviceId,
                                  @NonNull YoCbtProfileConnectionState connectionState,
                                  long timestampMillis) {
        if (a2dpController != null) {
            YoResult<YoCbtA2dpDeviceState> result = a2dpController.getDeviceState(deviceId);
            if (result.isSuccess() && result.getValue() != null) {
                applyA2dpState(result.getValue());
                return;
            }
        }
        applyA2dpState(new YoCbtA2dpDeviceState(
                deviceId,
                connectionState,
                deriveA2dpRuntimeState(connectionState),
                timestampMillis,
                YoCbtProfileStateSource.ANDROID_PROFILE_PROXY));
    }

    private void persistHeadsetState(@NonNull YoCbtDeviceId deviceId,
                                     @NonNull YoCbtProfileConnectionState connectionState,
                                     long timestampMillis) {
        if (headsetController != null) {
            YoResult<YoCbtHeadsetDeviceState> result = headsetController.getDeviceState(deviceId);
            if (result.isSuccess() && result.getValue() != null) {
                applyHeadsetState(result.getValue());
                return;
            }
        }
        applyHeadsetState(new YoCbtHeadsetDeviceState(
                deviceId,
                connectionState,
                YoCbtHeadsetAudioState.UNKNOWN,
                false,
                timestampMillis,
                YoCbtProfileStateSource.ANDROID_PROFILE_PROXY));
    }

    @NonNull
    private static YoCbtA2dpRuntimeState deriveA2dpRuntimeState(
            @NonNull YoCbtProfileConnectionState connectionState) {
        if (connectionState == YoCbtProfileConnectionState.CONNECTED) {
            return YoCbtA2dpRuntimeState.PROFILE_CONNECTED;
        }
        if (connectionState == YoCbtProfileConnectionState.CONNECTING) {
            return YoCbtA2dpRuntimeState.CODEC_UNKNOWN;
        }
        return YoCbtA2dpRuntimeState.PROFILE_DISCONNECTED;
    }

    private static boolean isHeadsetFamily(@NonNull YoCbtProfileType type) {
        return type == YoCbtProfileType.HEADSET;
    }
}
