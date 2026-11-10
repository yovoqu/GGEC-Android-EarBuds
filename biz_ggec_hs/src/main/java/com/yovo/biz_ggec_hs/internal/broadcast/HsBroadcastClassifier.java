package com.yovo.biz_ggec_hs.internal.broadcast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.model.broadcast.HsBroadcastReport;
import com.yovo.biz_ggec_hs.model.broadcast.HsScanSnapshot;

import java.util.List;

/**
 * 按注册顺序询问解码器。先认领的代际获胜，后面的解码器不再看这一帧。
 */
final class HsBroadcastClassifier {
    @NonNull
    private final List<HsBroadcastDecoder> decoders;

    HsBroadcastClassifier(@NonNull List<HsBroadcastDecoder> decoders) {
        this.decoders = List.copyOf(decoders);
    }

    @NonNull
    HsBroadcastReport classify(@Nullable HsScanSnapshot snapshot) {
        if (snapshot == null) {
            return HsBroadcastReport.notHs(null);
        }
        for (HsBroadcastDecoder decoder : decoders) {
            HsBroadcastDecoder.Claim claim = decoder.claim(snapshot);
            switch (claim.kind()) {
                case UNRECOGNIZED:
                    break;
                case INCOMPLETE:
                    return HsBroadcastReport.incomplete(decoder.generation(), snapshot.bleAddress());
                case IDENTIFIED:
                    return HsBroadcastReport.identified(claim.identity());
                default:
                    break;
            }
        }
        return HsBroadcastReport.notHs(snapshot.bleAddress());
    }
}
