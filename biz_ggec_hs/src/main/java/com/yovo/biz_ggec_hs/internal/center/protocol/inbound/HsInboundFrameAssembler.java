package com.yovo.biz_ggec_hs.internal.center.protocol.inbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.yovo.biz_ggec_hs.internal.log.HsLog;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;

/**
 * 入站流式拼帧器（H-03）。
 *
 * <p>BLE 通知可能在任意字节边界分片/粘包。此组件把逐包 hex 追加到缓冲区，按协议信封
 * （6 位命令前缀 + 4 位长度 + 载荷 + "1234" 尾标）切出完整帧后交给 {@code InboundFrameDemux}。</p>
 *
 * <p>已知例外：组合电量上报 {@code 99EC800003…} 的长度字段按电池条目数计、帧长固定 26 字符，
 * 与标准"按字节长度"不同，单独处理。无法对齐时按 1 字节重同步，避免死循环与丢帧。</p>
 */
public final class HsInboundFrameAssembler {

    private static final String TAG = "HsInboundFrame";
    private static final String COMBINED_BATTERY_PREFIX = "99EC800003";
    private static final int COMBINED_BATTERY_FRAME_CHARS = 26;
    private static final int MAX_BUFFER_CHARS = 8192;
    private static final int MAX_FRAMES_PER_FEED = 128;

    private enum Step { FRAME, NEED_MORE, RESYNC }

    private final StringBuilder buffer = new StringBuilder();

    /** 追加一个 hex 分片，返回本次可切出的完整帧（可能 0..N 个）。 */
    public synchronized List<String> accept(String hexChunk) {
        if (hexChunk == null || hexChunk.isEmpty()) {
            return List.of();
        }
        buffer.append(hexChunk.toUpperCase(Locale.ROOT));
        if (buffer.length() > MAX_BUFFER_CHARS) {
            buffer.delete(0, buffer.length() - MAX_BUFFER_CHARS);
            HsLog.w(TAG, "入站缓冲超限，已截断旧数据");
        }
        if ((buffer.length() & 1) != 0) {
            // 设备按字节投递，hex 长度理论上恒为偶数；奇数说明上游编码异常，丢弃 1 字符自愈。
            buffer.deleteCharAt(buffer.length() - 1);
        }
        List<String> frames = new ArrayList<>();
        while (frames.size() < MAX_FRAMES_PER_FEED) {
            int before = buffer.length();
            Step step = step(frames);
            if (step == Step.NEED_MORE) {
                break;
            }
            if (step == Step.RESYNC) {
                if (buffer.length() == before) {
                    break;
                }
                continue;
            }
        }
        return frames;
    }

    public synchronized void reset() {
        buffer.setLength(0);
    }

    private Step step(List<String> frames) {
        int available = buffer.length();
        if (available < 10) {
            return Step.NEED_MORE;
        }
        int expected;
        if (buffer.substring(0, 10).equalsIgnoreCase(COMBINED_BATTERY_PREFIX)) {
            expected = COMBINED_BATTERY_FRAME_CHARS;
        } else {
            int payloadBytes;
            try {
                payloadBytes = Integer.parseInt(buffer.substring(6, 10), 16);
            } catch (NumberFormatException malformed) {
                return resync();
            }
            expected = 14 + payloadBytes * 2;
            if (expected < 14 || expected > MAX_BUFFER_CHARS) {
                return resync();
            }
        }
        if (available < expected) {
            return Step.NEED_MORE;
        }
        String candidate = buffer.substring(0, expected);
        if (ProtocolValues.isProtocolFrame(candidate)) {
            buffer.delete(0, expected);
            frames.add(candidate);
            return Step.FRAME;
        }
        return resync();
    }

    private Step resync() {
        if (buffer.length() >= 2) {
            buffer.delete(0, 2);
            return Step.RESYNC;
        }
        return Step.NEED_MORE;
    }
}
