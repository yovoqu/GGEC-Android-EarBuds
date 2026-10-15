package com.yovo.lib_ai.api;

/**
 * 一次进行中的流式调用或一条 WebSocket 通道。
 * {@link #cancel()} 停止本地投递，并中断对应的 HTTP 请求。
 * WebSocket 上取消一条通道不会关闭整条连接。
 */
public interface YoAiCall {

    void cancel();
}
