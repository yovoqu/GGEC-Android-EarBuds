package com.yovo.lib_bluetooth.internal.classic.error;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;

import org.junit.Test;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtFailure} 失败信息构建与字段的单元测试。
 */

public class YoCbtFailureTest {
    @Test
    public void failureCarriesConnectionContextAndTrace() {
        YoCbtSppConnectionId connectionId = new YoCbtSppConnectionId("c-1");
        YoCbtSppEndpoint endpoint = YoCbtSppEndpoint.defaultSpp();

        YoCbtFailure failure = new YoCbtFailure(YoCbtErrorCode.YO_CBT_SPP_READ_FAILED,
                "read failed", null, true, true, "spp",
                YoCbtDeviceId.fromAddress("AA:BB:CC:DD:EE:FF"), null,
                connectionId, endpoint, "reconnect");

        assertEquals(YoCbtErrorCode.YO_CBT_SPP_READ_FAILED, failure.getErrorCode());
        assertEquals(connectionId, failure.getConnectionId());
        assertEquals(endpoint, failure.getEndpoint());
        assertEquals("reconnect", failure.getSuggestedAction());
        assertNotNull(failure.getTraceId());
    }
}
