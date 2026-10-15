package com.yovo.lib_bluetooth.internal.classic.profiles.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class YoCbtProfileTypeTest {

    @Test
    public void androidProfileIdsMatchPlatformConstants() {
        assertEquals(1, YoCbtProfileType.HEADSET.getAndroidProfile());
        assertEquals(2, YoCbtProfileType.A2DP.getAndroidProfile());
        assertEquals(4, YoCbtProfileType.HID_HOST.getAndroidProfile());
        assertEquals(5, YoCbtProfileType.PAN.getAndroidProfile());
        assertEquals(17, YoCbtProfileType.PBAP.getAndroidProfile());
        assertEquals(21, YoCbtProfileType.HEARING_AID.getAndroidProfile());
        assertEquals(22, YoCbtProfileType.LE_AUDIO.getAndroidProfile());
    }

    @Test
    public void headsetRuntimeProfileIsDistinctFromHfpAndHspCapabilities() {
        assertEquals(YoCbtProfileType.HEADSET, YoCbtProfileType.fromAndroidProfile(1));
        assertEquals(false, YoCbtProfileType.HFP.hasAndroidProfile());
        assertEquals(false, YoCbtProfileType.HSP.hasAndroidProfile());
        assertEquals(true, YoCbtProfileType.HFP.isCapabilityOnly());
    }
}
