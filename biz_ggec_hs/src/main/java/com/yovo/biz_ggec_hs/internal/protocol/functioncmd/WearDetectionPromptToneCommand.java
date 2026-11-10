package com.yovo.biz_ggec_hs.internal.protocol.functioncmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * 佩戴检测提示音开关与查询命令（协议 EC DD）。
 * <p>
 * 开启：99 EC DD 00 01 01 12 34
 * 关闭：99 EC DD 00 01 00 12 34
 * 查询：99 EC DD 00 01 02 12 34
 * <p>
 * 开关应答：00 成功 / 01 失败；查询应答：00 关闭 / 01 开启。
 */
public class WearDetectionPromptToneCommand extends Command {
    private static final String TAG = "WearDetectionPromptToneCommand";

    private static final String COMMAND_PREFIX = "99ECDD";
    private static final String COMMAND_ON = "99ECDD000101";
    private static final String COMMAND_OFF = "99ECDD000100";
    private static final String COMMAND_QUERY = "99ECDD000102";
    private static final String COMMAND_SUFFIX = "1234";

    /** 应答帧长度（chars）：99ECDD(6) + 0001(4) + 状态(2) + 1234(4) = 16 */
    private static final int RESPONSE_LENGTH = 16;

    /** 开关应答状态字节：00=成功，01=失败 */
    private static final int STATUS_SUCCESS = 0x00;

    public enum Action {
        ON,
        OFF,
        QUERY
    }

    private final Action action;

    public WearDetectionPromptToneCommand(boolean enabled) {
        this(enabled ? Action.ON : Action.OFF);
    }

    /**
     * @param action ON=开启，OFF=关闭，QUERY=查询
     */
    public WearDetectionPromptToneCommand(Action action) {
        super();
        this.action = action != null ? action : Action.QUERY;
        setCommandPrefix(COMMAND_PREFIX);
    }

    public Action getAction() {
        return action;
    }

    public boolean isEnabled() {
        return action == Action.ON;
    }

    public boolean isQuery() {
        return action == Action.QUERY;
    }

    @Override
    public String getCommandData() {
        String command;
        switch (action) {
            case ON:
                command = COMMAND_ON;
                break;
            case OFF:
                command = COMMAND_OFF;
                break;
            case QUERY:
            default:
                command = COMMAND_QUERY;
                break;
        }
        command = command + COMMAND_SUFFIX;
        HsLog.d(TAG, "生成的命令: " + command);
        return command;
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.startsWith(COMMAND_PREFIX);
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)
                || responseData.length() != RESPONSE_LENGTH) {
            HsLog.e(TAG, "响应数据格式错误，实际长度: "
                    + (responseData != null ? responseData.length() : 0));
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        int status;
        try {
            status = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态字节失败", e);
            return completed(ResultCode.FAILED, 0, "状态字节解析失败");
        }

        if (action == Action.QUERY) {
            return parseQueryStatus(status);
        }
        return parseSwitchStatus(status);
    }

    /**
     * 查询应答：状态字节直接表示当前状态，resultValue：0=关闭，1=开启。
     */
    private Response parseQueryStatus(int status) {
        if (status == 0x00) {
            HsLog.i(TAG, "佩戴检测提示音关闭");
            return completed(ResultCode.SUCCESS, 0, "佩戴检测提示音关闭");
        }
        if (status == 0x01) {
            HsLog.i(TAG, "佩戴检测提示音开启");
            return completed(ResultCode.SUCCESS, 1, "佩戴检测提示音开启");
        }
        String desc = "状态字节未知: 0x" + String.format("%02X", status);
        HsLog.w(TAG, desc);
        return completed(ResultCode.FAILED, 0, desc);
    }

    /**
     * 开关应答：00=成功，01=失败，resultValue 与状态字节一致。
     */
    private Response parseSwitchStatus(int status) {
        String actionText = action == Action.ON ? "开启" : "关闭";
        boolean success = status == STATUS_SUCCESS;
        String desc = actionText + "佩戴检测提示音" + (success ? "成功" : "失败");
        if (success) {
            HsLog.i(TAG, desc);
            return completed(ResultCode.SUCCESS, status, desc);
        }
        String failDesc = desc + "，状态=0x" + String.format("%02X", status);
        HsLog.w(TAG, failDesc);
        return completed(ResultCode.FAILED, status, failDesc);
    }
}
