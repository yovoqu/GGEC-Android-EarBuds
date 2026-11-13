package com.ggec.earbuds.wiring;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.ai.call.AiCallViewModel;

/**
 * 主线程定时器的平台实现。
 *
 * <p>通话页 ViewModel 只依赖 {@link AiCallViewModel.Ticker} 抽象；把 Handler/Looper 放进装配层，
 * 单元测试就不需要为“VM 里藏了一个 Handler”付出代价，也不需要在 VM 里做懒加载空判断。</p>
 */
public final class MainThreadTicker implements AiCallViewModel.Ticker {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void postDelayed(@NonNull Runnable runnable, long delayMs) {
        handler.postDelayed(runnable, delayMs);
    }

    @Override
    public void removeCallbacks(@NonNull Runnable runnable) {
        handler.removeCallbacks(runnable);
    }
}
