package com.yovo.biz_ggec_hs.internal.link;

import com.yovo.biz_ggec_hs.internal.center.port.HsClassicRadio;

/** 经典音频已经连着：{@code open} 在调用线程立刻就绪，GATT 可以马上开。 */
final class ReadyClassicRadio implements HsClassicRadio {

    @Override
    public void open(String classicAddress, Callback callback) {
        callback.onReady();
    }

    @Override
    public void cancel() {
    }

    @Override
    public void release() {
    }
}
