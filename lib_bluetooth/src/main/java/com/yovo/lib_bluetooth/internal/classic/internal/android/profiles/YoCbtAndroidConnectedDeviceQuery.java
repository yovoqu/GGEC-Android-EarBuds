package com.yovo.lib_bluetooth.internal.classic.internal.android.profiles;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.api.CbtInternalOperationHandle;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtConnectedDeviceObserver;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.internal.android.YoCbtAndroidDeviceMapper;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpRuntimeState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetAudioState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateRepository;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateSource;
import com.yovo.lib_bluetooth.internal.classic.runtime.YoCbtRuntime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android 已连接设备查询，聚合各 Profile 代理结果。
 */

public final class YoCbtAndroidConnectedDeviceQuery {
    private static final String TAG = "YoCbtAndroidConnectedDeviceQuery";
    private static final long TIMEOUT_MILLIS = 2_500L;

    private final Context appContext;
    private final BluetoothAdapter adapter;
    private final YoCbtRuntime runtime;
    private final YoCbtAndroidDeviceMapper mapper;
    private final YoCbtDeviceSnapshotStore snapshotStore;
    private final YoCbtProfileStateRepository profileStateRepository;

    public YoCbtAndroidConnectedDeviceQuery(
            @NonNull Context appContext,
            @Nullable BluetoothAdapter adapter,
            @NonNull YoCbtRuntime runtime,
            @NonNull YoCbtAndroidDeviceMapper mapper,
            @NonNull YoCbtDeviceSnapshotStore snapshotStore,
            @NonNull YoCbtProfileStateRepository profileStateRepository) {
        this.appContext = appContext.getApplicationContext();
        this.adapter = adapter;
        this.runtime = runtime;
        this.mapper = mapper;
        this.snapshotStore = snapshotStore;
        this.profileStateRepository = profileStateRepository;
    }

    @NonNull
    public CbtInternalOperationHandle query(@NonNull YoCbtConnectedDeviceObserver observer) {
        QuerySession session = new QuerySession(observer);
        runtime.callback(() -> session.start(BluetoothProfile.A2DP, BluetoothProfile.HEADSET));
        return new CbtInternalOperationHandle("connected-devices-" + System.nanoTime(), session::cancel);
    }

    private final class QuerySession {
        @NonNull private final YoCbtConnectedDeviceObserver observer;
        @NonNull private final AtomicBoolean cancelled = new AtomicBoolean(false);
        @NonNull private final Map<YoCbtDeviceId, YoCbtDeviceSnapshot> snapshots = new LinkedHashMap<>();
        private int[] profiles;
        private int remaining;
        private boolean finished;
        private long observedAtMillis;
        @NonNull private final Map<Integer, Set<YoCbtDeviceId>> connectedByProfile =
                new LinkedHashMap<>();
        @NonNull private final Set<Integer> observedProfiles = new HashSet<>();

        QuerySession(@NonNull YoCbtConnectedDeviceObserver observer) {
            this.observer = observer;
        }

        void start(int... profiles) {
            if (cancelled.get()) return;
            this.profiles = profiles;
            if (adapter == null || !adapter.isEnabled()) {
                finishSuccess();
                return;
            }
            runtime.scheduler().schedule(
                    () -> runtime.callback(this::completeRound), TIMEOUT_MILLIS);
            requestProfiles();
        }

        private void requestProfiles() {
            if (finished || cancelled.get()) return;
            snapshots.clear();
            connectedByProfile.clear();
            observedProfiles.clear();
            observedAtMillis = System.currentTimeMillis();
            remaining = profiles.length;
            for (int profile : profiles) {
                connectedByProfile.put(profile, new HashSet<>());
                requestProfile(profile);
            }
        }

        void cancel() {
            cancelled.set(true);
        }

