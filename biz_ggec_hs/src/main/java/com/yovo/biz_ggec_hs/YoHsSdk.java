package com.yovo.biz_ggec_hs;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.api.YoHsClient;
import com.yovo.biz_ggec_hs.internal.graph.HsGraph;

/**
 * HS 会话入口。每次 {@link #create(Context)} 独占一条 BLE 客户端。
 * 广播识别走 {@link YoHsBroadcast}，不从这里进入。
 */
public final class YoHsSdk {
    private YoHsSdk() {
    }

    @NonNull
    public static YoHsClient create(@NonNull Context context) {
        return HsGraph.open(context);
    }
}
