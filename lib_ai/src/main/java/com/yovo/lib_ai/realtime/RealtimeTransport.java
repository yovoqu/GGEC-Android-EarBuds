package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * Realtime 传输方式抽象。本轮只落地接口与能力位，不改变既有 WebSocket 行为：
 * <ul>
 *   <li>{@link WebSocket} 是默认装配，{@link #defaultEnabled()} 为 {@code true}；</li>
 *   <li>{@link WebRtc}、{@link Aoq} 是占位，不参与默认装配（{@link #defaultEnabled()} 为 {@code false}）。</li>
 * </ul>
 *
 * <p>为什么单独抽这一层：全双工语音有 WebSocket / WebRTC / AOQ 三种承载，切换承载不该
 * 牵动 {@link YoRealtimeConfig} 与协议编解码。把"哪种承载、是否默认启用"收进一个稳定接口，
 * 后续接 WebRTC 时只需新增实现，不改调用方。
 */
public sealed interface RealtimeTransport {

    /** 稳定标识，用于持久化与日志（不随枚举重命名而变）。 */
    @NonNull
    String id();

    /** 是否参与默认装配。只有 WebSocket 为 {@code true}。 */
    boolean defaultEnabled();

    /** 现有 WebSocket 承载（默认启用）。 */
    record WebSocket() implements RealtimeTransport {
        @NonNull
        @Override
        public String id() {
            return "websocket";
        }

        @Override
        public boolean defaultEnabled() {
            return true;
        }
    }

    /** WebRTC 承载（占位，默认不启用）。 */
    record WebRtc() implements RealtimeTransport {
        @NonNull
        @Override
        public String id() {
            return "webrtc";
        }

        @Override
        public boolean defaultEnabled() {
            return false;
        }
    }

    /** AOQ 承载（占位，默认不启用）。 */
    record Aoq() implements RealtimeTransport {
        @NonNull
        @Override
        public String id() {
            return "aoq";
        }

        @Override
        public boolean defaultEnabled() {
            return false;
        }
    }

    @NonNull
    static RealtimeTransport webSocket() {
        return new WebSocket();
    }

    @NonNull
    static RealtimeTransport webRtc() {
        return new WebRtc();
    }

    @NonNull
    static RealtimeTransport aoq() {
        return new Aoq();
    }

    /** 全部承载（含占位）。 */
    @NonNull
    static List<RealtimeTransport> all() {
        return Arrays.asList(new WebSocket(), new WebRtc(), new Aoq());
    }

    /** 默认装配的承载：WebSocket。 */
    @NonNull
    static RealtimeTransport defaultTransport() {
        return new WebSocket();
    }
}
