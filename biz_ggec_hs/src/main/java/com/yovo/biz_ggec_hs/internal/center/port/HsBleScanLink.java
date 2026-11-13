package com.yovo.biz_ggec_hs.internal.center.port;





import java.util.List;
import java.util.function.Predicate;

/**
 * 广播扫描端口：底层只做订阅与原始帧转交，不判断这帧是不是 HS 耳机、不维护业务会话。
 *
 * <p>失败分类由实现方把蓝牙库错误码映射成 {@link HsControl.Failure} 后交上来，上层不再认识库错误码。</p>
 *
 * <p>帧级谓词是机制层的省流量入口。扫描档位由蓝牙适配自己的设置决定，不从这里传入。
 * 业务层的真源永远是自己维护的设备表 + 过滤规则。</p>
 */
public interface HsBleScanLink {

    void start(Observer observer);

    void stop();

    boolean isScanning();

    /**
     * 热替换帧级谓词：只影响后续帧，不重启扫描、不清空。默认放行全部（{@code null}）。
     *
     * <p>扫描栈尚未提供该机制时实现方可忽略——忽略只意味着「不省流量」，识别与规则结果不变。</p>
     */
    default void setFramePredicate(Predicate<HsRawAdvert> predicate) { }

    /**
     * 机制层最近一帧的时刻（毫秒，与 {@link System#currentTimeMillis()} 同口径）；
     * 尚无观测时返回 {@code 0}。用于区分「业务没识别出设备」与「平台根本没在给帧」。
     */
    default long lastFrameAtMillis() {
        return 0L;
    }

    interface Observer {

        /** 一批原始广播（可能为空实现方的空批不投递）。 */
        void onBatch(List<HsRawAdvert> adverts);

        /** 库报告某地址不再出现。 */
        void onLost(String address);

        /** 扫描失败；实现方在投递前已结束本次订阅。 */
        void onFailed(HsScanFault failure);

        /** 扫描被停止（含库侧主动停止）。 */
        void onStopped();
    }
}
