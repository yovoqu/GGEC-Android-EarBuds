package com.yovo.biz_ggec_hs.internal.center.broadcast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 广播识别结果。未识别、帧未齐、已识别是三种状态，已识别才带有代际身份。
 */
public final class HsBroadcastReport {
    public enum Status {
        NOT_HS,
        INCOMPLETE,
        IDENTIFIED
    }

    @NonNull
    private final Status status;
    @Nullable
    private final String bleAddress;
    @Nullable
    private final HsGeneration generation;
    @Nullable
    private final HsBroadcastIdentity identity;

    private HsBroadcastReport(@NonNull Status status,
                              @Nullable String bleAddress,
                              @Nullable HsGeneration generation,
                              @Nullable HsBroadcastIdentity identity) {
        this.status = status;
        this.bleAddress = bleAddress;
        this.generation = generation;
        this.identity = identity;
    }

    @NonNull
    public static HsBroadcastReport notHs(@Nullable String bleAddress) {
        return new HsBroadcastReport(Status.NOT_HS, bleAddress, null, null);
    }

    @NonNull
    public static HsBroadcastReport incomplete(@NonNull HsGeneration generation,
                                               @Nullable String bleAddress) {
        return new HsBroadcastReport(Status.INCOMPLETE, bleAddress, generation, null);
    }

    @NonNull
    public static HsBroadcastReport identified(@NonNull HsBroadcastIdentity identity) {
        return new HsBroadcastReport(
                Status.IDENTIFIED, identity.bleAddress(), identity.generation(), identity);
    }

    @NonNull
    public Status status() {
        return status;
    }

    public boolean isHsDevice() {
        return status == Status.IDENTIFIED;
    }

    @Nullable
    public String bleAddress() {
        return bleAddress;
    }

    @Nullable
    public HsGeneration generation() {
        return generation;
    }

    @Nullable
    public HsBroadcastIdentity identity() {
        return identity;
    }
}
