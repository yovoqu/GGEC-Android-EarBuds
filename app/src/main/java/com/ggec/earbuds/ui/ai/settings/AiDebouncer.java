package com.ggec.earbuds.ui.ai.settings;

/**
 * 设置页的保存节流器：把一串连续变更合并成一次落盘。
 *
 * <p>抽象出来是为了让 ViewModel 可单测——生产用 {@link HandlerAiDebouncer}（主线程延迟），
 * 测试用立即执行的实现，不需要真的等 300ms。</p>
 */
public interface AiDebouncer {

    /** 取消上一个未执行的延迟任务，并在 {@code delayMillis} 后执行 {@code task}。 */
    void schedule(long delayMillis, @androidx.annotation.NonNull Runnable task);

    /** 取消尚未执行的任务。 */
    void cancel();
}
