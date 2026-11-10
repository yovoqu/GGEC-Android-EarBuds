package com.yovo.biz_ggec_hs.model.broadcast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * HS01 广播身份。来自 128-bit Service UUID 中的特征码、颜色和经典地址。
 */
public final class Hs01Identity implements HsBroadcastIdentity {
    @Nullable
    private final String bleAddress;
    @NonNull
    private final String classicAddress;
    @NonNull
    private final String color;
    @NonNull
    private final String featureCode;
    @NonNull
    private final UUID serviceUuid;
    private final int marketId;

    public Hs01Identity(@Nullable String bleAddress,
                        @NonNull String classicAddress,
                        @NonNull String color,
                        @NonNull String featureCode,
                        @NonNull UUID serviceUuid,
                        int marketId) {
        this.bleAddress = bleAddress;
        this.classicAddress = classicAddress;
        this.color = color;
        this.featureCode = featureCode;
        this.serviceUuid = serviceUuid;
        this.marketId = marketId;
    }

    @NonNull
    @Override
    public HsGeneration generation() {
        return HsGeneration.HS01;
    }

    @Nullable
    @Override
    public String bleAddress() {
        return bleAddress;
    }

    @NonNull
    @Override
    public String classicAddress() {
        return classicAddress;
    }

    @Override
    public int marketId() {
        return marketId;
    }

    @NonNull
    @Override
    public String color() {
        return color;
    }

    @Override
    public boolean headset() {
        return true;
    }

    @NonNull
    public String featureCode() {
        return featureCode;
    }

    @NonNull
    public UUID serviceUuid() {
        return serviceUuid;
    }
}
