package com.yovo.lib_bluetooth.internal.classic.error;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtErrorCode} 错误码定义与分组的单元测试。
 */

public class YoCbtErrorCodeTest {

    @Test
    public void allCodesHaveValidNames() {
        for (YoCbtErrorCode code : YoCbtErrorCode.values()) {
            assertNotNull(code.code());
            assertTrue(code.code().startsWith("YO_CBT_"));
        }
    }

    @Test
    public void countMeetsMinimum() {
        // 文档要求约 70 个错误码
        assertTrue("Expected >= 70 error codes, got " + YoCbtErrorCode.values().length,
                YoCbtErrorCode.values().length >= 70);
    }

    @Test
    public void generalGroupPresent() {
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_BLUETOOTH_NOT_SUPPORTED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_BLUETOOTH_DISABLED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_INTERNAL_ERROR"));
    }

    @Test
    public void permissionGroupPresent() {
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_PERMISSION_SCAN_DENIED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_PERMISSION_CONNECT_DENIED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_PERMISSION_LOCATION_DENIED"));
    }

    @Test
    public void discoveryGroupPresent() {
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_DISCOVERY_START_FAILED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_DISCOVERY_TIMEOUT"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_DISCOVERY_RECEIVER_ERROR"));
    }

    @Test
    public void pairingGroupPresent() {
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_BOND_START_FAILED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_BOND_HIDDEN_API_BLOCKED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_BOND_DEVICE_NOT_CONNECTABLE"));
    }

    @Test
    public void sdpGroupPresent() {
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_SDP_UUID_EMPTY"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_SDP_UUID_NOT_MATCHED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_SDP_CHANNEL_NOT_RESOLVED"));
    }

    @Test
    public void profileGroupPresent() {
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_PROFILE_UNSUPPORTED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_PROFILE_PROXY_GET_FAILED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_PROFILE_PROXY_LIFECYCLE_ERROR"));
    }

    @Test
    public void a2dpGroupPresent() {
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_A2DP_STATE_QUERY_FAILED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_A2DP_CODEC_STATE_UNAVAILABLE"));
    }

    @Test
    public void headsetGroupPresent() {
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_HEADSET_STATE_QUERY_FAILED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_HEADSET_SCO_STATE_UNAVAILABLE"));
    }

    @Test
    public void sppGroupPresent() {
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_SPP_CONNECT_TIMEOUT"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_SPP_CONNECTION_ID_NOT_FOUND"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_SPP_ALREADY_CONNECTED"));
    }

    @Test
    public void sppChannelGroupPresent() {
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_SPP_CHANNEL_HIDDEN_API_NOT_AVAILABLE"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_SPP_CHANNEL_REFLECTION_FAILED"));
    }

    @Test
    public void protocolGroupPresent() {
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_PACKET_ENCODE_FAILED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_COMMAND_TIMEOUT"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_COMMAND_CANCELLED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_RESPONSE_NOT_MATCHED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_COMMAND_QUEUE_FULL"));
    }

    @Test
    public void lifecycleGroupPresent() {
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_RELEASED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_ALREADY_RELEASED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_RELEASE_IN_PROGRESS"));
    }

    @Test
    public void multiConnectionGroupPresent() {
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_SPP_CONNECTION_KEY_CONFLICT"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_SPP_CONNECTION_LIMIT_EXCEEDED"));
        assertNotNull(YoCbtErrorCode.valueOf("YO_CBT_SPP_CONNECTION_GENERATION_EXPIRED"));
    }
}
