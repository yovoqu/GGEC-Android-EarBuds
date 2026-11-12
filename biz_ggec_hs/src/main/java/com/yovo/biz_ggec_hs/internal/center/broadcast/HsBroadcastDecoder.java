package com.yovo.biz_ggec_hs.internal.center.broadcast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.internal.center.broadcast.HsBroadcastIdentity;
import com.yovo.biz_ggec_hs.internal.center.broadcast.HsGeneration;
import com.yovo.biz_ggec_hs.internal.center.broadcast.HsScanSnapshot;

/**
 * 一代广播协议的解码器。认领失败返回 {@link Claim.Kind#UNRECOGNIZED}，
 * 认领后帧未齐返回 {@link Claim.Kind#INCOMPLETE}，齐了才给出该代际自己的身份。
 */
interface HsBroadcastDecoder {
    @NonNull
    HsGeneration generation();

    @NonNull
    Claim claim(@NonNull HsScanSnapshot snapshot);

    final class Claim {
        enum Kind {
            UNRECOGNIZED,
            INCOMPLETE,
            IDENTIFIED
        }

        @NonNull
        private final Kind kind;
        @Nullable
        private final HsBroadcastIdentity identity;

        private Claim(@NonNull Kind kind, @Nullable HsBroadcastIdentity identity) {
            this.kind = kind;
            this.identity = identity;
        }

        @NonNull
        static Claim unrecognized() {
            return new Claim(Kind.UNRECOGNIZED, null);
        }

        @NonNull
        static Claim incomplete() {
            return new Claim(Kind.INCOMPLETE, null);
        }

        @NonNull
        static Claim identified(@NonNull HsBroadcastIdentity identity) {
            return new Claim(Kind.IDENTIFIED, identity);
        }

        @NonNull
        Kind kind() {
            return kind;
        }

        @Nullable
        HsBroadcastIdentity identity() {
            return identity;
        }
    }
}
