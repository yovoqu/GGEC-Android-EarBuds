package com.ggec.earbuds.domain.device.model;

import java.util.Locale;
import java.util.Objects;

/**
 * 设备身份：型号 + BLE 地址。
 *
 * <p>扫描、连接、档案、台账共用这一个键；{@code classicAddress} 是属性而不是身份，
 * 避免同一台设备出现两个 id。</p>
 *
 * <p>BLE 地址规范化单点在此：去首尾空白、转大写。<b>不</b>去掉分隔符——地址要原样交给
 * 蓝牙栈，改格式会让链路打不通。</p>
 */
public record DeviceId(String modelId, String bleAddress) {

    public DeviceId {
        modelId = Objects.requireNonNull(modelId, "modelId").trim();
        bleAddress = normalize(Objects.requireNonNull(bleAddress, "bleAddress"));
        if (modelId.isEmpty() || bleAddress.isEmpty()) {
            throw new IllegalArgumentException("DeviceId requires modelId and bleAddress");
        }
    }

    public static String normalize(String address) {
        return address.trim().toUpperCase(Locale.ROOT);
    }

    /** 持久化与列表行使用的稳定键。 */
    public String key() {
        return modelId + "@" + bleAddress;
    }

    /** 界面展示形式：分隔符统一成冒号，不改变字节顺序。 */
    public String displayAddress() {
        return bleAddress.replace('-', ':');
    }
}
