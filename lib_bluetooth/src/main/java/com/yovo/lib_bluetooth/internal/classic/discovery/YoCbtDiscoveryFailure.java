package com.yovo.lib_bluetooth.internal.classic.discovery;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;

import java.util.Objects;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 设备发现专用失败类型，继承 {@link YoCbtFailure} 并携带设备 ID 与发现状态上下文。
 */
public final class YoCbtDiscoveryFailure extends YoCbtFailure {

    @Nullable
    private final YoCbtDeviceId deviceId;

    @NonNull
    private final YoCbtDiscoveryState discoveryState;

    /**
     * 完整构造函数。
     *
     * @param errorCode       发现错误码
     * @param message         失败描述
     * @param cause           可选的根因异常
     * @param recoverable     是否可恢复/重试
     * @param retryable       是否适合自动重试
     * @param sourceModule    失败来源模块
     * @param deviceId        相关设备 ID，非设备相关时为 null
     * @param discoveryState  失败时的发现状态
     * @param suggestedAction 建议的恢复操作
     */
    public YoCbtDiscoveryFailure(@NonNull YoCbtErrorCode errorCode,
                                  @NonNull String message,
                                  @Nullable Throwable cause,
                                  boolean recoverable,
                                  boolean retryable,
                                  @NonNull String sourceModule,
                                  @Nullable YoCbtDeviceId deviceId,
                                  @NonNull YoCbtDiscoveryState discoveryState,
                                  @NonNull String suggestedAction) {
        super(errorCode, message, cause, recoverable, retryable,
                sourceModule, deviceId, null, null, null, suggestedAction);
        this.deviceId = deviceId;
        this.discoveryState = Objects.requireNonNull(discoveryState, "discoveryState must not be null");
    }

    /**
     * 不可恢复、不可重试的发现失败简化工厂方法。
     *
     * @param errorCode      发现错误码
     * @param message        人类可读描述
     * @param sourceModule   失败来源模块
     * @param discoveryState 失败时的发现状态
     * @return 新的 {@link YoCbtDiscoveryFailure}
     */
    @NonNull
    public static YoCbtDiscoveryFailure of(@NonNull YoCbtErrorCode errorCode,
                                            @NonNull String message,
                                            @NonNull String sourceModule,
                                            @NonNull YoCbtDiscoveryState discoveryState) {
        return new YoCbtDiscoveryFailure(errorCode, message, null, true, false,
                sourceModule, null, discoveryState, "");
    }

    /**
     * 设备相关发现失败的工厂方法。
     *
     * @param errorCode      发现错误码
     * @param message        人类可读描述
     * @param sourceModule   失败来源模块
     * @param deviceId       失败发生时正在发现的设备
     * @param discoveryState 失败时的发现状态
     * @return 新的 {@link YoCbtDiscoveryFailure}
     */
    @NonNull
    public static YoCbtDiscoveryFailure forDevice(@NonNull YoCbtErrorCode errorCode,
                                                   @NonNull String message,
                                                   @NonNull String sourceModule,
                                                   @NonNull YoCbtDeviceId deviceId,
                                                   @NonNull YoCbtDiscoveryState discoveryState) {
        return new YoCbtDiscoveryFailure(errorCode, message, null, true, false,
                sourceModule, deviceId, discoveryState, "");
    }

    /**
     * 返回失败发生时正在发现的设备 ID；
     * 非设备相关失败时为 {@code null}。
     */
    @Nullable
    @Override
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    /**
     * 返回失败时的发现状态。
     */
    @NonNull
    public YoCbtDiscoveryState getDiscoveryState() {
        return discoveryState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof YoCbtDiscoveryFailure)) return false;
        YoCbtDiscoveryFailure that = (YoCbtDiscoveryFailure) o;
        return Objects.equals(deviceId, that.deviceId)
                && discoveryState == that.discoveryState;
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, discoveryState);
    }

    @Override
    public String toString() {
        return "YoCbtDiscoveryFailure{"
                + "errorCode=" + getErrorCode()
                + ", message=" + getMessage()
                + ", deviceId=" + deviceId
                + ", discoveryState=" + discoveryState
                + ", recoverable=" + isRecoverable()
                + '}';
    }
}
