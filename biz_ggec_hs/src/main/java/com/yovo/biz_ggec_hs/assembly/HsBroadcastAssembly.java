package com.yovo.biz_ggec_hs.assembly;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.api.YoHsBroadcastSession;
import com.yovo.biz_ggec_hs.internal.broadcast.HsBroadcastRuntime;
import com.yovo.biz_ggec_hs.model.broadcast.HsBroadcastReport;
import com.yovo.biz_ggec_hs.model.broadcast.HsScanSnapshot;

/**
 * 广播层组合根：门面经此转发到 internal 广播运行时，门面本身不 import internal。
 */
public final class HsBroadcastAssembly {
    private HsBroadcastAssembly() {
    }

    @NonNull
    public static HsBroadcastReport classify(@NonNull HsScanSnapshot snapshot) {
        return HsBroadcastRuntime.classify(snapshot);
    }

    @NonNull
    public static YoHsBroadcastSession openSession() {
        return HsBroadcastRuntime.openSession();
    }
}
