package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;

/**
 * 实时会话的输出出口。
 *
 * <p>所有回调在同一条后台线程上按发生顺序发出，不会并发。这条线程不是主线程：
 * 要改界面请自己投回主线程。不要在这里做重活，否则后面的音频会堵在队列里。</p>
 */
public interface YoRealtimeListener {

    /**
     * 收到一条输出。实现不应该抛异常；抛了这条输出就丢了，后面的仍会继续。
     */
    void onOutput(@NonNull YoRealtimeOutput output);
}
