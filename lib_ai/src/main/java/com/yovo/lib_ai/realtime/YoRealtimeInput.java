package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * 调用方送进一条实时会话的数据。每种输入只有自己的字段。
 *
 * <p>音频字节必须符合打开会话时声明的上行线格式（默认 24kHz PCM16）。
 * 麦克风的采集格式不进这个类型：重采样在调用方完成。</p>
 */
public sealed interface YoRealtimeInput {

    /** 一段已经按线上格式排好的 PCM。所有权交给会话，会话不再改这个数组。 */
    record Audio(@NonNull byte[] pcm16) implements YoRealtimeInput {
        public Audio {
            Objects.requireNonNull(pcm16, "pcm16");
        }
    }

    /** 打断正在生成的回答。会话会按已经播放的进度自行截断对话史。 */
    record Cancel() implements YoRealtimeInput {
    }
}
