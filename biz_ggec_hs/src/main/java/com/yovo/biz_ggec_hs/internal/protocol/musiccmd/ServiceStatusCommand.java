package com.yovo.biz_ggec_hs.internal.protocol.musiccmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 获取媒体/通话/录音等服务状态命令
 *
 * 命令格式：99 EC B5 00 01 00 12 34
 * 响应/主动上报：99 EC B5 00 07 xx xx xx xx xx xx xx [12 34]
 *   xx1: 0x00=非音乐  0x01=音乐中
 *   xx2: 0x00=非通话  0x01=通话中
 *   xx3: 0x00=非录音  0x01=录音中  0x02=录音触发
 *   xx4: 0x00=非文件导出  0x01=文件导出中
 *   xx5: 0x00=非随录随传  0x01=随录随传中
 *   xx6: 0x00=都未佩戴  0x01=右佩戴中  0x02=左佩戴中  0x03=都佩戴中
 *   xx7: 0x00=非AI翻译  0x01=AI翻译中  0x02=低电退出AI翻译
 *        0x03=通话停止AI翻译  0x04=摘下停止AI翻译
 */
public class ServiceStatusCommand extends Command {
    private static final String TAG = "ServiceStatusCommand";

    private static final String COMMAND_PREFIX = "99ECB5";
    private static final String COMMAND_SUFFIX = "1234";
    private static final int STATUS_COUNT = 7;
    /** 无 AI 翻译字节的上一版：音乐/通话/录音/导出/随录/佩戴。 */
    private static final int PREVIOUS_STATUS_COUNT = 6;
    /** 仅音乐/通话/录音 3 字节。 */
    private static final int LEGACY_STATUS_COUNT = 3;
    /** 录音状态索引（0-based，对应 statusMap 键 "02"）。 */
    private static final int RECORD_STATUS_INDEX = 2;
    private static final int RECORD_STATUS_MAX = 2;
    /** AI 翻译状态索引（0-based，对应 statusMap 键 "06"）。 */
    private static final int AI_TRANSLATE_STATUS_INDEX = 6;
    private static final int AI_TRANSLATE_STATUS_MAX = 4;
    /** 佩戴状态索引（0-based）。 */
    private static final int WEAR_STATUS_INDEX = 5;
    private static final int WEAR_STATUS_MAX = 3;

    public ServiceStatusCommand() {
        super();
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        return COMMAND_PREFIX + "000100" + COMMAND_SUFFIX;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!isResponseMatch(responseData)) {
            return completed(ResultCode.FAILED, null, "响应数据格式错误");
        }

        Map<String, String> statusMap = parseStatusMap(responseData);
        if (statusMap == null) {
            return completed(ResultCode.FAILED, null, "解析服务状态失败");
        }

        int[] statusArray = new int[statusMap.size()];
        for (int i = 0; i < statusArray.length; i++) {
            statusArray[i] = Integer.parseInt(statusMap.get(String.format("%02d", i)));
        }
        return completed(ResultCode.SUCCESS, statusArray, "获取服务状态成功");
    }

    /**
     * 解析服务状态数据（主动查询响应与主动上报共用）
     * 格式：99ECB5 0007 xx xx xx xx xx xx xx [1234]
     * 兼容：99ECB5 0006 … / 99ECB5 0003 …
     */
    public static Map<String, String> parseStatusMap(String responseData) {
        int statusCount = resolveStatusCount(responseData);
        if (statusCount < 0) {
            return null;
        }
        try {
            Map<String, String> statusMap = new LinkedHashMap<>();
            for (int i = 0; i < statusCount; i++) {
                int offset = 10 + i * 2;
                int status = Integer.parseInt(responseData.substring(offset, offset + 2), 16);
                if (!isValidStatus(i, status)) {
                    return null;
                }
                statusMap.put(String.format("%02d", i), String.valueOf(status));
            }
            return statusMap;
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            HsLog.e(TAG, "解析服务状态失败", e);
            return null;
        }
    }

    private static boolean isValidStatus(int index, int status) {
        if (index == RECORD_STATUS_INDEX) {
            return status >= 0 && status <= RECORD_STATUS_MAX;
        }
        if (index == WEAR_STATUS_INDEX) {
            return status >= 0 && status <= WEAR_STATUS_MAX;
        }
        if (index == AI_TRANSLATE_STATUS_INDEX) {
            return status >= 0 && status <= AI_TRANSLATE_STATUS_MAX;
        }
        return status == 0 || status == 1;
    }

    private static boolean isSupportedStatusCount(int payloadLength) {
        return payloadLength == STATUS_COUNT
                || payloadLength == PREVIOUS_STATUS_COUNT
                || payloadLength == LEGACY_STATUS_COUNT;
    }

    private static int resolveStatusCount(String responseData) {
        if (responseData == null || !responseData.regionMatches(true, 0, COMMAND_PREFIX, 0, 6)) {
            return -1;
        }
        boolean completeFrame = ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX);
        if (completeFrame) {
            int payloadLength = Integer.parseInt(responseData.substring(6, 10), 16);
            if (isSupportedStatusCount(payloadLength)) {
                return payloadLength;
            }
            return -1;
        }
        // 主动上报可能无尾标 1234
        if (responseData.length() < 10) {
            return -1;
        }
        int payloadLength;
        try {
            payloadLength = Integer.parseInt(responseData.substring(6, 10), 16);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (isSupportedStatusCount(payloadLength)
                && responseData.length() == 10 + payloadLength * 2) {
            return payloadLength;
        }
        return -1;
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return resolveStatusCount(responseData) > 0
                && ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX);
    }
}
