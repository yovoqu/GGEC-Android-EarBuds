package com.yovo.biz_ggec_hs.internal.graph;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_hs.api.YoHsClient;
import com.yovo.biz_ggec_hs.internal.protocol.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.session.YoHsClientImpl;
import com.yovo.biz_ggec_hs.provider.lib_bluetooth.YoBleCommandLink;

/**
 * 用已经打开的 BLE 命令链路组装协议会话。协议包不引用这里。
 */
public final class HsGraph {
    private HsGraph() {
    }

    public static YoHsClient open(@NonNull YoBleCommandLink link) {
        return new YoHsClientImpl(new HsProtocolEngine(link));
    }
}
