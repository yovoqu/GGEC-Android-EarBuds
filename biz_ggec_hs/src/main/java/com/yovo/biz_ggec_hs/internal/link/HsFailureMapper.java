package com.yovo.biz_ggec_hs.internal.link;





import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.CommandCompletion;
import com.yovo.biz_ggec_hs.internal.center.protocol.HsProtocolEngine;

/**
 * 引擎侧原因与命令完成码到对外失败分类的唯一映射点。
 *
 * <p>返回 {@code null} 表示「不是失败」：主动断开与换连接目标的收尾都属于正常结束，
 * 不能塞进任何失败分类。</p>
 */
final class HsFailureMapper {

    private HsFailureMapper() {
    }

    /**
     * 已建立会话结束的原因归一。
     *
     * @return {@code null} 表示正常收尾（主动断开、换连接目标）
     */
    static HsControl.Failure ofDisconnectReason(String reason) {
        if (reason == null || reason.isEmpty()) {
            return null;
        }
        if (HsProtocolEngine.REASON_TARGET_SWITCHED.equals(reason)) {
            return null;
        }
        if (HsProtocolEngine.REASON_READY_TIMEOUT.equals(reason)) {
            return HsControl.Failure.TIMEOUT;
        }
        return HsControl.Failure.DEVICE_ERROR;
    }

    /** 尚未就绪即失败的原因归一。 */
    static HsControl.Failure ofConnectFailure(String reason) {
        if (HsProtocolEngine.REASON_READY_TIMEOUT.equals(reason)) {
            return HsControl.Failure.TIMEOUT;
        }
        return HsControl.Failure.DEVICE_ERROR;
    }

    /**
     * 命令完成码归一。
     *
     * @return {@code null} 表示成功
     */
    static HsControl.Failure ofCommandCode(int code) {
        return switch (code) {
            case Command.ResultCode.SUCCESS -> null;
            case Command.ResultCode.TIMEOUT -> HsControl.Failure.TIMEOUT;
            case Command.ResultCode.FAILED -> HsControl.Failure.DEVICE_ERROR;
            case Command.ResultCode.RESERVED -> HsControl.Failure.REJECTED;
            default -> throw new IllegalArgumentException("未知的命令完成码: " + code);
        };
    }

    /** 写入/动作回调用结果。 */
    static HsControl.Outcome<Void> writeResult(CommandCompletion completion) {
        HsControl.Failure failure = ofCommandCode(completion.getCode());
        return failure == null ? new HsControl.Outcome.Ok<>(null) : new HsControl.Outcome.Err<>(failure);
    }
}
