package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * 音频输入源目录与注册中心端口。
 *
 * <p>集中负责输入源发现、默认优选路由以及外部自定义 PCM 源（例如耳机 BLE/SPP 自定义通道或测试流）
 * 的热插拔注册与注销，使通话会话可以在不停机、不重建网络长连接的前提下自由热切换输入源。</p>
 */
public interface AiAudioSourceCatalog {

    /** 返回当前所有可用输入源描述列表（按优先级排序，不可变列表）。 */
    @NonNull
    List<AiAudioSource.Descriptor> availableSources();

    /** 返回当前推荐默认输入源；若系统中无任何可用源则返回 null。 */
    @Nullable
    AiAudioSource defaultSource();

    /** 按 {@code sourceId} 解析对应的输入源实例；不存在或已移除时返回 null。 */
    @Nullable
    AiAudioSource resolve(@NonNull String sourceId);

    /** 注册外部自定义音频源（如耳机自定义语音流或虚拟注入流）。同名 id 将替换旧条目。 */
    void registerExternal(@NonNull AiAudioSource source);

    /** 注销先前通过 {@link #registerExternal(AiAudioSource)} 注册的外部音频源。 */
    void unregisterExternal(@NonNull String sourceId);

    /** 订阅输入源列表动态变化（如外设插拔、蓝牙连断、外部源增删）。 */
    void addObserver(@NonNull Observer observer);

    /** 取消订阅输入源列表变化。 */
    void removeObserver(@NonNull Observer observer);

    @FunctionalInterface
    interface Observer {
        void onSourcesChanged(@NonNull List<AiAudioSource.Descriptor> sources);
    }
}
