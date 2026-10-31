package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;

/**
 * 传输和会话之间的内部出口。公共 API 不暴露这一层，调用方只看 {@link YoRealtimeOutput}。
 */
interface RealtimeSink {

    /**
     * 收到一个内部事件。实现不应该抛异常；抛了也不会影响后续事件，但这条事件的内容就丢了。
     * 回调都在同一条后台线程上按序发出。
     */
    void onEvent(@NonNull YoRealtimeEvent event);
}
