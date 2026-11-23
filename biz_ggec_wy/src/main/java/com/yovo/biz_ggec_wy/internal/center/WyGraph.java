package com.yovo.biz_ggec_wy.internal.center;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_wy.internal.center.catalog.WyCatalogImpl;
import com.yovo.biz_ggec_wy.internal.link.WySession;
import com.yovo.biz_ggec_wy.internal.record.WyRecordPathResolver;
import com.yovo.biz_ggec_wy.internal.record.WyStreamRecordSession;
import com.yovo.biz_ggec_wy.internal.scan.WyClassicScanner;
import com.yovo.biz_ggec_wy.provider.lib_bluetooth.WyClassicRadio;
import com.yovo.biz_ggec_wy.provider.lib_common.log.WyLog;
import com.yovo.biz_ggec_wy.provider.lib_ggec_opus.WyOpusCodecProvider;

/**
 * 组合根：装配 Provider 适配层、中层协议与扫描/会话/录音组件。
 */
public final class WyGraph {

    private final WyClassicScanner scanner;
    private final WySession session;
    private final WyCatalogImpl catalog;
    private final WyStreamRecordSession recordSession;

    private WyGraph(@NonNull WyClassicScanner scanner,
                    @NonNull WySession session,
                    @NonNull WyCatalogImpl catalog,
                    @NonNull WyStreamRecordSession recordSession) {
        this.scanner = scanner;
        this.session = session;
        this.catalog = catalog;
        this.recordSession = recordSession;
    }

    @NonNull
    public static WyGraph open(@NonNull Context context) {
        Context app = context.getApplicationContext();
        Context target = app != null ? app : context;

        WyLog.requireInitialized();

        WyClassicRadio radio = WyClassicRadio.open(target);
        WyOpusCodecProvider opusCodecProvider = new WyOpusCodecProvider();
        WyCatalogImpl catalog = new WyCatalogImpl();

        WyClassicScanner scanner = new WyClassicScanner(radio);
        WySession session = new WySession(radio, catalog);
        // 录音文件名带产品型号：绑定当前连接型号（连接前为空 → 命名回落 UNKNOWN）。
        WyRecordPathResolver pathResolver = new WyRecordPathResolver(target,
                () -> session.peer().modelId());
        WyStreamRecordSession recordSession = new WyStreamRecordSession(radio, opusCodecProvider, pathResolver);

        // 音频数据路由：Audio SPP 接收到的原始音频字节送入录音会话
        session.setAudioChunkConsumer(recordSession::onAudioChunkReceived);
        // 控制数据路由：Control SPP 接收到的控制流字节送入录音会话以解析 99 EC 协议
        session.setControlDataConsumer(recordSession::onControlDataReceived);

        return new WyGraph(scanner, session, catalog, recordSession);
    }

    @NonNull
    public WyClassicScanner scan() {
        return scanner;
    }

    @NonNull
    public WySession link() {
        return session;
    }

    @NonNull
    public WyCatalogImpl catalog() {
        return catalog;
    }

    @NonNull
    public WyStreamRecordSession record() {
        return recordSession;
    }

    public void close() {
        scanner.stop();
        session.close();
        recordSession.release();
    }
}
