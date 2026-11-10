package com.yovo.biz_ggec_hs.internal.protocol.functioncmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-07-24
 * Description:
 * 新手/练习模式进入、退出命令（协议 EC CC / EC CD）
 * <p>
 * 进入：请求 CMD=CC，响应 00 成功 / 01 失败
 * 退出：请求 CMD=CD，响应 00 成功 / 01 失败
 */
public class BeginnerModeCommand extends Command {
    private static final String TAG = "BeginnerModeCommand";

    private static final String HEAD = "99EC";
    private static final String LEN_DATA = "000100";
    private static final String SUFFIX = "1234";

    private final boolean enter;
    private final String cmdByte;
    private final String commandPrefix;
    private final String commandData;

    /**
     * @param enter true=进入新手模式（CC），false=退出（CD）
     */
    public BeginnerModeCommand(boolean enter) {
        super();
        this.enter = enter;
        this.cmdByte = enter ? "CC" : "CD";
        this.commandPrefix = HEAD + cmdByte;
        this.commandData = commandPrefix + LEN_DATA + SUFFIX;
        setCommandPrefix(commandPrefix);
    }

    public boolean isEnter() {
        return enter;
    }

    @Override
    public String getCommandData() {
        return commandData;
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.startsWith(commandPrefix);
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);
        String action = enter ? "进入" : "退出";

        if (!ProtocolValues.isFixedFrame(responseData, commandPrefix + "0001", 16)) {
            return completed(ResultCode.FAILED, 1, "响应数据格式错误");
        }

        int status;
        try {
            status = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析状态字节失败", e);
            return completed(ResultCode.FAILED, 1, "状态字节解析失败");
        }

        boolean success = status == 0x00;
        String desc = action + "新手模式" + (success ? "成功" : "失败");
        HsLog.i(TAG, desc + ", status=0x" + String.format("%02X", status));
        // resultValue：0=成功，1=失败（与应答状态字节一致）
        return completed(success ? ResultCode.SUCCESS : ResultCode.FAILED, status, desc);
    }
}
