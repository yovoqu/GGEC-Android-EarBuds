package com.yovo.biz_ggec_hs.internal.broadcast;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * 广播代际的组合点。后续产品在这里登记一个解码器，排在前面的先认领。
 */
final class HsBroadcastRegistry {
    private HsBroadcastRegistry() {
    }

    @NonNull
    static HsBroadcastClassifier standard() {
        return new HsBroadcastClassifier(List.of(
                new Hs02BroadcastDecoder(),
                new Hs01BroadcastDecoder()));
    }
}
