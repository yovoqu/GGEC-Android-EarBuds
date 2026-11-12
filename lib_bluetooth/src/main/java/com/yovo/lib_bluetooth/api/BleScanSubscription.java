package com.yovo.lib_bluetooth.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.model.ble.BleOperationHandle;

/**
 * 订阅级扫描句柄：既是可取消的 {@link BleOperationHandle}，又允许热替换帧级过滤器。
 *
 * <p>两条变更路径语义写死（见 {@code docs/architecture/2026-11-refactor/scan-architecture.md} §2.2）：</p>
 * <ul>
 *   <li>{@link #setFrameFilter(BleScanFrameFilter)}：只替换谓词对象，求值在帧管道内逐帧进行，
 *       <b>绝不</b>触发平台扫描的 stop/start，也不清空订阅或已收到的数据。</li>
 *   <li>平台扫描参数（模式/批处理/legacy/硬件过滤）走 {@code YoBleClient.reconfigureScan(BleScanPlan)}，
 *       那是唯一合法的停扫→重扫入口。</li>
 * </ul>
 *
 * <p>取消（{@link #cancel()} / {@link #release()}）语义与普通句柄一致：取消即退订，最后一个订阅者
 * 取消后进程内扫描停止。</p>
 */
public abstract class BleScanSubscription extends BleOperationHandle {

    protected BleScanSubscription(@NonNull String operationId, @NonNull Runnable cancelAction) {
        super(operationId, cancelAction);
    }

    /**
     * 热替换帧级过滤器；{@code null} 表示不过滤（放行全部帧）。
     *
     * <p>本方法不触发任何平台调用，也不改变扫描状态。</p>
     */
    public abstract void setFrameFilter(@Nullable BleScanFrameFilter filter);
}
