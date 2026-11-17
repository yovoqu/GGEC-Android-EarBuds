package com.yovo.biz_ggec_hs.internal.center;

import android.content.Context;

import com.yovo.biz_ggec_hs.internal.center.catalog.HsCatalogImpl;
import com.yovo.biz_ggec_hs.internal.interact.HsCapabilityRouter;
import com.yovo.biz_ggec_hs.internal.scan.HsNearbyImpl;
import com.yovo.biz_ggec_hs.internal.link.HsConnectionIntake;
import com.yovo.biz_ggec_hs.internal.link.HsSessionImpl;
import com.yovo.biz_ggec_hs.internal.center.link.HsClassicConnect;
import com.yovo.biz_ggec_hs.internal.center.protocol.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.link.HsMainDelay;
import com.yovo.biz_ggec_hs.internal.record.HsRecordManager;
import com.yovo.biz_ggec_hs.internal.record.HsRecordPathResolver;
import com.yovo.biz_ggec_hs.provider.lib_bluetooth.HsNearbyScanner;
import com.yovo.biz_ggec_hs.provider.lib_bluetooth.YoBleAudioDataLink;
import com.yovo.biz_ggec_hs.provider.lib_bluetooth.YoBleCommandLink;
import com.yovo.biz_ggec_hs.provider.lib_bluetooth.YoClassicRadio;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;
import com.yovo.biz_ggec_hs.provider.lib_ggec_opus.YoOpusCodecProvider;

/**
 * 组合根：把 provider 的链路适配、中层协议引擎、上层会话/扫描与设备目录装在一起。
 *
 * <p>这是模块里唯一构造 {@code provider/lib_bluetooth} 适配、并调用
 * {@code HsLog.requireInitialized} 的地方。日志调用点直接用 {@code HsLog}。
 * 对外只经过 {@code YoHsSdk}。</p>
 */
public final class HsGraph {

    private final HsNearbyImpl nearby;
    private final HsSessionImpl session;
    private final HsCatalogImpl catalog;
    private final HsRecordManager recordManager;

    private HsGraph(HsNearbyImpl nearby, HsSessionImpl session, HsCatalogImpl catalog,
                    HsRecordManager recordManager) {
        this.nearby = nearby;
        this.session = session;
        this.catalog = catalog;
        this.recordManager = recordManager;
    }

    public static HsGraph open(Context context) {
        HsLog.requireInitialized();
        YoBleCommandLink link = YoBleCommandLink.open(context);
        YoBleAudioDataLink audioLink = new YoBleAudioDataLink(link.bleClient());
        HsProtocolEngine engine = new HsProtocolEngine(link);
        HsCatalogImpl catalog = new HsCatalogImpl();
        HsCapabilityRouter.verify(catalog);
        HsRecordPathResolver pathResolver = new HsRecordPathResolver(context);
        HsSessionImpl session = new HsSessionImpl(engine, catalog, new HsClassicConnect(
                YoClassicRadio.open(context), new HsMainDelay()),
                new HsConnectionIntake(), audioLink);
        HsRecordManager recordManager = new HsRecordManager(engine, audioLink, pathResolver,
                new YoOpusCodecProvider(), session::applyCompletion);
        return new HsGraph(
                HsNearbyImpl.open(new HsNearbyScanner(link.bleClient()), catalog),
                session,
                catalog,
                recordManager);
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

    public HsRecordManager record() {
        return recordManager;
    }

    /** 先停扫描再释放链路：扫描句柄与连接句柄共用同一条 BLE 客户端。 */
    public void close() {
        nearby.stop();
        session.close();
        recordManager.release();
    }
}
