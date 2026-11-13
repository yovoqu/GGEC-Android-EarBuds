package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.internal.center.port.HsClassicLink;
import com.yovo.lib_bluetooth.YoBluetoothSDK;
import com.yovo.lib_bluetooth.api.YoCbtClient;
import com.yovo.lib_bluetooth.model.classic.CbtBondState;
import com.yovo.lib_bluetooth.model.classic.CbtBondStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtConnectedDeviceObserver;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceId;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceProfileState;
import com.yovo.lib_bluetooth.model.classic.CbtDeviceSnapshot;
import com.yovo.lib_bluetooth.model.classic.CbtFailure;
import com.yovo.lib_bluetooth.model.classic.CbtOperationHandle;
import com.yovo.lib_bluetooth.model.classic.CbtProfileConnectionState;
import com.yovo.lib_bluetooth.model.classic.CbtProfileStateObserver;
import com.yovo.lib_bluetooth.model.classic.CbtProfileType;
import com.yovo.lib_bluetooth.model.common.Result;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 把 {@link YoCbtClient} 收成配对事实、音频事实，以及配对和音频连接两个动作。
 * 下一步由中层决定。
 */
public final class YoClassicRadio implements HsClassicLink {

    private final YoCbtClient client;
    private final Object lock = new Object();

    private int generation;
    private boolean released;
    private boolean audioSettled;
    @Nullable
    private String address;
    @Nullable
    private CbtBondState observedBond;
    @Nullable
    private Listener listener;
    @Nullable
    private CbtOperationHandle bonds;
    @Nullable
    private CbtOperationHandle profiles;
    @Nullable
    private CbtOperationHandle query;

    public YoClassicRadio(@NonNull YoCbtClient client) {
        this.client = client;
    }

    @NonNull
    public static YoClassicRadio open(@NonNull Context context) {
        Context application = context.getApplicationContext();
        Context target = application == null ? context : application;
        return new YoClassicRadio(YoBluetoothSDK.classic(target));
    }

    @Override
    public void watch(@NonNull String classicAddress, @NonNull Listener listener) {
        Objects.requireNonNull(classicAddress, "classicAddress");
        Objects.requireNonNull(listener, "listener");
        unwatch();
        int current;
        synchronized (lock) {
            if (released) {
                throw new IllegalStateException("经典蓝牙链路已释放");
            }
            current = generation;
            address = classicAddress.trim().toUpperCase(Locale.US);
            this.listener = listener;
            observedBond = null;
            audioSettled = false;
        }
        client.prepareProfileProxy(CbtProfileType.A2DP);
        client.prepareProfileProxy(CbtProfileType.HEADSET);
        CbtOperationHandle bondHandle = client.observeBondState(new CbtBondStateObserver() {
            @Override
            public void onBondStateChanged(@NonNull CbtDeviceSnapshot snapshot) {
                if (!same(current, snapshot.deviceId())) {
                    return;
                }
                synchronized (lock) {
                    if (current == generation) {
                        observedBond = snapshot.bondState();
                    }
                }
                notifyListener(current);
            }
        });
        CbtOperationHandle profileHandle = client.observeProfileState(new CbtProfileStateObserver() {
            @Override
            public void onProfileStateChanged(@NonNull CbtDeviceProfileState state) {
                if (same(current, state.deviceId())) {
                    notifyListener(current);
                }
            }
        });
        CbtOperationHandle queryHandle = client.queryConnectedDevices(new CbtConnectedDeviceObserver() {
            @Override
            public void onConnectedDevices(@NonNull List<CbtDeviceSnapshot> snapshots) {
                settle(current);
            }

            @Override
            public void onConnectedDeviceQueryFailed(@NonNull CbtFailure failure) {
                // 查不到音频事实就保持未知。中层会按等待超时失败，不会把未知当成「没连上」去配对。
            }
        });
        synchronized (lock) {
            if (current != generation) {
                cancel(bondHandle);
                cancel(profileHandle);
                cancel(queryHandle);
                return;
            }
            bonds = bondHandle;
            profiles = profileHandle;
            query = queryHandle;
        }
    }

