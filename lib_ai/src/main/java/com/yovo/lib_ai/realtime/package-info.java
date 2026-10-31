/**
 * 实时语音。调用方只使用这一包里公开的类型：
 *
 * <ul>
 *   <li>{@link com.yovo.lib_ai.realtime.YoChatGptRealtime}：这条产品线能提供的主机、方言和实时模型。
 *       选哪一个不在这里。</li>
 *   <li>{@link com.yovo.lib_ai.realtime.YoRealtime}：唯一入口。打开时传入
 *       {@link com.yovo.lib_ai.realtime.YoRealtimeConfig}。</li>
 *   <li>{@link com.yovo.lib_ai.realtime.YoRealtimeInput}：送进去的音频和打断。</li>
 *   <li>{@link com.yovo.lib_ai.realtime.YoRealtimeOutput}：送出来的文字、音频、说话状态和失败。
 *       每种输出只带自己的字段。</li>
 * </ul>
 *
 * <p>WebSocket、方言 JSON、播放进度和打断截断都封在 {@link com.yovo.lib_ai.realtime.YoRealtime}
 * 里面。麦克风、扬声器和界面不在这一包。短期密钥（{@code ek_}）由调用方提供。</p>
 */
package com.yovo.lib_ai.realtime;
