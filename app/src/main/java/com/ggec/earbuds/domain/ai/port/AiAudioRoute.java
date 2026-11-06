package com.ggec.earbuds.domain.ai.port;

/**
 * 当前输出路由是否"私密"（耳机/蓝牙/USB），即不会有外放回授。
 *
 * <p>它决定能不能全双工：私密路由声音进不了麦克风，可以一直开着上行；
 * 外放/听筒则要靠 AEC，AEC 不达标就只能半双工。</p>
 */
public interface AiAudioRoute {

    /** 输出是否私密路由（耳机/蓝牙/USB）。查询失败按 false（保守当外放）。 */
    boolean hasPrivateOutput();
}
