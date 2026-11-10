package com.yovo.biz_ggec_hs.api;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.model.broadcast.HsBroadcastReport;
import com.yovo.biz_ggec_hs.model.broadcast.HsScanSnapshot;

/**
 * 按设备累积广播分片，再交给代际解码器。
 * 扫描响应和广播包分两次到达时，用这个会话把它们合成一次识别。
 */
public interface YoHsBroadcastSession {
    @NonNull
    HsBroadcastReport observe(@NonNull HsScanSnapshot snapshot);

    void clear();
}
