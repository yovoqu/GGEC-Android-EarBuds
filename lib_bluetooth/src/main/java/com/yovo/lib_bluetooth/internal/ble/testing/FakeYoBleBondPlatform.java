package com.yovo.lib_bluetooth.internal.ble.testing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.ble.pairing.YoBleBondPlatform;
import com.yovo.lib_bluetooth.model.ble.BleBondState;
import com.yovo.lib_bluetooth.model.ble.BleDeviceId;
import com.yovo.lib_bluetooth.model.ble.BleDeviceIdentity;
import com.yovo.lib_bluetooth.model.ble.BleErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BLE 配对平台可控 Fake 实现。
 */
public final class FakeYoBleBondPlatform implements YoBleBondPlatform {

    private final Map<BleDeviceId, BleDeviceIdentity> bondedIdentities = new LinkedHashMap<>();
    private final Map<BleDeviceId, BleBondState> bondStates = new LinkedHashMap<>();
    private boolean createBondSuccess = true;
    private boolean cancelBondSuccess = true;
    private boolean removeBondSuccess = true;
    private boolean autoCompleteCreateBond = false;
    private BleBondState autoCompleteTargetState = BleBondState.BONDED;
    private int autoCompleteReason = 0;

    @Nullable
    private BondEventSink autoCompleteSink;

    public interface BondEventSink {
        void onBondTransition(@NonNull BleDeviceId deviceId,
                              @NonNull BleBondState previous,
                              @NonNull BleBondState current,
                              int rawReason);
    }

    public FakeYoBleBondPlatform setAutoCompleteSink(@Nullable BondEventSink sink) {
        this.autoCompleteSink = sink;
        return this;
    }

    public FakeYoBleBondPlatform setAutoCompleteCreateBond(boolean autoComplete,
                                                           @NonNull BleBondState targetState,
                                                           int reason) {
        this.autoCompleteCreateBond = autoComplete;
        this.autoCompleteTargetState = targetState;
        this.autoCompleteReason = reason;
        return this;
    }

    public FakeYoBleBondPlatform addBondedDevice(@NonNull BleDeviceIdentity identity) {
        bondedIdentities.put(identity.deviceId(), identity);
        bondStates.put(identity.deviceId(), BleBondState.BONDED);
        return this;
    }

    public FakeYoBleBondPlatform setBondState(@NonNull BleDeviceId deviceId, @NonNull BleBondState state) {
        bondStates.put(deviceId, state);
        if (state == BleBondState.BONDED && !bondedIdentities.containsKey(deviceId)) {
            bondedIdentities.put(deviceId, new BleDeviceIdentity(deviceId, "", 0L));
        } else if (state != BleBondState.BONDED) {
            bondedIdentities.remove(deviceId);
        }
        return this;
    }

    public FakeYoBleBondPlatform setCreateBondSuccess(boolean createBondSuccess) {
        this.createBondSuccess = createBondSuccess;
        return this;
    }

    public FakeYoBleBondPlatform setRemoveBondSuccess(boolean removeBondSuccess) {
        this.removeBondSuccess = removeBondSuccess;
        return this;
    }

    @NonNull
    @Override
    public List<BleDeviceIdentity> getBondedDevices() {
        return new ArrayList<>(bondedIdentities.values());
    }

    @NonNull
    @Override
    public BleBondState getBondState(@NonNull BleDeviceId deviceId) {
        return bondStates.getOrDefault(deviceId, BleBondState.NONE);
    }

    @NonNull
    @Override
    public ActionResult createBond(@NonNull BleDeviceId deviceId) {
        if (!createBondSuccess) {
            return ActionResult.fail(BleErrorCode.BOND_FAILED, "Fake createBond rejected");
        }
        BleBondState prev = getBondState(deviceId);
        bondStates.put(deviceId, BleBondState.BONDING);
        if (autoCompleteSink != null) {
            autoCompleteSink.onBondTransition(deviceId, prev, BleBondState.BONDING, 0);
            if (autoCompleteCreateBond) {
                setBondState(deviceId, autoCompleteTargetState);
                autoCompleteSink.onBondTransition(
                        deviceId, BleBondState.BONDING, autoCompleteTargetState, autoCompleteReason);
            }
        }
        return ActionResult.ok();
    }

    @NonNull
    @Override
    public ActionResult cancelBondProcess(@NonNull BleDeviceId deviceId) {
        if (!cancelBondSuccess) {
            return ActionResult.fail(BleErrorCode.BOND_FAILED, "Fake cancelBondProcess failed");
        }
        bondStates.put(deviceId, BleBondState.NONE);
        return ActionResult.ok();
    }

    @NonNull
    @Override
    public ActionResult removeBond(@NonNull BleDeviceId deviceId, boolean allowHiddenApi) {
        if (!allowHiddenApi) {
            return ActionResult.fail(BleErrorCode.HIDDEN_API_BLOCKED, "Hidden API blocked");
        }
        if (!removeBondSuccess) {
            return ActionResult.fail(BleErrorCode.HIDDEN_API_FAILED, "Fake removeBond failed");
        }
        setBondState(deviceId, BleBondState.NONE);
        return ActionResult.ok();
    }
}
