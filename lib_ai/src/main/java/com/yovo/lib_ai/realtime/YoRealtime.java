package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 实时语音的唯一入口。
 *
 * <p>调用方给出一份 {@link YoRealtimeConfig}，然后只做两件事：{@link Connection#send}
 * 送 {@link YoRealtimeInput}，从 {@link YoRealtimeListener} 收 {@link YoRealtimeOutput}。
 * WebSocket、方言 JSON、打断时的截断都在这一次打开里装好，不交给调用方拼。</p>
 */
public final class YoRealtime {

    private YoRealtime() {
    }

    /**
     * 打开一条会话。返回时通常还没连上，结果走 {@code listener}。
     * 一个 {@link Connection} 只能用到关闭为止，下一通电话再打开一条。
     */
    @NonNull
    public static Connection open(@NonNull YoRealtimeConfig config, @NonNull YoRealtimeListener listener) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(listener, "listener");
        YoRealtimePlayback playback = config.countPlaybackOnArrival()
                ? YoRealtimePlayback.onArrival() : YoRealtimePlayback.manual();
        YoRealtimeSession session = new YoRealtimeSession(
                new RealtimeWebSocketLink(), playback, config.interruptOnSpeech());
        session.open(config, event -> {
            YoRealtimeOutput output = YoRealtimeOutputs.from(event);
            if (output != null) {
                listener.onOutput(output);
            }
        });
        return new Connection(session);
    }

    /** 一条已经打开的会话。关闭是幂等的。 */
    public static final class Connection implements AutoCloseable {

        private final YoRealtimeSession session;

        private Connection(YoRealtimeSession session) {
            this.session = session;
        }


        /**
         * 手动记播放进度。只在 {@code countPlaybackOnArrival(false)} 时使用：
         * 播放线程真正写出一段 PCM 之后调这里，打断时截断的毫秒数才是"用户听到的"。
         */
        public void notePlayed(@Nullable String itemId, int contentIndex, int pcmBytes) {
            session.notePlayed(itemId, contentIndex, pcmBytes);
        }

        /** 把对话史里用户没听到的那一段截掉（{@code conversation.item.truncate}）。 */
        public void truncate(@NonNull String itemId, int contentIndex, int audioEndMs) {
            session.truncate(itemId, contentIndex, audioEndMs);
        }

        public void send(@NonNull YoRealtimeInput input) {
            Objects.requireNonNull(input, "input");
            switch (input) {
                case YoRealtimeInput.Audio audio -> session.appendAudio(audio.pcm16());
                case YoRealtimeInput.Cancel ignored -> session.cancelResponse();
                case YoRealtimeInput.Commit ignored -> session.commitInput();
                case YoRealtimeInput.Clear ignored -> session.clearInput();
                case YoRealtimeInput.CreateResponse ignored -> session.createResponse();
                case YoRealtimeInput.ToolOutput tool -> session.submitToolOutput(tool.callId(), tool.output());
            }
        }

        @Override
        public void close() {
            session.close();
        }
    }
}
