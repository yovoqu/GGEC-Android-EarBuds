package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 用户已经听到了多少。打断时把这个毫秒数放进 {@code conversation.item.truncate}。
 *
 * <p><b>为什么不能把「已经收到的字节」当成「已经听到的」</b>：模型出声比喇叭快，
 * 队列里可能还堆着好几秒。按收到的字节截断，服务端会以为那些还在队列里的声音用户已经听到了。
 * 耳机播放有缓冲时用 {@link #manual()}，在真正写出喇叭之后再 {@code onPlayed}。
 * {@link #onArrival()} 只适合缓冲可以忽略的链路，它比「完全不截断」好，但仍然偏长。</p>
 */
public final class YoRealtimePlayback {

    /** 已经听到的那一条。没有在播的音频时是空的。 */
    public static final class State {
        @NonNull
        private final String itemId;
        private final int contentIndex;
        private final int elapsedMs;

        State(@NonNull String itemId, int contentIndex, int elapsedMs) {
            this.itemId = itemId;
            this.contentIndex = contentIndex;
            this.elapsedMs = elapsedMs;
        }

        @NonNull
        public String itemId() {
            return itemId;
        }

        public int contentIndex() {
            return contentIndex;
        }

        public int elapsedMs() {
            return elapsedMs;
        }
    }

    private final boolean countOnArrival;
    private final Object lock = new Object();

    @NonNull
    private YoRealtimeAudioFormat format = YoRealtimeAudioFormat.pcm16(
            YoRealtimeConfig.DEFAULT_OUTPUT_SAMPLE_RATE_HZ);
    @Nullable
    private String itemId;
    private int contentIndex;
    private long playedBytes;

    private YoRealtimePlayback(boolean countOnArrival) {
        this.countOnArrival = countOnArrival;
    }

    /**
     * 音频事件一到就记进度。缓冲很短时够用。
     */
    @NonNull
    public static YoRealtimePlayback onArrival() {
        return new YoRealtimePlayback(true);
    }

    /**
     * 只认 {@link #onPlayed}。播放线程写出一段，再记一段。
     */
    @NonNull
    public static YoRealtimePlayback manual() {
        return new YoRealtimePlayback(false);
    }

    public boolean countsOnArrival() {
        return countOnArrival;
    }

    public void setFormat(@NonNull YoRealtimeAudioFormat format) {
        synchronized (lock) {
            this.format = format;
        }
    }

    /**
     * 记入已经播放的 PCM 字节。{@code itemId} 为空时忽略：没有条目 id 就无法截断。
     * 换了一条回答时进度从 0 重新累加，不能把上一句的毫秒加到这一句上。
     */
    public void onPlayed(@Nullable String itemId, int contentIndex, int pcmBytes) {
        if (itemId == null || itemId.isBlank() || pcmBytes <= 0) {
            return;
        }
        synchronized (lock) {
            if (!itemId.equals(this.itemId) || contentIndex != this.contentIndex) {
                this.itemId = itemId;
                this.contentIndex = contentIndex;
                playedBytes = 0;
            }
            playedBytes += pcmBytes;
        }
    }

    /**
     * 取出当前进度并清零。打断只该截断一次；第二次再取应当是空的。
     */
    @Nullable
    public State captureAndClear() {
        synchronized (lock) {
            if (itemId == null || playedBytes <= 0) {
                itemId = null;
                playedBytes = 0;
                return null;
            }
            int frames = format.bytesPerFrame();
            int elapsed = (int) Math.min(
                    Integer.MAX_VALUE,
                    playedBytes / frames * 1000L / format.sampleRateHz());
            State state = new State(itemId, contentIndex, elapsed);
            itemId = null;
            playedBytes = 0;
            return state;
        }
    }
}
