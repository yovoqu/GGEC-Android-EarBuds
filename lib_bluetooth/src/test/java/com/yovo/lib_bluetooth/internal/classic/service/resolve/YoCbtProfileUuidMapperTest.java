package com.yovo.lib_bluetooth.internal.classic.service.resolve;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;

import org.junit.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtProfileUuidMapper} UUID 到 Profile 映射的单元测试。
 */

public class YoCbtProfileUuidMapperTest {
    @Test
    public void mapsCommonBluetoothProfileUuids() {
        YoCbtProfileUuidMapper mapper = new YoCbtProfileUuidMapper();

        Set<YoCbtProfileType> profiles = mapper.mapProfiles(Arrays.asList(
                UUID.fromString("0000110b-0000-1000-8000-00805f9b34fb"),
                UUID.fromString("0000111e-0000-1000-8000-00805f9b34fb"),
                YoCbtSppEndpoint.STANDARD_SPP_UUID));

        assertTrue(profiles.contains(YoCbtProfileType.A2DP));
        assertTrue(profiles.contains(YoCbtProfileType.HEADSET));
        assertTrue(profiles.contains(YoCbtProfileType.HFP));
        assertTrue(profiles.contains(YoCbtProfileType.SPP));
    }

    @Test
    public void mapsAvrcpSeparatelyFromA2dpSource() {
        YoCbtProfileUuidMapper mapper = new YoCbtProfileUuidMapper();

        Set<YoCbtProfileType> avrcpOnly = mapper.mapProfiles(Arrays.asList(
                UUID.fromString("0000110a-0000-1000-8000-00805f9b34fb")));
        Set<YoCbtProfileType> a2dpSource = mapper.mapProfiles(Arrays.asList(
                UUID.fromString("0000110b-0000-1000-8000-00805f9b34fb")));

        assertTrue(avrcpOnly.contains(YoCbtProfileType.AVRCP));
        assertFalse(avrcpOnly.contains(YoCbtProfileType.A2DP));
        assertTrue(a2dpSource.contains(YoCbtProfileType.A2DP));
        assertFalse(a2dpSource.contains(YoCbtProfileType.AVRCP));
    }
}
