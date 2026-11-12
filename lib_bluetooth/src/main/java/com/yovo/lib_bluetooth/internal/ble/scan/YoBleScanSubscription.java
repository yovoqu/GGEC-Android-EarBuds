package com.yovo.lib_bluetooth.internal.ble.scan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.api.BleScanFrameFilter;
import com.yovo.lib_bluetooth.api.BleScanSubscription;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 订阅级扫描句柄：观测者 + 可热替换的帧级过滤器。
 *
 * <p>过滤器是可替换对象（{@link AtomicReference} 持有），帧管道每帧读一次当前值再求值，
 * 因此替换只影响后续帧，与扫描生命周期正交。</p>
 */
final class YoBleScanSubscription extends BleScanSubscription {

    @NonNull
    final BleScanObserver observer;
    @NonNull
    private final AtomicReference<BleScanFrameFilter> frameFilter = new AtomicReference<>();

    YoBleScanSubscription(@NonNull String operationId,
                          @NonNull BleScanObserver observer,
                          @NonNull Runnable cancelAction) {
        super(operationId, cancelAction);
        this.observer = observer;
    }

    @Override
    public void setFrameFilter(@Nullable BleScanFrameFilter filter) {
        frameFilter.set(filter);
    }

    /** 当前帧是否放行；未设置过滤器时放行全部。 */
    boolean accepts(@NonNull BleScanResult frame) {
        BleScanFrameFilter filter = frameFilter.get();
        return filter == null || filter.matches(frame);
    }
}
