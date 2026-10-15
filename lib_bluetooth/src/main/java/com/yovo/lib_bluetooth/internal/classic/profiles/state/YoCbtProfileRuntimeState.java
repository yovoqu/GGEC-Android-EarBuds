package com.yovo.lib_bluetooth.internal.classic.profiles.state;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Profile 连接运行时状态枚举，为各 Profile 类型提供统一状态词汇。
 */
public enum YoCbtProfileRuntimeState {

    /**
     * Profile 已断开，不存在连接。
     */
    DISCONNECTED,

    /**
     * 正在建立连接。此为过渡状态；
     * Profile 将转为 {@link #CONNECTED} 或回到 {@link #DISCONNECTED}。
     */
    CONNECTING,

    /**
     * Profile 已连接但空闲 &mdash; 无数据流传输或活跃通话进行中。
     */
    CONNECTED,

    /**
     * 正在断开连接。此为过渡状态；
     * Profile 将转为 {@link #DISCONNECTED}。
     */
    DISCONNECTING,

    /**
     * Profile 已连接且正在使用（如 HEADSET/HFP 的活跃 SCO 音频流，
     * 或 SPP 的数据传输）。
     */
    ACTIVE,

    /**
     * Profile 已连接且正在流媒体内容
     * （通常用于 A2DP Profile 播放音频时）。
     */
    PLAYING,

    /**
     * Profile 连接处于错误状态。连接可能意外丢失或在建立过程中失败。
     */
    ERROR,

    /**
     * Profile 状态未知。在尚未确定实际状态时用作默认值或哨兵值。
     */
    UNKNOWN;

    /**
     * 若此状态表示非过渡中的活跃连接状态则返回 {@code true}。
     * <p>
     * 已连接状态包括 {@link #CONNECTED}、{@link #ACTIVE} 和 {@link #PLAYING}。
     *
     * @return 若为稳定已连接状态则为 {@code true}
     */
    public boolean isConnectedState() {
        return this == CONNECTED || this == ACTIVE || this == PLAYING;
    }

    /**
     * 若此状态表示预期将解析为稳定状态的过渡状态则返回 {@code true}。
     * <p>
     * 过渡状态包括 {@link #CONNECTING} 和 {@link #DISCONNECTING}。
     *
     * @return 若为过渡状态则为 {@code true}
     */
    public boolean isTransitionState() {
        return this == CONNECTING || this == DISCONNECTING;
    }
}
