package com.ggec.earbuds.wiring.device;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.DeviceTransport;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.domain.device.port.DeviceLinkPort;
import com.ggec.earbuds.domain.device.port.RecordPort;

/**
 * 一个 SDK 实例暴露出的接入端点：一种传输 + 该传输的目录、链路与录音端口。
 *
 * <p>端点只描述「这一个 SDK 能做什么」，不认识其它端点，也没有机型白名单：哪些机型在谁的
 * 目录里，由 {@link DeviceEndpoints} 在构造期索引一次。装配层内部类型，不出现在
 * {@code ui}/{@code domain} 的公共签名里。</p>
 */
public interface DeviceEndpoint {

    /** 本端点承载的无线传输；连接路由按它选择端点。 */
    @NonNull
    DeviceTransport transport();

    /** 本端点的机型能力目录。 */
    @NonNull
    DeviceCapabilityCatalog catalog();

    /** 本端点的扫描与链路端口。 */
    @NonNull
    DeviceLinkPort link();

    /** 本端点的录音端口。 */
    @NonNull
    RecordPort record();

    /** 释放本端点持有的 SDK 资源（摘订阅、停扫描、关 SDK）。 */
    void close();
}
