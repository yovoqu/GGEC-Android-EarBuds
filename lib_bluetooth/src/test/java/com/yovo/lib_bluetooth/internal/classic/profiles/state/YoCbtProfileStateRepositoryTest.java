package com.yovo.lib_bluetooth.internal.classic.profiles.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtAddressType;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshotStore;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpDeviceState;
import com.yovo.lib_bluetooth.internal.classic.profiles.a2dp.YoCbtA2dpRuntimeState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetAudioState;
import com.yovo.lib_bluetooth.internal.classic.profiles.headset.YoCbtHeadsetDeviceState;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtProfileStateRepository} 状态存储与通知的单元测试。
 */

public class YoCbtProfileStateRepositoryTest {

    private YoCbtDeviceSnapshotStore store;
    private YoCbtProfileStateRepository repository;
    private YoCbtDeviceId deviceId;

    @Before
    public void setUp() {
        store = new YoCbtDeviceSnapshotStore();
        repository = new YoCbtProfileStateRepository(store);
        YoCbtDeviceIdentity identity = new YoCbtDeviceIdentity(
                YoCbtDeviceId.fromAddress("AA:BB:CC:DD:EE:01"),
                "AA:BB:CC:DD:EE:01",
                YoCbtAddressType.CLASSIC,
                YoCbtDeviceType.CLASSIC,
                "test-device");
        deviceId = identity.getDeviceId();
        store.updateObserved(identity, -50, 100L);
    }

    @Test
    public void applyProfileStatePersistsToSnapshotStore() {
        YoCbtDeviceProfileState profileState = new YoCbtDeviceProfileState(
                deviceId,
                YoCbtProfileType.A2DP,
                YoCbtProfileConnectionState.CONNECTED,
                YoCbtProfileStateSource.PROFILE_BROADCAST,
                200L);

        YoCbtDeviceProfileState applied = repository.applyProfileState(profileState);

        assertNotNull(applied);
        YoCbtDeviceSnapshot snapshot = store.get(deviceId);
        assertNotNull(snapshot);
        assertEquals(YoCbtProfileConnectionState.CONNECTED,
                snapshot.getProfileStates().get(YoCbtProfileType.A2DP).getConnectionState());
    }

    @Test
    public void applyA2dpAndHeadsetStatePersistTypedSnapshots() {
        repository.applyA2dpState(new YoCbtA2dpDeviceState(
                deviceId,
                YoCbtProfileConnectionState.CONNECTED,
                YoCbtA2dpRuntimeState.PROFILE_CONNECTED,
                300L,
                YoCbtProfileStateSource.ANDROID_PROFILE_PROXY));
        repository.applyHeadsetState(new YoCbtHeadsetDeviceState(
                deviceId,
                YoCbtProfileConnectionState.CONNECTED,
                YoCbtHeadsetAudioState.AUDIO_CONNECTED,
                false,
                301L,
                YoCbtProfileStateSource.PROFILE_BROADCAST));

        YoCbtDeviceSnapshot snapshot = store.get(deviceId);
        assertNotNull(snapshot.getA2dpState());
        assertNotNull(snapshot.getHeadsetState());
        assertEquals(YoCbtHeadsetAudioState.AUDIO_CONNECTED, snapshot.getHeadsetState().getAudioState());
    }

