package com.yovo.lib_bluetooth.internal.classic.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtAddressType;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentityResolver;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtBondedDevice;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtObservedDevice;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtResolvedDevice;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtDeviceIdentityResolver} 多源身份合并的单元测试。
 */

public class YoCbtDeviceIdentityResolverTest {

    private YoCbtDeviceIdentityResolver resolver;

    @Before
    public void setUp() {
        resolver = new YoCbtDeviceIdentityResolver();
    }

    private static YoCbtDeviceIdentity createIdentity(String mac, String name, YoCbtDeviceType type) {
        return new YoCbtDeviceIdentity(
                new YoCbtDeviceId(mac), mac, YoCbtAddressType.CLASSIC, type, name);
    }

    @Test
    public void resolveObserved() {
        YoCbtDeviceIdentity identity = createIdentity("AA:BB:CC:DD:EE:FF", "TestDevice",
                YoCbtDeviceType.CLASSIC);
        YoCbtObservedDevice observed = resolver.resolveObserved(identity, -45,
                YoCbtDeviceClass.unknown(), System.currentTimeMillis());
        assertNotNull(observed);
        assertEquals("TestDevice", observed.getIdentity().getDisplayName());
        assertEquals(-45, observed.getRssi());
    }

    @Test
    public void resolveBonded() {
        YoCbtDeviceIdentity identity = createIdentity("11:22:33:44:55:66", "BondedDevice",
                YoCbtDeviceType.CLASSIC);
        UUID sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        YoCbtBondedDevice bonded = resolver.resolveBonded(identity, YoCbtBondState.BONDED,
                Collections.singletonList(sppUuid), System.currentTimeMillis());
        assertNotNull(bonded);
        assertTrue(bonded.isBonded());
        assertTrue(bonded.hasCachedUuids());
    }

    @Test
    public void mergeIdentityPrefersNonEmptyName() {
        YoCbtDeviceIdentity existing = createIdentity("AA:BB:CC:DD:EE:FF", "",
                YoCbtDeviceType.UNKNOWN);
        YoCbtObservedDevice observed = resolver.resolveObserved(
                createIdentity("AA:BB:CC:DD:EE:FF", "NewName", YoCbtDeviceType.CLASSIC),
                -50, null, System.currentTimeMillis());
        YoCbtDeviceIdentity merged = resolver.mergeIdentity(existing, observed,
                System.currentTimeMillis());
        assertEquals("NewName", merged.getDisplayName());
    }

    @Test
    public void mergeIdentityPrefersKnownDeviceType() {
        YoCbtDeviceIdentity existing = createIdentity("AA:BB:CC:DD:EE:FF", "Device",
                YoCbtDeviceType.UNKNOWN);
        YoCbtObservedDevice observed = resolver.resolveObserved(
                createIdentity("AA:BB:CC:DD:EE:FF", "Device", YoCbtDeviceType.CLASSIC),
                -50, YoCbtDeviceClass.unknown(), System.currentTimeMillis());
        YoCbtDeviceIdentity merged = resolver.mergeIdentity(existing, observed,
                System.currentTimeMillis());
        assertEquals(YoCbtDeviceType.CLASSIC, merged.getDeviceType());
    }

    @Test
    public void mergeWithNullExistingReturnsObserved() {
        YoCbtObservedDevice observed = resolver.resolveObserved(
                createIdentity("FF:EE:DD:CC:BB:AA", "Solo", YoCbtDeviceType.DUAL),
                -70, null, System.currentTimeMillis());
        YoCbtDeviceIdentity merged = resolver.mergeIdentity(null, observed,
                System.currentTimeMillis());
        assertNotNull(merged);
        assertEquals("Solo", merged.getDisplayName());
    }
}
