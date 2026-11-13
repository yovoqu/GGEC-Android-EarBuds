package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2026-07-24
 * Description:
 * 获取耳机存储容量命令（协议 EC BA）
 * <p>
 * 发送：99 EC BA 00 01 xx 12 34
 * xx：00 左耳 / 01 右耳 / 02 双耳
 * <p>
 * 单耳响应：99 EC BA 00 09 xx [总容量4B] [剩余容量4B] 12 34
 * 双耳响应：99 EC BA 00 11 02 [左总4B] [左剩4B] [右总4B] [右剩4B] 12 34
 * （容量均为小端序；双耳先左后右）
 */
public class GetStorageCommand extends Command {
    private static final String TAG = "GetStorageCommand";

    private static final String COMMAND_PREFIX = "99ECBA";
    private static final int SINGLE_PAYLOAD_LEN = 9;
    private static final int BOTH_PAYLOAD_LEN = 17;
    private static final int SINGLE_RESPONSE_CHARS = 32; // 6+4+18+4
    private static final int BOTH_RESPONSE_CHARS = 48;   // 6+4+34+4

    private final int earType;
    private final String commandData;

    /**
     * @param earType {@link StorageCapacityResult#EAR_LEFT} /
     *                {@link StorageCapacityResult#EAR_RIGHT} /
     *                {@link StorageCapacityResult#EAR_BOTH}
     */
    public GetStorageCommand(int earType) {
        super();
        if (earType < StorageCapacityResult.EAR_LEFT
                || earType > StorageCapacityResult.EAR_BOTH) {
            throw new IllegalArgumentException("存储容量耳侧参数超出协议范围");
        }
        this.earType = earType;
        setCommandPrefix(COMMAND_PREFIX);
        this.commandData = "99ECBA0001" + String.format("%02X", earType) + "1234";
    }

    public int getEarType() {
        return earType;
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

        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)) {
            return failed("响应数据格式错误");
        }

        int payloadLen;
        try {
            payloadLen = Integer.parseInt(responseData.substring(6, 10), 16);
        } catch (NumberFormatException e) {
            return failed("载荷长度解析失败");
        }

        int responseEar;
        try {
            responseEar = Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException e) {
            return failed("耳侧字节解析失败");
        }
        if (responseEar < StorageCapacityResult.EAR_LEFT
                || responseEar > StorageCapacityResult.EAR_BOTH) {
            return failed("耳侧类型无效");
        }

        try {
            if (payloadLen == SINGLE_PAYLOAD_LEN
                    && responseData.length() == SINGLE_RESPONSE_CHARS) {
                long total = parseLittleEndianU32(responseData, 12);
                long available = parseLittleEndianU32(responseData, 20);
                StorageCapacityResult result = StorageCapacityResult.single(
                        responseEar, total, available);
                String desc = "存储容量查询成功: 耳侧=" + responseEar
                        + ", 总容量=" + total + ", 剩余容量=" + available;
                HsLog.i(TAG, desc);
                return completed(ResultCode.SUCCESS, result, desc);
            }

            if (payloadLen == BOTH_PAYLOAD_LEN
                    && responseData.length() == BOTH_RESPONSE_CHARS
                    && responseEar == StorageCapacityResult.EAR_BOTH) {
                long leftTotal = parseLittleEndianU32(responseData, 12);
                long leftAvailable = parseLittleEndianU32(responseData, 20);
                long rightTotal = parseLittleEndianU32(responseData, 28);
                long rightAvailable = parseLittleEndianU32(responseData, 36);
                StorageCapacityResult result = StorageCapacityResult.both(
                        leftTotal, leftAvailable, rightTotal, rightAvailable);
                String desc = "存储容量查询成功(双耳): 左总=" + leftTotal
                        + ", 左剩=" + leftAvailable
                        + ", 右总=" + rightTotal
                        + ", 右剩=" + rightAvailable;
                HsLog.i(TAG, desc);
                return completed(ResultCode.SUCCESS, result, desc);
            }
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            HsLog.e(TAG, "解析容量数据失败", e);
            return failed("容量数据解析失败");
        }

        return failed("响应数据长度或耳侧不匹配");
    }

    private Response failed(String message) {
        HsLog.e(TAG, message);
        return completed(ResultCode.FAILED, null, message);
    }

    private static long parseLittleEndianU32(String frame, int hexOffset) {
        return Long.parseLong(reverseHexBytes(frame.substring(hexOffset, hexOffset + 8)), 16);
    }

    private static String reverseHexBytes(String hex) {
        StringBuilder sb = new StringBuilder(hex.length());
        for (int i = hex.length() - 2; i >= 0; i -= 2) {
            sb.append(hex.charAt(i));
            sb.append(hex.charAt(i + 1));
        }
        return sb.toString();
    }
}
