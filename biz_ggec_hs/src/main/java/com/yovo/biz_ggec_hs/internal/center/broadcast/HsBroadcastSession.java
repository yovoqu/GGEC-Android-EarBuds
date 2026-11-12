package com.yovo.biz_ggec_hs.internal.center.broadcast;

/**
 * 按设备累积广播分片，再交给代际解码器。
 *
 * <p>扫描响应与广播包分两次到达时，用这个会话把它们合成一次识别。它是模块内能力，
 * 不对外暴露。</p>
 */
public interface HsBroadcastSession {

    HsBroadcastReport observe(HsScanSnapshot snapshot);

    void clear();
}
