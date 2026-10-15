package com.yovo.lib_bluetooth.internal.classic.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtAddressType;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceIdentity;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceType;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.profiles.state.YoCbtDeviceProfileState;

import org.junit.Test;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class YoCbtDeviceSnapshotTest {

    @Test
    public void unknownRssiSentinels_areNormalizedToUnavailable() {
        YoCbtDeviceSnapshot integerMin = snapshot(Integer.MIN_VALUE);
        YoCbtDeviceSnapshot shortMin = snapshot(Short.MIN_VALUE);

        assertEquals(YoCbtDeviceSnapshot.RSSI_UNAVAILABLE, integerMin.getRssi());
        assertEquals(YoCbtDeviceSnapshot.RSSI_UNAVAILABLE, shortMin.getRssi());
        assertFalse(integerMin.hasValidRssi());
        assertFalse(shortMin.hasValidRssi());
    }

    @Test
    public void classicRssiRange_isValid() {
        YoCbtDeviceSnapshot weak = snapshot(-127);
        YoCbtDeviceSnapshot zero = snapshot(0);
        YoCbtDeviceSnapshot high = snapshot(20);

        assertTrue(weak.hasValidRssi());
        assertTrue(zero.hasValidRssi());
        assertTrue(high.hasValidRssi());
    }

    @Test
    public void outOfRangeRssi_isUnavailable() {
        YoCbtDeviceSnapshot tooLow = snapshot(-128);
        YoCbtDeviceSnapshot tooHigh = snapshot(21);

        assertEquals(YoCbtDeviceSnapshot.RSSI_UNAVAILABLE, tooLow.getRssi());
        assertEquals(YoCbtDeviceSnapshot.RSSI_UNAVAILABLE, tooHigh.getRssi());
        assertFalse(tooLow.hasValidRssi());
        assertFalse(tooHigh.hasValidRssi());
    }

    private static YoCbtDeviceSnapshot snapshot(int rssi) {
        Map<YoCbtProfileType, YoCbtDeviceProfileState> profiles =
                new EnumMap<>(YoCbtProfileType.class);
        return new YoCbtDeviceSnapshot(
                new YoCbtDeviceIdentity(
                        YoCbtDeviceId.fromAddress("AA:BB:CC:DD:EE:FF"),
                        "AA:BB:CC:DD:EE:FF",
                        YoCbtAddressType.CLASSIC,
                        YoCbtDeviceType.CLASSIC,
                        "speaker"),
                YoCbtBondState.UNKNOWN,
                YoCbtAclLinkState.UNKNOWN,
                Collections.emptyList(),
                profiles,
                Collections.emptyList(),
                rssi,
                0L,
                0L);
    }
}
