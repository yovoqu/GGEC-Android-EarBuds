package com.yovo.lib_bluetooth.internal.classic.link;

import static org.junit.Assert.assertEquals;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtAddressType;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshotStore;

import org.junit.Test;

public class YoCbtAclLinkRepositoryTest {

    @Test
    public void connectedDevicesFallsBackToSeededSnapshotState() {
        YoCbtDeviceSnapshotStore store = new YoCbtDeviceSnapshotStore();
        YoCbtAclLinkTracker tracker = new YoCbtAclLinkTracker(store);
        YoCbtAclLinkRepository repository = new YoCbtAclLinkRepository(tracker, store);
        YoCbtDeviceId id = YoCbtDeviceId.fromAddress("AA:BB:CC:DD:EE:01");
        YoCbtDeviceIdentity identity = new YoCbtDeviceIdentity(
                id, id.value(), YoCbtAddressType.CLASSIC, YoCbtDeviceType.CLASSIC, "1888");

        store.updateAcl(identity, YoCbtAclLinkState.CONNECTED, 100L);

        assertEquals(1, repository.getConnectedDevices().size());
        assertEquals(id, repository.getConnectedDevices().get(0));
    }

    @Test
    public void explicitTrackerDisconnectOverridesOlderConnectedSnapshot() {
        YoCbtDeviceSnapshotStore store = new YoCbtDeviceSnapshotStore();
        YoCbtAclLinkTracker tracker = new YoCbtAclLinkTracker(store);
        YoCbtAclLinkRepository repository = new YoCbtAclLinkRepository(tracker, store);
        YoCbtDeviceId id = YoCbtDeviceId.fromAddress("AA:BB:CC:DD:EE:02");
        YoCbtDeviceIdentity identity = new YoCbtDeviceIdentity(
                id, id.value(), YoCbtAddressType.CLASSIC, YoCbtDeviceType.CLASSIC, "1888");

        tracker.updateAclState(identity, YoCbtAclLinkState.DISCONNECTED, 100L);
        store.updateAcl(identity, YoCbtAclLinkState.CONNECTED, 200L);

        assertEquals(0, repository.getConnectedDevices().size());
    }
}
