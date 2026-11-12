package com.yovo.biz_ggec_hs;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.api.YoHsBroadcastSession;
import com.yovo.biz_ggec_hs.assembly.HsBroadcastAssembly;
import com.yovo.biz_ggec_hs.model.broadcast.HsBroadcastReport;
import com.yovo.biz_ggec_hs.model.broadcast.HsScanSnapshot;

/**
 * 广播层入口。只判断一帧或一条扫描流是不是 HS 设备，并区分代际，不打开连接。
 * {@link #classify(HsScanSnapshot)} 看单次快照。广播和扫描响应分两次到达时，用 {@link #openSession()}。
 */
public final class YoHsBroadcast {
    private YoHsBroadcast() {
    }

    @NonNull
    public static HsBroadcastReport classify(@NonNull HsScanSnapshot snapshot) {
        return HsBroadcastAssembly.classify(snapshot);
    }

    @NonNull
    public static YoHsBroadcastSession openSession() {
        return HsBroadcastAssembly.openSession();
    }
}