    @Override
    public void unwatch() {
        CbtOperationHandle bondHandle;
        CbtOperationHandle profileHandle;
        CbtOperationHandle queryHandle;
        synchronized (lock) {
            generation++;
            address = null;
            listener = null;
            observedBond = null;
            audioSettled = false;
            bondHandle = bonds;
            profileHandle = profiles;
            queryHandle = query;
            bonds = null;
            profiles = null;
            query = null;
        }
        cancel(bondHandle);
        cancel(profileHandle);
        cancel(queryHandle);
    }

    @Override
    public Snapshot read(@NonNull String classicAddress) {
        String target = classicAddress.trim().toUpperCase(Locale.US);
        boolean settled;
        synchronized (lock) {
            settled = audioSettled && target.equals(address);
        }
        Bond bond = bondOf(target);
        if (!settled) {
            return new Snapshot(bond, Audio.UNKNOWN);
        }
        return new Snapshot(bond, audioOf(target));
    }

    @Override
    public boolean createBond(@NonNull String classicAddress) {
        Result<Void> started = client.createBond(CbtDeviceId.fromAddress(classicAddress));
        return !started.isFailure();
    }

    @Override
    public boolean connectAudio(@NonNull String classicAddress) {
        Result<Void> started = client.connectAudio(CbtDeviceId.fromAddress(classicAddress));
        return !started.isFailure();
    }

    @Override
    public void release() {
        synchronized (lock) {
            released = true;
        }
        unwatch();
        client.release();
    }

    private void settle(int current) {
        synchronized (lock) {
            if (current != generation) {
                return;
            }
            audioSettled = true;
        }
        notifyListener(current);
    }

    private void notifyListener(int current) {
        Listener currentListener;
        synchronized (lock) {
            if (current != generation) {
                return;
            }
            currentListener = listener;
        }
        if (currentListener != null) {
            currentListener.onChanged();
        }
    }

    private Bond bondOf(String target) {
        CbtBondState observed;
        synchronized (lock) {
            observed = observedBond;
        }
        if (observed == CbtBondState.BONDING) {
            return Bond.BONDING;
        }
        Result<List<CbtDeviceSnapshot>> bonded = client.getBondedDevices();
        if (!bonded.isFailure() && bonded.getValue() != null) {
            for (CbtDeviceSnapshot snapshot : bonded.getValue()) {
                if (snapshot.deviceId() != null && target.equals(snapshot.deviceId().value())) {
                    return mapBond(snapshot.bondState());
                }
            }
        }
        if (observed == CbtBondState.BONDED) {
            return Bond.BONDED;
        }
        return Bond.NONE;
    }

    private static Bond mapBond(@Nullable CbtBondState state) {
        if (state == CbtBondState.BONDING) {
            return Bond.BONDING;
        }
        if (state == CbtBondState.BONDED) {
            return Bond.BONDED;
        }
        return Bond.NONE;
    }

    private Audio audioOf(String target) {
        Result<CbtDeviceProfileSnapshot> snapshot =
                client.getDeviceProfileSnapshot(CbtDeviceId.fromAddress(target));
        if (snapshot.isFailure() || snapshot.getValue() == null) {
            return Audio.DOWN;
        }
        CbtProfileConnectionState a2dp = state(snapshot.getValue(), CbtProfileType.A2DP);
        CbtProfileConnectionState headset = state(snapshot.getValue(), CbtProfileType.HEADSET);
        if (a2dp == CbtProfileConnectionState.CONNECTED
                || headset == CbtProfileConnectionState.CONNECTED) {
            return Audio.UP;
        }
        if (a2dp == CbtProfileConnectionState.CONNECTING
                || a2dp == CbtProfileConnectionState.DISCONNECTING
                || headset == CbtProfileConnectionState.CONNECTING
                || headset == CbtProfileConnectionState.DISCONNECTING) {
            return Audio.BUSY;
        }
        return Audio.DOWN;
    }

    @Nullable
    private static CbtProfileConnectionState state(CbtDeviceProfileSnapshot snapshot, CbtProfileType type) {
        CbtDeviceProfileState profile = snapshot.profileStates().get(type);
        return profile == null ? null : profile.state();
    }

    private boolean same(int current, @Nullable CbtDeviceId deviceId) {
        String target;
        synchronized (lock) {
            if (current != generation) {
                return false;
            }
            target = address;
        }
        return target != null && deviceId != null && target.equals(deviceId.value());
    }

    private static void cancel(@Nullable CbtOperationHandle handle) {
        if (handle != null) {
            handle.cancel();
        }
    }
}
