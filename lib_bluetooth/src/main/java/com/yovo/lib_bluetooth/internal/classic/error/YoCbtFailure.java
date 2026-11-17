package com.yovo.lib_bluetooth.internal.classic.error;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoTraceId;
import com.yovo.lib_bluetooth.internal.common.result.YoFailure;
import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceId;
import com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.model.classic.CbtErrorCode;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description YoCbt 模块结构化失败信息，继承通用 {@link com.yovo.lib_bluetooth.internal.common.result.YoFailure}。
 */

public class YoCbtFailure extends YoFailure {
    private final CbtErrorCode publicClass;
    private final YoCbtDeviceId deviceId;
    private final YoCbtProfileType profileType;
    private final YoCbtSppConnectionId connectionId;
    private final YoCbtSppEndpoint endpoint;
    private final String suggestedAction;

    public YoCbtFailure(@NonNull YoCbtErrorCode errorCode,
                        @NonNull String message,
                        @Nullable Throwable cause,
                        boolean recoverable,
                        boolean retryable,
                        @NonNull String sourceModule,
                        @Nullable YoCbtDeviceId deviceId,
                        @Nullable YoCbtProfileType profileType,
                        @Nullable YoCbtSppConnectionId connectionId,
                        @Nullable YoCbtSppEndpoint endpoint,
                        @NonNull String suggestedAction) {
        super(errorCode, message, cause, recoverable, retryable, sourceModule, YoTraceId.next());
        this.publicClass = errorCode.publicClass();
        this.deviceId = deviceId;
        this.profileType = profileType;
        this.connectionId = connectionId;
        this.endpoint = endpoint;
        this.suggestedAction = suggestedAction;
    }

    public static YoCbtFailure of(@NonNull YoCbtErrorCode code,
                                  @NonNull String message,
                                  @NonNull String sourceModule) {
        return new YoCbtFailure(code, message, null, true, false, sourceModule,
                null, null, null, null, "");
    }

    /** 对外失败分类：由内部错误码自带，门面投影直接读取，不做字符串对表。 */
    @NonNull
    public CbtErrorCode publicClass() {
        return publicClass;
    }

    @Nullable
    public YoCbtDeviceId getDeviceId() {
        return deviceId;
    }

    @Nullable
    public YoCbtProfileType getProfileType() {
        return profileType;
    }

    @Nullable
    public YoCbtSppConnectionId getConnectionId() {
        return connectionId;
    }

    @Nullable
    public YoCbtSppEndpoint getEndpoint() {
        return endpoint;
    }

    @NonNull
    public String getSuggestedAction() {
        return suggestedAction;
    }
}
