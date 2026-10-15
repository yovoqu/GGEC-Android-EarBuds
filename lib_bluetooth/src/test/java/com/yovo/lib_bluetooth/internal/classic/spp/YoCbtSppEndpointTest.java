package com.yovo.lib_bluetooth.internal.classic.spp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpointSource;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpointType;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppSecurityMode;

import org.junit.Test;

import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtSppEndpoint} 端点构建与比较的单元测试。
 */

public class YoCbtSppEndpointTest {
    @Test
    public void defaultSppUsesStandardUuidAndSecureMode() {
        YoCbtSppEndpoint endpoint = YoCbtSppEndpoint.defaultSpp();

        assertEquals(YoCbtSppEndpointType.UUID, endpoint.getType());
        assertEquals(YoCbtSppEndpoint.STANDARD_SPP_UUID, endpoint.getUuid());
        assertEquals(YoCbtSppEndpointSource.DEFAULT_SPP, endpoint.getSource());
        assertEquals(YoCbtSppSecurityMode.SECURE, endpoint.getSecurityMode());
    }

    @Test
    public void channelEndpointKeepsChannelSourceAndSecurity() {
        YoCbtSppEndpoint endpoint = YoCbtSppEndpoint.channel(7,
                YoCbtSppEndpointSource.DEBUG_TOOL_CONFIRMED,
                YoCbtSppSecurityMode.INSECURE);

        assertEquals(YoCbtSppEndpointType.RFCOMM_CHANNEL, endpoint.getType());
        assertEquals(7, endpoint.getChannel());
        assertNull(endpoint.getUuid());
        assertEquals(YoCbtSppEndpointSource.DEBUG_TOOL_CONFIRMED, endpoint.getSource());
        assertEquals(YoCbtSppSecurityMode.INSECURE, endpoint.getSecurityMode());
    }

    @Test
    public void uuidEndpointKeepsCallerUuid() {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

        YoCbtSppEndpoint endpoint = YoCbtSppEndpoint.uuid(uuid);

        assertEquals(YoCbtSppEndpointType.UUID, endpoint.getType());
        assertEquals(uuid, endpoint.getUuid());
        assertEquals(YoCbtSppEndpointSource.USER_SPECIFIED, endpoint.getSource());
    }
}
