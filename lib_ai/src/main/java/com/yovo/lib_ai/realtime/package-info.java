/**
 * 实时语音传输与协议引擎。调用方只使用这一包里公开的类型：
 *
 * <ul>
 *   <li>{@link com.yovo.lib_ai.realtime.YoChatGptRealtime}：这条产品线能提供的主机、方言和实时模型。</li>
 *   <li>{@link com.yovo.lib_ai.realtime.YoRealtime}：唯一入口。打开时传入
 *       {@link com.yovo.lib_ai.realtime.YoRealtimeConfig}，获得 {@link com.yovo.lib_ai.realtime.YoRealtime.Connection}。</li>
 *   <li>{@link com.yovo.lib_ai.realtime.YoRealtimeInput}：统上行的音频、截断、断句、打断与工具结果。</li>
 *   <li>{@link com.yovo.lib_ai.realtime.YoRealtimeOutput}：直接从协议帧解出的文字、音频、说话状态、线速率与失败输出。</li>
 * </ul>
 *
 * <p>SDK 仅负责 WebSocket 长连接与三方言 JSON 编解码，不持有麦克风、重采样、喇叭或本地播放计时。</p>
 */
package com.yovo.lib_ai.realtime;
