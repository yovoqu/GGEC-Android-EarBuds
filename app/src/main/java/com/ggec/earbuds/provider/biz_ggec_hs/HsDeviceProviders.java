package com.ggec.earbuds.provider.biz_ggec_hs;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.yovo.biz_ggec_hs.YoHsSdk;

import java.util.Objects;

/**
 * 一次 {@code YoHsSdk} 实例的生命周期持有者，把同一个 SDK 实例装配成本模块的两个端口。
 *
 * <p>{@code YoHsSdk.open(context)} 只创建一条 BLE 客户端，附近扫描与会话共用它；因此
 * 链路端口与能力目录必须来自同一个实例，不能各开一个 SDK。装配根只拿本类，
 * 不接触任何 SDK 类型。</p>
 *
 * <p>关闭顺序：先摘掉端口的订阅与扫描，再关闭 SDK。</p>
 */
public final class HsDeviceProviders {

    private final YoHsSdk sdk;
    private final HsDeviceLinkAdapter link;
    private final HsCapabilityCatalogAdapter catalog;

    private HsDeviceProviders(@NonNull YoHsSdk sdk) {
        this.sdk = Objects.requireNonNull(sdk, "sdk");
        this.link = new HsDeviceLinkAdapter(sdk);
        this.catalog = new HsCapabilityCatalogAdapter(sdk);
    }

    @NonNull
    public static HsDeviceProviders open(@NonNull Context context) {
        return new HsDeviceProviders(YoHsSdk.open(context));
    }

    @NonNull
    public DeviceLinkPort deviceLink() {
        return link;
    }

    @NonNull
    public DeviceCapabilityCatalog deviceCatalog() {
        return catalog;
    }

    public void close() {
        link.close();
        sdk.close();
    }
}
