package com.yovo.biz_ggec_hs.internal.graph;

import android.content.Context;

import com.yovo.biz_ggec_hs.api.YoHsClient;
import com.yovo.biz_ggec_hs.internal.protocol.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.session.YoHsClientImpl;
import com.yovo.biz_ggec_hs.provider.lib_bluetooth.YoBleCommandLink;

/**
 * 组装协议运行时和 BLE 适配器。协议包不引用这里。
 */
public final class HsGraph {
    private HsGraph() {
    }

    public static YoHsClient open(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context 不能为空");
        }
        HsProtocolEngine engine = new HsProtocolEngine(YoBleCommandLink.open(context));
        return new YoHsClientImpl(engine);
    }
}
