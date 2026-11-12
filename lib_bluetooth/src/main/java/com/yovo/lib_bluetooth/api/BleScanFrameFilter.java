package com.yovo.lib_bluetooth.api;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.model.ble.BleScanResult;

/**
 * 帧级过滤器：决定"这一帧要不要交给订阅者"。
 *
 * <p>纯谓词，由订阅方注入；求值发生在扫描帧管道内，逐帧进行。替换谓词只影响后续帧，
 * <b>不重启扫描、不清空已有订阅</b>（与平台扫描参数变更正交）。默认（未设置）放行全部帧。</p>
 *
 * <p>实现必须是纯函数、无副作用、可被任意扫描回调线程调用；不得持有设备表或做去重/TTL。</p>
 */
public interface BleScanFrameFilter {

    /**
     * @param frame 平台上报的原始帧
     * @return {@code true} 表示交给订阅者
     */
    boolean matches(@NonNull BleScanResult frame);
}
