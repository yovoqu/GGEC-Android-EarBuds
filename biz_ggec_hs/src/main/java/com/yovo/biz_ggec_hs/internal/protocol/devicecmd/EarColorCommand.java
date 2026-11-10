package com.yovo.biz_ggec_hs.internal.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 获取耳机颜色命令
 * 负责从耳机获取硬件颜色信息
 */
public class EarColorCommand extends Command {
    private static final String TAG = "EarColorCommand";

    // 命令前缀，用于确认是获取耳机颜色命令
    private static final String REQUEST_PREFIX = "99EC910001";

    // 响应前缀
    // 注意：根据协议文档，应该返回99EC8B0001，但实际上返回的是与请求相同的99EC910001
    private static final String RESPONSE_PREFIX = "99EC910001";

    // 发送命令数据
    private static final String COMMAND_DATA = "99EC91000100" + "1234";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "1234";

    // 耳机颜色常量
    public static final int COLOR_BLACK = 0; // 黑色

    // 耳机颜色结果
    private int earColor = COLOR_BLACK;

    /**
     * 构造方法
     */
    public EarColorCommand() {
        super();
        // 设置命令前缀
        setCommandPrefix("99EC91");
    }

    @Override
    public String getCommandData() {
        return COMMAND_DATA;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isCompleteFrame(responseData, "99EC91")
                || responseData.length() != 16) {
            // 通知命令失败，yobackdata为null
            return completed(ResultCode.FAILED, null, "响应数据格式错误");
        }

        // 获取颜色字节 - 在实际返回的数据中位于位置10-12
        String colorByte = responseData.substring(10, 12);

        // 解析颜色值（透传设备端返回的颜色数值，不做范围限制）
        try {
            earColor = Integer.parseInt(colorByte, 16);
            String result = String.valueOf(earColor);

            // 通知命令完成，yobackdata为颜色代码数字字符串
            return completed(ResultCode.SUCCESS, result, "获取颜色成功");
        } catch (NumberFormatException e) {
            // 通知命令失败，yobackdata为null
            return completed(ResultCode.FAILED, null, "颜色数据解析错误");
        }
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        HsLog.d(TAG, "检查响应匹配: " + responseData);

        // 检查响应前缀是否匹配
        return responseData != null && responseData.startsWith(RESPONSE_PREFIX);
    }

    /**
     * 获取耳机颜色结果
     * @return 耳机颜色代码
     */
    public int getEarColor() {
        return earColor;
    }
}
