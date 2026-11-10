package com.yovo.biz_ggec_hs.internal.protocol.batterycmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 耳机在充电盒内状态命令
 * 负责获取耳机是否在充电盒内
 */
public class EarInCaseStatusCommand extends Command {
    private static final String TAG = "EarInCaseStatusCommand";

    // 命令前缀
    private static final String COMMAND_PREFIX = "99EC96";

    // 命令基础部分
    private static final String COMMAND_BASE = "99EC9600010012";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "34";

    // 响应前缀
    private static final String RESPONSE_PREFIX = "99EC96";

    // 耳机在充电盒内状态值
    // 00: 耳机都不在充电盒
    // 01：左耳在，右耳不在
    // 02：右耳在，左耳不在
    // 03：左右耳都在充电盒
    private int earInCaseStatus = 0;

    /**
     * 构造方法
     */
    public EarInCaseStatusCommand() {
        super();
        // 设置命令前缀
        setCommandPrefix("99EC96");
    }

    /**
     * 获取耳机在充电盒内状态
     * @return 状态值
     *         0: 耳机都不在充电盒
     *         1：左耳在，右耳不在
     *         2：右耳在，左耳不在
     *         3：左右耳都在充电盒
     */
    public int getEarInCaseStatus() {
        return earInCaseStatus;
    }

    @Override
    public String getCommandData() {
        // 完整命令: 99 EC 96 00 01 00 12 34
        return COMMAND_BASE + COMMAND_SUFFIX;
    }

    /**
     * 静态解析耳机在充电盒内状态
     * @param responseData 响应数据
     * @return 状态值（0-3），-1表示解析失败
     */
    public static int parseInCaseStatus(String responseData) {
        if (responseData == null) {
            return -1;
        }

        try {
            // 响应格式：99 EC 96 00 01 0X 12 34
            if (hasExpectedFrameShape(responseData)) {
                    String statusHex = responseData.substring(10, 12);
                    int status = Integer.parseInt(statusHex, 16);

                    if (status >= 0 && status <= 3) {
                        return status;
                    } else {
                        HsLog.w(TAG, "解析到无效的耳机在充电盒内状态值: " + status);
                        return -1;
                    }
            }
            return -1;
        } catch (Exception e) {
            HsLog.e(TAG, "静态解析耳机在充电盒内状态失败: " + responseData, e);
            return -1;
        }
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析耳机在充电盒内状态响应数据: " + responseData);

        try {
            // 处理响应格式：99 EC 96 00 01 0X 12 34
            earInCaseStatus = parseInCaseStatus(responseData);
            if (earInCaseStatus != -1) {
                // 通知命令完成
                return completed(ResultCode.SUCCESS, (Object) earInCaseStatus, "成功获取耳机在仓状态");
            }

            // 如果没有匹配的格式，返回0
            return completed(ResultCode.FAILED, null, "无法解析耳机在充电盒内状态数据: " + responseData);
        } catch (Exception e) {
            HsLog.e(TAG, "解析耳机在充电盒内状态失败: " + responseData, e);

            // 通知命令失败
            return completed(ResultCode.FAILED, null, "解析耳机在充电盒内状态失败");
        }
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        // 检查响应是否为耳机在充电盒内状态响应
        if (responseData == null) {
            return false;
        }

        HsLog.d(TAG, "检查响应匹配: " + responseData);

        // 处理响应格式：99 EC 96 00 01 0X 12 34
        if (hasExpectedFrameShape(responseData)) {
            HsLog.d(TAG, "匹配到耳机在充电盒内状态响应: " + responseData);
            return true;
        }

        return false;
    }

    private static boolean hasExpectedFrameShape(String responseData) {
        return responseData != null
                && responseData.length() == 16
                && responseData.startsWith(RESPONSE_PREFIX)
                && responseData.endsWith("1234");
    }
}
