package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;

/**
 * 实时语音链路的事件出口。
 *
 * <p><b>线程约定（调用方不用自己加锁）</b>：所有事件都从同一条后台线程按发生顺序发出，
 * 不会并发。理由有两条：</p>
 * <ol>
 *   <li>状态机不加锁也能正确。<code>LISTENING → THINKING → SPEAKING</code> 这类流转
 *       如果被两条线程同时推进，就会出现「先 SPEAKING 后 THINKING」的鬼状态，
 *       而这种 bug 只在真机上偶发，靠日志几乎抓不到。</li>
 *   <li>音频块必须按序播放。乱序的一两帧在耳朵里就是爆音。</li>
 * </ol>
 *
 * <p>这条线程不是主线程：实现里要改 UI 请自己 post 到主线程，不要在这条线程上做
 * 重活（写文件、合成、渲染），否则音频会堵在队列后面。</p>
 */
public interface YoRealtimeListener {

    /**
     * 收到一个事件。实现不应该抛异常；抛了也不会影响后续事件，但这条事件的内容就丢了。
     */
    void onEvent(@NonNull YoRealtimeEvent event);
}
