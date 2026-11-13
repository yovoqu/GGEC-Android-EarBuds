package com.yovo.biz_ggec_hs.internal.center.port;

import com.yovo.biz_ggec_hs.api.HsControl;

/**
 * 经典音频链路。会话只看到「可以开 GATT / 失败 / 掉了」，不看到配对和 Profile。
 */
public interface HsClassicRadio {

    void open(String classicAddress, Callback callback);

    /** 放弃当前这一次，不释放客户端。迟到的回调必须丢掉。 */
    void cancel();

    void release();

    interface Callback {
        void onReady();

        void onFailed(HsControl.Failure failure);

        /** 已经就绪的经典链路掉了。GATT 还没开时由会话当成连接失败。 */
        void onLost();
    }
}
