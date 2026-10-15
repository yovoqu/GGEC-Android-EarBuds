package com.yovo.lib_bluetooth.internal.classic.spp.connection;

import static org.junit.Assert.assertEquals;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpointSource;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppSecurityMode;
import com.yovo.lib_bluetooth.internal.classic.spp.policy.YoCbtSppConnectionPolicy;
import com.yovo.lib_bluetooth.internal.classic.spp.policy.YoCbtSppConnectionStrategy;

import org.junit.Test;

public class YoCbtSppConnectionKeyTest {

    @Test
    public void fourArgConstructorDefaultsToUserSpecifiedOnly() {
        YoCbtDeviceId deviceId = YoCbtDeviceId.fromAddress("AA:BB:CC:DD:EE:FF");
        YoCbtSppEndpoint endpoint = YoCbtSppEndpoint.channel(
                15, YoCbtSppEndpointSource.USER_SPECIFIED, YoCbtSppSecurityMode.SECURE);

        YoCbtSppConnectionKey key = new YoCbtSppConnectionKey(
                deviceId, endpoint, endpoint.getSecurityMode(), YoCbtSppConnectionRole.CLIENT);

        assertEquals(YoCbtSppConnectionStrategy.USER_SPECIFIED_ONLY,
                key.getConnectionPolicy().getStrategy());
        assertEquals(YoCbtSppConnectionPolicy.defaultUserSpecified(), key.getConnectionPolicy());
    }
}
