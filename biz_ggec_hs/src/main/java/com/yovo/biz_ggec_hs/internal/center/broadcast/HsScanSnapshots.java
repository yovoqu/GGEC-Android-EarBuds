package com.yovo.biz_ggec_hs.internal.center.broadcast;

import com.yovo.biz_ggec_hs.internal.center.port.HsRawAdvert;

/**
 * 原始广播 → 广播层输入快照。协议侧唯一认识传输层记录的转换点。
 *
 * <p>名称在此归一：空串视为「本帧没有名字」，{@link HsScanSnapshot#deviceName()} 保持可空，
 * 由产品过滤决定最终展示名。</p>
 */
public final class HsScanSnapshots {

    private HsScanSnapshots() {
    }

    public static HsScanSnapshot from(HsRawAdvert advert) {
        if (advert == null) {
            throw new IllegalArgumentException("原始广播不能为空");
        }
        return HsScanSnapshot.of(
                advert.address(),
                advert.serviceUuids(),
                // 快照自身复制字节；这里传递只读视图，避免逐包二次复制。
                advert.rawAdvertisingView(),
                blankToNull(advert.name()));
    }

    private static String blankToNull(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        return name.trim();
    }
}
