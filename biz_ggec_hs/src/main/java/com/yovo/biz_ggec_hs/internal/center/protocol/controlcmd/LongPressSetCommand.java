package com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-05-15
 * Description:
 * 长触设置命令（协议 EC B6）
 * <p>
 * 发送：99 EC B6 00 02 xx xx 12 34
 * xx1：左右耳类型（0=左右耳, 1=左耳, 2=右耳）
 * xx2：操作类型（0录音 1语音助手 2EQ切换 3游戏模式 4无）
 * <p>
 * 响应：99 EC B6 00 01 xx 12 34
 * xx：0x00 = 成功    0x01 = 失败
 */
public class LongPressSetCommand extends Command {
    private static final String TAG = "LongPressSetCommand";

    private static final String COMMAND_PREFIX = "99ECB6";

    /**
     * 响应帧最小长度（chars）：99ECB6(6) + 0001(4) + xx(2) + 1234(4) = 16
     */
    private static final int MIN_RESPONSE_LENGTH = 16;

    private final String commandData;

    /**
     * @param actionType 操作类型（0录音 1语音助手 2EQ切换 3游戏模式 4无）
     * @param earType    耳类型（0=左右耳, 1=左耳, 2=右耳）
     */
    public LongPressSetCommand(int actionType, int earType) {
        super();
        if (actionType < 0 || actionType > PressActionConfiguration.LONG_ACTION_MAX
                || earType < 0 || earType > 2) {
            throw new IllegalArgumentException("长触设置参数超出协议范围");
        }
        setCommandPrefix(COMMAND_PREFIX);
        // HEAD=99EC CMD=B6 LEN=0002 DATA=earType+actionType SEQ=1234
        this.commandData = "99ECB60002" + String.format("%02X%02X", earType, actionType) + "1234";
    }

    @Override
    public String getCommandData() {
        return commandData;
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.startsWith(COMMAND_PREFIX);
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)
                || responseData.length() != MIN_RESPONSE_LENGTH) {
            HsLog.e(TAG, "响应数据长度不足，实际长度: " + (responseData != null ? responseData.length() : 0));
            return completed(ResultCode.FAILED, 0, "响应数据格式错误");
        }

        // 响应帧布局（单位：hex 字符数）：
        //   [0..5]   99ECB6  CMD
        //   [6..9]   0001    LEN
        //   [10..11] xx      结果状态字节
        //   [12..15] 1234    SEQ
        int status;
        try {
            status = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态字节失败", e);
            return completed(ResultCode.FAILED, 0, "状态字节解析失败");
        }

        String desc;
        switch (status) {
            case 0x00:
                desc = "长触设置成功";
                break;
            case 0x01:
                desc = "长触设置失败";
                break;
            default:
                desc = "未知结果: 0x" + String.format("%02X", status);
                break;
        }

        HsLog.i(TAG, "长触设置结果: 0x" + String.format("%02X", status) + " " + desc);
        return completed(status == 0x00 ? ResultCode.SUCCESS : ResultCode.FAILED, status, desc);
    }
}
