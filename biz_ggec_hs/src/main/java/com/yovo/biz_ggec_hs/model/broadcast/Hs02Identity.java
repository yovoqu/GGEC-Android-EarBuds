package com.yovo.biz_ggec_hs.model.broadcast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * HS02 广播身份。来自厂商数据：设备类型、机型、颜色、经典地址和市场号。
 */
public final class Hs02Identity implements HsBroadcastIdentity {
    /** 协议 Device Type：耳机。 */
    public static final int DEVICE_TYPE_HEADSET = 0x02;

    @Nullable
    private final String bleAddress;
    @NonNull
    private final String classicAddress;
    @Nullable
    private final String name;
    @NonNull
    private final String color;
    private final int marketId;
    private final int deviceType;
    private final int productModel;
    private final boolean pairable;
    private final boolean reconnect;

    public Hs02Identity(@Nullable String bleAddress,
                        @NonNull String classicAddress,
                        @Nullable String name,
                        @NonNull String color,
                        int marketId,
                        int deviceType,
                        int productModel,
                        boolean pairable,
                        boolean reconnect) {
        this.bleAddress = bleAddress;
        this.classicAddress = classicAddress;
        this.name = name;
        this.color = color;
        this.marketId = marketId;
        this.deviceType = deviceType;
        this.productModel = productModel;
        this.pairable = pairable;
        this.reconnect = reconnect;
    }

    @NonNull
    @Override
    public HsGeneration generation() {
        return HsGeneration.HS02;
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
        return deviceType == DEVICE_TYPE_HEADSET;
    }

    @Nullable
    public String name() {
        return name;
    }

    public int deviceType() {
        return deviceType;
    }

    public int productModel() {
        return productModel;
    }

    public boolean pairable() {
        return pairable;
    }

    public boolean reconnect() {
        return reconnect;
    }
}
