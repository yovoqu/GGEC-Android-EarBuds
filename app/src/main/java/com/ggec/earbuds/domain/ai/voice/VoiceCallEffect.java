package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** reducer 计算出的副作用。状态机不直接执行 IO，由 actor 按顺序执行。 */
public sealed interface VoiceCallEffect {

    /** 发布一次对外快照。 */
    record Publish() implements VoiceCallEffect {
    }

    record StartCapture() implements VoiceCallEffect {
    }

    record StopCapture() implements VoiceCallEffect {
    }

    /** 上行闸门：静音或半双工自听期间关掉，麦克风仍在读数（电平还在动）。 */
    record SetUplinkEnabled(boolean enabled) implements VoiceCallEffect {
    }

    record StartPlayback(int sampleRateHz) implements VoiceCallEffect {
    }

    /** 写一段音频；带 itemId 才能在打断时截断到正确的条目。 */
    record PlayAudio(@NonNull byte[] pcm16, @Nullable String itemId, int contentIndex)
            implements VoiceCallEffect {
    }

    /**
     * 停止当前这一轮助手发言：本地停播 + （需要时）让服务端取消并截断。
     *
     * <p><b>为什么合成一个 effect 而不是拆成三条</b>：截断需要的"用户听到多少毫秒"
     * 只能由播放端口给出，而 reducer 不碰 IO。拆开的话 reducer 得凭空编一个毫秒数，
     * 或者让 actor 在两条 effect 之间偷偷共享状态——两者都会让"截断毫秒"失真。
     * 合成之后 actor 内部顺序是固定的：</p>
     *
     * <ol>
     *   <li>先从播放端口取已播进度（播放线程在记账，此时还没清队列）；</li>
     *   <li>清空播放队列；</li>
     *   <li>需要时发 {@code response.cancel} + {@code conversation.item.truncate}；</li>
     *   <li>丢掉未提交的上行缓冲。</li>
     * </ol>
     *
     * @param cancelGeneration 是否还有"正在生成"的回答要取消。
     *                         已经生成完（DRAINING）时传 false：通义对没有活动响应的
     *                         {@code response.cancel} 会回错误事件，OpenAI 也会回空操作。
     */
    record StopAssistant(boolean cancelGeneration) implements VoiceCallEffect {
    }

    record StopPlayback() implements VoiceCallEffect {
    }

    record CloseLink() implements VoiceCallEffect {
    }

    /**
     * 本地播放排空后投递 {@link VoiceCallEvent.PlaybackDrained}。
     *
     * <p>为什么用调度器而不是靠采集帧轮询：半双工下播放期间上行是关的，
     * 采集帧可能不再被状态机处理；靠帧驱动会漏掉排空事件，通话就卡在 DRAINING。</p>
     */
    record ScheduleDrain(long delayMillis) implements VoiceCallEffect {
    }
}
