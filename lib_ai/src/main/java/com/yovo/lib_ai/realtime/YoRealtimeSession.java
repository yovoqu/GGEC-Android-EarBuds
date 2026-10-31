package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 传输之上的会话：播放进度、插话截断、工具结果。
 *
 * <p>它实现 {@link YoRealtimeLink}，所以原来拿着链路的调用方可以换成它，
 * 收发音频的方式不变。多出来的行为只有两件：</p>
 * <ol>
 *   <li>插话或 {@link #cancelResponse()} 时，除了 {@code response.cancel}，
 *       再按已经听到的毫秒发 {@code conversation.item.truncate}。
 *       不截断的话，服务端对话史里留着用户没听到的后半句，下一轮会接着那半句答。</li>
 *   <li>{@link #submitToolResult} 写回函数结果并立刻 {@code response.create}，
 *       否则模型拿到结果也不会开口。</li>
 * </ol>
 *
 * <p>麦克风和喇叭仍在调用方。默认按「音频一到就算听到」计进度，适合缓冲很短的链路；
 * 耳机播放队列很长时，构造时传入 {@link YoRealtimePlayback#manual()}，
 * 在真正写出喇叭之后调用 {@link #notePlayed}。</p>
 */
final class YoRealtimeSession implements YoRealtimeLink {

    @NonNull
    private final YoRealtimeLink transport;
    @NonNull
    private final YoRealtimePlayback playback;
    private final boolean interruptOnSpeech;
    private final Object bargeLock = new Object();

    public YoRealtimeSession(@NonNull YoRealtimeLink transport) {
        this(transport, YoRealtimePlayback.onArrival(), true);
    }

    public YoRealtimeSession(
            @NonNull YoRealtimeLink transport,
            @NonNull YoRealtimePlayback playback,
            boolean interruptOnSpeech) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.playback = Objects.requireNonNull(playback, "playback");
        this.interruptOnSpeech = interruptOnSpeech;
    }

    @NonNull
    public YoRealtimePlayback playback() {
        return playback;
    }

    @Override
    public void open(@NonNull YoRealtimeConfig config, @NonNull RealtimeSink listener) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(listener, "listener");
        playback.setFormat(config.outputWireFormat());
        transport.open(config, event -> {
            observe(event);
            listener.onEvent(event);
        });
    }

    @Override
    public void appendAudio(@NonNull byte[] pcm16) {
        transport.appendAudio(pcm16);
    }

    @Override
    public void cancelResponse() {
        bargeIn();
    }

    @Override
    public void commitInput() {
        transport.commitInput();
    }

    @Override
    public void clearInput() {
        transport.clearInput();
    }

    @Override
    public void createResponse() {
        transport.createResponse();
    }

    @Override
    public void truncate(@NonNull String itemId, int contentIndex, int audioEndMs) {
        transport.truncate(itemId, contentIndex, audioEndMs);
    }

    @Override
    public void submitToolOutput(@NonNull String callId, @NonNull String output) {
        transport.submitToolOutput(callId, output);
    }

    /**
     * 写回一个函数结果并请模型开口。多个函数要一起说时，先多次
     * {@link #submitToolOutput}，最后再 {@link #createResponse()} 一次。
     */
    public void submitToolResult(@NonNull String callId, @NonNull String output) {
        transport.submitToolOutput(callId, output);
        transport.createResponse();
    }

    /**
     * 手动播放进度。只在 {@link YoRealtimePlayback#manual()} 下使用；
     * 到达即计的播放器自己会记，再调一次会把同一段音频算两遍。
     */
    public void notePlayed(@Nullable String itemId, int contentIndex, int pcmBytes) {
        playback.onPlayed(itemId, contentIndex, pcmBytes);
    }

    @Override
    public void close() {
        transport.close();
    }

    private void observe(@NonNull YoRealtimeEvent event) {
        if (event.type() == YoRealtimeEventType.ASSISTANT_AUDIO_CHUNK && playback.countsOnArrival()) {
            byte[] pcm = event.audioPcm16();
            int index = event.contentIndex() == null ? 0 : event.contentIndex();
            playback.onPlayed(event.itemId(), index, pcm == null ? 0 : pcm.length);
        }
        if (interruptOnSpeech && event.type() == YoRealtimeEventType.INPUT_SPEECH_STARTED) {
            bargeIn();
        }
    }

    /**
     * 先取进度再清掉，所以按钮打断和 VAD 打断撞在一起时只会截断一次。
     * 没有 item id（老网关的音频帧不带）时只 cancel，和以前的行为一样。
     */
    private void bargeIn() {
        YoRealtimePlayback.State heard;
        synchronized (bargeLock) {
            heard = playback.captureAndClear();
        }
        transport.cancelResponse();
        if (heard != null) {
            transport.truncate(heard.itemId(), heard.contentIndex(), heard.elapsedMs());
        }
    }
}
