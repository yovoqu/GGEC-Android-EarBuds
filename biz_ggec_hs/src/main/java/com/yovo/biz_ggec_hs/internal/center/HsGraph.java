package com.yovo.biz_ggec_hs.internal.center;

import android.content.Context;

import com.yovo.biz_ggec_hs.internal.center.catalog.HsCatalogImpl;
import com.yovo.biz_ggec_hs.internal.scan.HsNearbyImpl;
import com.yovo.biz_ggec_hs.internal.link.HsSessionImpl;
import com.yovo.biz_ggec_hs.internal.center.protocol.HsProtocolEngine;
import com.yovo.biz_ggec_hs.provider.lib_bluetooth.HsNearbyScanner;
import com.yovo.biz_ggec_hs.provider.lib_bluetooth.YoBleCommandLink;

/**
 * 组合根：把 provider 的链路适配、中层协议引擎、上层会话/扫描与设备目录装在一起。
 *
 * <p>这是模块里唯一构造 {@code provider/**} 的地方。对外只经过 {@code YoHsSdk}。</p>
 */
public final class HsGraph {

    private final HsNearbyImpl nearby;
    private final HsSessionImpl session;
    private final HsCatalogImpl catalog;

    private HsGraph(HsNearbyImpl nearby, HsSessionImpl session, HsCatalogImpl catalog) {
        this.nearby = nearby;
        this.session = session;
        this.catalog = catalog;
    }

    public static HsGraph open(Context context) {
        YoBleCommandLink link = YoBleCommandLink.open(context);
        HsProtocolEngine engine = new HsProtocolEngine(link);
        HsCatalogImpl catalog = new HsCatalogImpl();
        return new HsGraph(
                HsNearbyImpl.open(new HsNearbyScanner(link.bleClient()), catalog),
                new HsSessionImpl(engine, catalog),
                catalog);
    }

    public HsNearbyImpl scan() {
        return nearby;
    }

    public HsSessionImpl link() {
        return session;
    }

    public HsCatalogImpl catalog() {
        return catalog;
    }

    /** 先停扫描再释放链路：扫描句柄与连接句柄共用同一条 BLE 客户端。 */
    public void close() {
        nearby.stop();
        session.close();
    }
}
