package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 实时语音的唯一入口。
 *
 * <p>调用方给出一份 {@link YoRealtimeConfig}，然后只做两件事：通过 {@link Connection#send}
 * 送出 {@link YoRealtimeInput}，从 {@link YoRealtimeListener} 接收 {@link YoRealtimeOutput}。
 * WebSocket 建连与方言 JSON 编解码全部封装在此之后，音频采集、重采样与打断策略交由调用方统筹。</p>
 */
public final class YoRealtime {

    private YoRealtime() {
    }

    /**
     * 打开一条会话。返回时通常尚未完成握手，连接与下行数据通过 {@code listener} 通知。
     * 一个 {@link Connection} 只用于单次通话生命周期。
     */
    @NonNull
    public static Connection open(@NonNull YoRealtimeConfig config, @NonNull YoRealtimeListener listener) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(listener, "listener");
        RealtimeWebSocketLink link = new RealtimeWebSocketLink();
        link.open(config, listener);
        return link;
    }

    /** 一条已打开的实时连接。关闭操作幂等且在握手阶段即可取消。 */
    public interface Connection extends AutoCloseable {

        /** 发送音频或对话控制指令。 */
        void send(@NonNull YoRealtimeInput input);

        @Override
        void close();
    }
}