        private void requestProfile(int profileType) {
            boolean started;
            try {
                started = adapter.getProfileProxy(appContext, new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        try {
                            collect(profile, proxy);
                        } finally {
                            closeProfileProxyQuietly(profile, proxy);
                            completeOne();
                        }
                    }

                    @Override
                    public void onServiceDisconnected(int profile) {
                    }
                }, profileType);
            } catch (SecurityException e) {
                finishFailure(new YoCbtFailure(
                        YoCbtErrorCode.YO_CBT_PROFILE_PROXY_GET_FAILED,
                        "Permission denied while querying connected profile devices",
                        e, false, false, TAG,
                        null, null, null, null,
                        "Grant BLUETOOTH_CONNECT permission"));
                return;
            } catch (Throwable e) {
                finishFailure(YoCbtFailure.of(
                        YoCbtErrorCode.YO_CBT_PROFILE_PROXY_GET_FAILED,
                        "getProfileProxy failed: " + e.getMessage(), TAG));
                return;
            }
            if (!started) {
                completeOne();
            }
        }

        private void collect(int profileType, @Nullable BluetoothProfile proxy) {
            if (proxy == null || finished || cancelled.get()) return;
            List<BluetoothDevice> devices;
            try {
                devices = proxy.getConnectedDevices();
            } catch (Throwable ignored) {
                return;
            }
            observedProfiles.add(profileType);
            if (devices == null || devices.isEmpty()) return;
            for (BluetoothDevice device : devices) {
                YoCbtDeviceIdentity identity = mapper.map(device, null);
                if (identity == null) continue;
                connectedByProfile.get(profileType).add(identity.getDeviceId());
                YoCbtDeviceSnapshot snapshot = persistConnectedProfile(
                        identity, profileType, observedAtMillis);
                snapshots.put(identity.getDeviceId(), snapshot);
            }
        }

        @NonNull
        private YoCbtDeviceSnapshot persistConnectedProfile(
                @NonNull YoCbtDeviceIdentity identity,
                int profileType,
                long now) {
            snapshotStore.updateObserved(identity, Integer.MIN_VALUE, now);
            snapshotStore.updateAcl(identity, YoCbtAclLinkState.CONNECTED, now);

            YoCbtProfileType type = toProfileType(profileType);
            YoCbtDeviceId deviceId = identity.getDeviceId();
            profileStateRepository.applyProfileState(new YoCbtDeviceProfileState(
                    deviceId,
                    type,
                    YoCbtProfileConnectionState.CONNECTED,
                    YoCbtProfileStateSource.ANDROID_PROFILE_PROXY,
                    now));
            if (type == YoCbtProfileType.A2DP) {
                profileStateRepository.applyA2dpState(new YoCbtA2dpDeviceState(
                        deviceId,
                        YoCbtProfileConnectionState.CONNECTED,
                        YoCbtA2dpRuntimeState.PROFILE_CONNECTED,
                        now,
                        YoCbtProfileStateSource.ANDROID_PROFILE_PROXY));
            } else if (type == YoCbtProfileType.HEADSET) {
                profileStateRepository.applyHeadsetState(new YoCbtHeadsetDeviceState(
                        deviceId,
                        YoCbtProfileConnectionState.CONNECTED,
                        YoCbtHeadsetAudioState.UNKNOWN,
                        false,
                        now,
                        YoCbtProfileStateSource.ANDROID_PROFILE_PROXY));
            }
            YoCbtDeviceSnapshot snapshot = snapshotStore.get(deviceId);
            return snapshot != null ? snapshot : snapshotStore.updateObserved(identity, Integer.MIN_VALUE, now);
        }

        private void completeOne() {
            if (finished || cancelled.get()) return;
            remaining--;
            if (remaining <= 0) completeRound();
        }

        private void completeRound() {
            if (finished || cancelled.get()) return;
            for (int profile : profiles) {
                if (!observedProfiles.contains(profile)) continue;
                profileStateRepository.reconcileProfileSnapshot(
                        toProfileType(profile), connectedByProfile.get(profile), observedAtMillis);
            }
            finishSuccess();
        }

        private void finishSuccess() {
            if (finished || cancelled.get()) return;
            finished = true;
            List<YoCbtDeviceSnapshot> result = new ArrayList<>(snapshots.values());
            runtime.callback(() -> {
                if (!cancelled.get()) observer.onConnectedDevices(result);
            });
        }

        private void finishFailure(@NonNull YoCbtFailure failure) {
            if (finished || cancelled.get()) return;
            finished = true;
            runtime.callback(() -> {
                if (!cancelled.get()) observer.onConnectedDeviceQueryFailed(failure);
            });
        }

    }

    @NonNull
    private static YoCbtProfileType toProfileType(int profileType) {
        if (profileType == BluetoothProfile.A2DP) return YoCbtProfileType.A2DP;
        return YoCbtProfileType.HEADSET;
    }

    private void closeProfileProxyQuietly(int profile, @Nullable BluetoothProfile proxy) {
        if (adapter == null || proxy == null) return;
        try {
            adapter.closeProfileProxy(profile, proxy);
        } catch (Throwable ignored) {
        }
    }
}
