package com.yovo.lib_bluetooth.internal.classic.spp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionKey;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionRole;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.internal.classic.spp.registry.YoCbtSppConnectionRegistry;

import org.junit.Test;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtSppConnectionRegistry} 连接注册与选择的单元测试。
 */

public class YoCbtSppConnectionRegistryTest {
    @Test
    public void storesAndQueriesByConnectionIdAndDevice() {
        YoCbtSppConnectionRegistry<String> registry = new YoCbtSppConnectionRegistry<>();
        YoCbtDeviceId deviceA = YoCbtDeviceId.fromAddress("AA:BB:CC:DD:EE:FF");
        YoCbtDeviceId deviceB = YoCbtDeviceId.fromAddress("11:22:33:44:55:66");
        YoCbtSppConnectionId id1 = new YoCbtSppConnectionId("spp-1");
        YoCbtSppConnectionId id2 = new YoCbtSppConnectionId("spp-2");

        registry.put(id1, key(deviceA), "a1");
        registry.put(id2, key(deviceB), "b1");

        assertEquals("a1", registry.get(id1));
        assertEquals(1, registry.byDevice(deviceA).size());
        assertEquals(id1, registry.idsByDevice(deviceA).get(0));
        assertEquals("a1", registry.remove(id1));
        assertNull(registry.get(id1));
    }

    private static YoCbtSppConnectionKey key(YoCbtDeviceId deviceId) {
        YoCbtSppEndpoint endpoint = YoCbtSppEndpoint.defaultSpp();
        return new YoCbtSppConnectionKey(deviceId, endpoint,
                endpoint.getSecurityMode(), YoCbtSppConnectionRole.CLIENT);
    }
}
