package com.yovo.biz_ggec_hs.internal.link;

import android.os.Handler;
import android.os.Looper;

import com.yovo.biz_ggec_hs.internal.center.link.HsClassicConnect;

/** 经典连接超时投到主线程。 */
public final class HsMainDelay implements HsClassicConnect.Delay {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void postDelayed(Runnable task, long delayMillis) {
        handler.postDelayed(task, delayMillis);
    }

    @Override
    public void removeCallbacks(Runnable task) {
        handler.removeCallbacks(task);
    }
}