    @Test
    public void getConnectedProfilesReadsPersistedProfileMap() {
        repository.applyProfileState(new YoCbtDeviceProfileState(
                deviceId, YoCbtProfileType.A2DP, YoCbtProfileConnectionState.CONNECTED,
                YoCbtProfileStateSource.PROFILE_BROADCAST, 400L));
        repository.applyProfileState(new YoCbtDeviceProfileState(
                deviceId, YoCbtProfileType.HEADSET, YoCbtProfileConnectionState.DISCONNECTED,
                YoCbtProfileStateSource.PROFILE_BROADCAST, 401L));

        var result = repository.getConnectedProfiles(deviceId);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getValue().size());
        assertEquals(YoCbtProfileType.A2DP, result.getValue().get(0));
    }

    @Test
    public void getConnectedProfilesIncludesSppRegistryState() {
        repository.applyProfileState(new YoCbtDeviceProfileState(
                deviceId, YoCbtProfileType.SPP, YoCbtProfileConnectionState.CONNECTED,
                YoCbtProfileStateSource.SPP_CONNECTION_REGISTRY, 450L));

        var result = repository.getConnectedProfiles(deviceId);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getValue().size());
        assertEquals(YoCbtProfileType.SPP, result.getValue().get(0));
    }

    @Test
    public void refreshOnAclDisconnectedClearsActiveProfiles() {
        repository.applyProfileState(new YoCbtDeviceProfileState(
                deviceId, YoCbtProfileType.A2DP, YoCbtProfileConnectionState.CONNECTED,
                YoCbtProfileStateSource.PROFILE_BROADCAST, 500L));

        repository.refreshOnAclDisconnected(deviceId);

        YoCbtDeviceSnapshot snapshot = store.get(deviceId);
        assertEquals(YoCbtProfileConnectionState.DISCONNECTED,
                snapshot.getProfileStates().get(YoCbtProfileType.A2DP).getConnectionState());
        assertEquals(YoCbtProfileStateSource.ACL_BROADCAST_INFERRED,
                snapshot.getProfileStates().get(YoCbtProfileType.A2DP).getSource());
    }

    @Test
    public void olderProfileEventCannotOverwriteNewerState() {
        repository.applyProfileState(new YoCbtDeviceProfileState(
                deviceId, YoCbtProfileType.A2DP, YoCbtProfileConnectionState.DISCONNECTED,
                YoCbtProfileStateSource.PROFILE_BROADCAST, 700L));

        YoCbtDeviceProfileState ignored = repository.applyProfileState(
                new YoCbtDeviceProfileState(
                        deviceId, YoCbtProfileType.A2DP, YoCbtProfileConnectionState.CONNECTED,
                        YoCbtProfileStateSource.ANDROID_PROFILE_PROXY, 600L));

        assertEquals(null, ignored);
        assertEquals(YoCbtProfileConnectionState.DISCONNECTED,
                store.get(deviceId).getProfileStates().get(YoCbtProfileType.A2DP)
                        .getConnectionState());
    }

    @Test
    public void proxySnapshotDisconnectsPreviouslyActiveProfileMissingFromResult() {
        repository.applyProfileState(new YoCbtDeviceProfileState(
                deviceId, YoCbtProfileType.A2DP, YoCbtProfileConnectionState.CONNECTED,
                YoCbtProfileStateSource.PROFILE_BROADCAST, 800L));

        repository.reconcileProfileSnapshot(
                YoCbtProfileType.A2DP, Collections.emptySet(), 900L);

        assertEquals(YoCbtProfileConnectionState.DISCONNECTED,
                store.get(deviceId).getProfileStates().get(YoCbtProfileType.A2DP)
                        .getConnectionState());
        assertEquals(YoCbtProfileStateSource.ANDROID_PROFILE_PROXY,
                store.get(deviceId).getProfileStates().get(YoCbtProfileType.A2DP).getSource());
        assertEquals(YoCbtProfileConnectionState.DISCONNECTED,
                store.get(deviceId).getA2dpState().getProfileState());
        assertEquals(YoCbtA2dpRuntimeState.PROFILE_DISCONNECTED,
                store.get(deviceId).getA2dpState().getRuntimeState());
    }

    @Test
    public void lateProxySnapshotCannotOverwriteBroadcastArrivingAfterQueryStarted() {
        repository.applyProfileState(new YoCbtDeviceProfileState(
                deviceId, YoCbtProfileType.A2DP, YoCbtProfileConnectionState.CONNECTED,
                YoCbtProfileStateSource.ANDROID_PROFILE_PROXY, 1_000L));
        repository.applyProfileState(new YoCbtDeviceProfileState(
                deviceId, YoCbtProfileType.A2DP, YoCbtProfileConnectionState.DISCONNECTED,
                YoCbtProfileStateSource.PROFILE_BROADCAST, 1_100L));

        repository.applyProfileState(new YoCbtDeviceProfileState(
                deviceId, YoCbtProfileType.A2DP, YoCbtProfileConnectionState.CONNECTED,
                YoCbtProfileStateSource.ANDROID_PROFILE_PROXY, 1_050L));

        assertEquals(YoCbtProfileConnectionState.DISCONNECTED,
                store.get(deviceId).getProfileStates().get(YoCbtProfileType.A2DP)
                        .getConnectionState());
    }
}
