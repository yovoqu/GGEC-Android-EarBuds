package com.yovo.lib_bluetooth.internal.classic.spp.policy;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpoint;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpointSource;
import com.yovo.lib_bluetooth.internal.classic.spp.endpoint.YoCbtSppEndpointType;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 校验 SPP 端点是否满足连接策略约束。
 */
public final class YoCbtSppConnectionPolicyValidator {

    private static final String TAG = "YoCbtSppConnectionPolicyValidator";

    private YoCbtSppConnectionPolicyValidator() {
    }

    @NonNull
    public static YoResult<Void> validate(@NonNull YoCbtSppConnectionPolicy policy,
                                            @NonNull YoCbtSppEndpoint endpoint) {
        switch (policy.getStrategy()) {
            case UUID_ONLY:
            case SDP_UUID_ONLY:
                if (endpoint.getType() != YoCbtSppEndpointType.UUID) {
                    return failure("Endpoint must be UUID-based for strategy "
                            + policy.getStrategy());
                }
                break;
            case CHANNEL_ONLY:
            case SDP_CHANNEL_ONLY:
                if (endpoint.getType() != YoCbtSppEndpointType.RFCOMM_CHANNEL) {
                    return failure("Endpoint must be RFCOMM channel for strategy "
                            + policy.getStrategy());
                }
                break;
            case USER_SPECIFIED_ONLY:
                YoCbtSppEndpointSource source = endpoint.getSource();
                if (source != YoCbtSppEndpointSource.USER_SPECIFIED
                        && source != YoCbtSppEndpointSource.DEFAULT_SPP) {
                    return failure("Endpoint source " + source
                            + " is not allowed for USER_SPECIFIED_ONLY");
                }
                break;
            case UUID_THEN_CHANNEL:
            case CHANNEL_THEN_UUID:
                if (endpoint.getType() != YoCbtSppEndpointType.UUID
                        && endpoint.getType() != YoCbtSppEndpointType.RFCOMM_CHANNEL) {
                    return failure("Endpoint type " + endpoint.getType()
                            + " is not supported for fallback strategy "
                            + policy.getStrategy());
                }
                break;
            default:
                break;
        }
        return YoResult.success(null);
    }

    @NonNull
    private static YoResult<Void> failure(@NonNull String message) {
        return YoResult.failure(YoCbtFailure.of(
                YoCbtErrorCode.YO_CBT_SPP_UUID_INVALID,
                message,
                TAG));
    }
}
