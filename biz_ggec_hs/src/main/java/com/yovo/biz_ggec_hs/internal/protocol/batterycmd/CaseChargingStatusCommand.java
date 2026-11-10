package com.yovo.biz_ggec_hs.internal.protocol.batterycmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 充电盒充电状态命令
 * 负责获取充电盒是否处于充电中
 */
public class CaseChargingStatusCommand extends Command {
    private static final String TAG = "CaseChargingStatusCommand";

    // 命令前缀
    private static final String COMMAND_PREFIX = "99EC95";

    // 命令基础部分
    private static final String COMMAND_BASE = "99EC9500010012";

    // 命令校验和后缀
    private static final String COMMAND_SUFFIX = "34";

    // 响应前缀
    private static final String RESPONSE_PREFIX = "99EC95";

    // 充电状态值 (0:不在充电, 1:在充电)
    private int chargingStatus = 0;

    /**
     * 构造方法
     */
    public CaseChargingStatusCommand() {
        super();
        // 设置命令前缀
        setCommandPrefix(COMMAND_PREFIX);
    }

    /**
     * 获取充电状态
     * @return 充电状态值 (0:不在充电, 1:在充电)
     */
    public int getChargingStatus() {
        return chargingStatus;
    }

    @Override
    public String getCommandData() {
        // 完整命令: 99 EC 95 00 01 00 12 34
        return COMMAND_BASE + COMMAND_SUFFIX;
    }

    /**
     * 静态解析充电状态数据
     * @param responseData 响应数据
     * @return 充电状态（0或1），-1表示解析失败
     */
    public static int parseChargingStatus(String responseData) {
        if (responseData == null) {
            return -1;
        }

        try {
            // 响应格式：99 EC 95 00 01 XX 12 34
            if (hasExpectedFrameShape(responseData)) {
                    String statusHex = responseData.substring(10, 12);
                    int status = Integer.parseInt(statusHex, 16);

                    if (status == 0 || status == 1) {
                        return status;
                    } else {
                        HsLog.w(TAG, "解析到无效的充电状态值: " + status);
                        return -1;
                    }
            }
            return -1;
        } catch (Exception e) {
            HsLog.e(TAG, "静态解析充电状态失败: " + responseData, e);
            return -1;
        }
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析充电盒充电状态响应数据: " + responseData);

        try {
            // 处理响应格式：99 EC 95 00 01 XX 12 34
            chargingStatus = parseChargingStatus(responseData);
            if(chargingStatus != -1) {
                // 通知命令完成
                return completed(ResultCode.SUCCESS, (Object) chargingStatus, "成功获取充电盒充电状态");
            }

            // 如果没有匹配的格式，返回0
            return completed(ResultCode.FAILED, null, "无法解析充电盒充电状态数据: " + responseData);
        } catch (Exception e) {
            HsLog.e(TAG, "解析充电盒充电状态失败: " + responseData, e);

            // 通知命令失败
            return completed(ResultCode.FAILED, null, "解析充电盒充电状态失败");
        }
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        // 检查响应是否为充电盒充电状态响应
        if (responseData == null) {
            return false;
        }

        HsLog.d(TAG, "检查响应匹配: " + responseData);

        // 处理响应格式：99 EC 95 00 01 XX 12 34
        if (hasExpectedFrameShape(responseData)) {
            HsLog.d(TAG, "匹配到充电盒充电状态响应: " + responseData);
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

//    @Override
//    public long getTimeoutMs() {
//        // 充电状态命令使用较短超时时间，基于实际响应时间分析
//        // 实际响应时间平均90ms，最长170ms，设置400ms提供足够缓冲
//        return 3000;
//    }
}
