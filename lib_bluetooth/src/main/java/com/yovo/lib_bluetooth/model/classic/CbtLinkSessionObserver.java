package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * 链路会话观察者：订阅「经典链路就绪」的全部状态迁移。
 *
 * <p>回调语义固定：</p>
 * <ul>
 *   <li>{@link #onReadinessChanged} 只在就绪事实<b>变化</b>时回调，同一事实不重复上报；</li>
 *   <li>{@link #onLinkFailed} 表示会话已终态失败，调用方必须重新发起；</li>
 *   <li>所有回调都在 {@link CbtLinkSessionConfig} 指定的回调线程上执行。</li>
 * </ul>
 */
public interface CbtLinkSessionObserver {

    /** 就绪事实变化。{@code readiness} 永不 {@code null}。 */
    void onReadinessChanged(@NonNull CbtDeviceId deviceId, @NonNull CbtLinkReadiness readiness);

    /** 会话终态失败：超时、被拒绝、平台错误。 */
    void onLinkFailed(@NonNull CbtDeviceId deviceId, @NonNull CbtFailure failure);

    /** 用户主动取消（{@link CbtLinkSessionHandle#cancel()}）。 */
    default void onLinkCancelled(@NonNull CbtDeviceId deviceId) {
    }
}
