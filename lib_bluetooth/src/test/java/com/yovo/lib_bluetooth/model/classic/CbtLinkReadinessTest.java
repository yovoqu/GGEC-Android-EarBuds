package com.yovo.lib_bluetooth.model.classic;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * {@link CbtLinkReadiness#of} 的三态归并契约。
 *
 * <p>这是「链路就绪」判定的唯一真源：读错一个分支，上层就会在「已连接」时误判「未连接」
 * （对已连设备重复 CONNECT 被系统忽略 → 必然超时），或在「未连接」时干等。
 * 因此每个分支都单独钉死。</p>
 */
public final class CbtLinkReadinessTest {

    @Test
    public void aclConnectedIsReady() {
        assertEquals(CbtLinkReadiness.READY,
                CbtLinkReadiness.of(CbtAclLinkState.CONNECTED, null, null, null));
    }

    @Test
    public void a2dpConnectedIsReadyEvenIfAclUnknown() {
        assertEquals(CbtLinkReadiness.READY,
                CbtLinkReadiness.of(null, null, CbtProfileConnectionState.CONNECTED, null));
    }

    @Test
    public void headsetConnectedIsReadyEvenIfAclUnknown() {
        assertEquals(CbtLinkReadiness.READY,
                CbtLinkReadiness.of(null, null, null, CbtProfileConnectionState.CONNECTED));
    }

    @Test
    public void nonEmptyConnectedProfilesIsReady() {
        assertEquals(CbtLinkReadiness.READY,
                CbtLinkReadiness.of(null, List.of(CbtProfileType.A2DP), null, null));
    }

    @Test
    public void aclDisconnectedWithEmptyProfilesAndAudioDownIsDown() {
        assertEquals(CbtLinkReadiness.DOWN,
                CbtLinkReadiness.of(CbtAclLinkState.DISCONNECTED, List.of(),
                        CbtProfileConnectionState.DISCONNECTED,
                        CbtProfileConnectionState.DISCONNECTED));
    }

    @Test
    public void aclDisconnectedWithEmptyProfilesAndNullAudioIsDown() {
        // 音频 Profile 事实缺失但 ACL 明确断开、Profile 列表为空 → 可判 DOWN（安全发起连接）。
        assertEquals(CbtLinkReadiness.DOWN,
                CbtLinkReadiness.of(CbtAclLinkState.DISCONNECTED, List.of(), null, null));
    }

    @Test
    public void aclUnknownWithoutProfilesIsUnknown() {
        assertEquals(CbtLinkReadiness.UNKNOWN,
                CbtLinkReadiness.of(null, null, null, null));
    }

    @Test
    public void aclUnknownWithFailedProfileQueryIsUnknownNotDown() {
        // 关键：查询失败（connectedProfiles==null）绝不退化成 DOWN，否则会误触发重连。
        assertEquals(CbtLinkReadiness.UNKNOWN,
                CbtLinkReadiness.of(null, null,
                        CbtProfileConnectionState.DISCONNECTED,
                        CbtProfileConnectionState.DISCONNECTED));
    }

    @Test
    public void aclUnknownButAudioBusyIsUnknown() {
        assertEquals(CbtLinkReadiness.UNKNOWN,
                CbtLinkReadiness.of(null, null,
                        CbtProfileConnectionState.CONNECTING, null));
    }

    @Test
    public void aclDisconnectedButAudioConnectedIsReady() {
        // 连接是单调可达事实：任一 Profile 已连即 READY，哪怕 ACL 报断开。
        assertEquals(CbtLinkReadiness.READY,
                CbtLinkReadiness.of(CbtAclLinkState.DISCONNECTED, List.of(),
                        CbtProfileConnectionState.CONNECTED, null));
    }
}
