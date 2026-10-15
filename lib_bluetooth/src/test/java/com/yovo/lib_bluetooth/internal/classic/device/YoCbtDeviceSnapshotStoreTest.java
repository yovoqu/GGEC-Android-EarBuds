package com.yovo.lib_bluetooth.internal.classic.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtAddressType;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileConnectionState;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtProfileStateSource;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;

import org.junit.Test;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtDeviceSnapshotStore} 快照增量更新的单元测试。
 */

public class YoCbtDeviceSnapshotStoreTest {
    @Test
    public void updatesBondAclAndProfileWithoutMixingStates() {
        YoCbtDeviceSnapshotStore store = new YoCbtDeviceSnapshotStore();
        YoCbtDeviceIdentity identity = identity("AA:BB:CC:DD:EE:FF", "speaker");

        store.updateObserved(identity, -45, 10L);
        store.updateBond(identity, YoCbtBondState.BONDED, 20L);
        store.updateAcl(identity, YoCbtAclLinkState.CONNECTED, 30L);
        store.updateProfile(new YoCbtDeviceProfileState(identity.getDeviceId(), YoCbtProfileType.A2DP,
                YoCbtProfileConnectionState.CONNECTED,
                YoCbtProfileStateSource.ANDROID_PROFILE_PROXY,
                40L));

        YoCbtDeviceSnapshot snapshot = store.get(identity.getDeviceId());

        assertEquals(YoCbtBondState.BONDED, snapshot.getBondState());
        assertEquals(YoCbtAclLinkState.CONNECTED, snapshot.getAclLinkState());
        assertEquals(YoCbtProfileConnectionState.CONNECTED,
                snapshot.getProfileStates().get(YoCbtProfileType.A2DP).getConnectionState());
        assertTrue(snapshot.getSppConnections().isEmpty());
        assertEquals(40L, snapshot.getLastUpdatedMillis());
    }

    private static YoCbtDeviceIdentity identity(String address, String name) {
        return new YoCbtDeviceIdentity(YoCbtDeviceId.fromAddress(address), address,
                YoCbtAddressType.CLASSIC, YoCbtDeviceType.CLASSIC, name);
    }
}
