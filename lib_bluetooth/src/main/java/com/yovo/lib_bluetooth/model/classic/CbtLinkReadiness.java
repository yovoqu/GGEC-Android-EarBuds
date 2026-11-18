package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 经典蓝牙「链路就绪」的三态事实。
 *
 * <p><b>为什么必须三态而不是 boolean</b>：Android 的 ACL/Profile 状态有「未知」这一合法取值
 * （设备未被跟踪、广播尚未到达、代理尚未 onServiceConnected）。把它压缩成
 * {@code false}（未连接）会让调用方误判为「需要重新连接」，
 * 而实际设备早已连上——系统会忽略重复的 CONNECT 请求，不再产生任何状态变化广播，
 * 调用方只能等到超时。这是经典蓝牙最常见的一类时序缺陷。</p>
 *
 * <p>{@link #UNKNOWN} 的语义是「还没有足够事实」，调用方必须继续等待，
 * 不得据此发起配对或重连。</p>
 */
public enum CbtLinkReadiness {

    /** 链路已就绪：ACL 连通，或任一音频 Profile（A2DP / HEADSET）已连接。 */
    READY,

    /** 明确未连接：所有事实源都给出断开。可以安全地发起配对/连接。 */
    DOWN,

    /** 事实不足：ACL 未知且无 Profile 事实。只能等待，不能据此判断。 */
    UNKNOWN;

    public boolean isReady() {
        return this == READY;
    }

    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    /**
     * 把「已连接 Profile 列表 + ACL 状态 + Profile 快照」归并为就绪事实。
     *
     * <p>归并优先级：任何一项给出 CONNECTED 即 READY（连接是单调可达的事实）；
     * 全部明确 DISCONNECTED 才是 DOWN；其余一律 UNKNOWN。</p>
     *
     * @param aclState           ACL 链路状态
     * @param connectedProfiles  已连接 Profile 列表（可为 {@code null} 表示查询失败）
     * @param a2dpState          A2DP Profile 状态（可为 {@code null} 表示未知）
     * @param headsetState       HEADSET Profile 状态（可为 {@code null} 表示未知）
     */
    @NonNull
    public static CbtLinkReadiness of(@Nullable CbtAclLinkState aclState,
                                      @Nullable List<CbtProfileType> connectedProfiles,
                                      @Nullable CbtProfileConnectionState a2dpState,
                                      @Nullable CbtProfileConnectionState headsetState) {
        if (aclState == CbtAclLinkState.CONNECTED) {
            return READY;
        }
        if (a2dpState == CbtProfileConnectionState.CONNECTED
                || headsetState == CbtProfileConnectionState.CONNECTED) {
            return READY;
        }
        if (connectedProfiles != null && !connectedProfiles.isEmpty()) {
            return READY;
        }

        boolean aclDown = aclState == CbtAclLinkState.DISCONNECTED;
        boolean a2dpDown = a2dpState == CbtProfileConnectionState.DISCONNECTED;
        boolean headsetDown = headsetState == CbtProfileConnectionState.DISCONNECTED;
        boolean profilesKnownEmpty = connectedProfiles != null && connectedProfiles.isEmpty();
        if (aclDown && profilesKnownEmpty && (a2dpDown || a2dpState == null)
                && (headsetDown || headsetState == null)) {
            return DOWN;
        }
        return UNKNOWN;
    }
}
