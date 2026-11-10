package com.yovo.biz_ggec_hs.internal.protocol.dualconnectioncmd;

import com.yovo.biz_ggec_hs.model.DualConnectionPageData;
import com.yovo.biz_ggec_hs.model.PairedDevice;
import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

import java.util.ArrayList;
import java.util.List;

/**
 * 双连接页聚合查询命令。
 * <p>
 * 发送：99 EC BD 00 01 00 12 34
 * 响应：99 EC BD [顶层 TLV Block...] 12 34
 * <p>
 * 顶层 TLV：0x01 双连接开关 / 0x02 优先设备 / 0x03 设备列表（子项解析见
 * {@link PairedDeviceTlvParser}）。
 */
public class GetDualConnectionPageCommand extends Command {
    private static final String TAG = "GetDualConnectionPageCommand";

    private static final String COMMAND_PREFIX = "99ECBD";
    private static final String COMMAND_DATA = "99ECBD000100" + "1234";
    private static final String COMMAND_SUFFIX = "1234";

    private static final int TOP_TLV_DUAL_CONNECTION = 0x01;
    private static final int TOP_TLV_PRIORITY = 0x02;
    private static final int TOP_TLV_DEVICE_LIST = 0x03;

    /** 响应帧最小长度：prefix(6) + suffix(4) */
    private static final int MIN_RESPONSE_LENGTH = 10;

    public GetDualConnectionPageCommand() {
        super();
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        return COMMAND_DATA;
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null && responseData.startsWith(COMMAND_PREFIX);
    }

    @Override
    public boolean isResponseFrameValid(String responseData) {
        return responseData != null
                && responseData.length() >= MIN_RESPONSE_LENGTH
                && ProtocolValues.isHex(responseData)
                && responseData.startsWith(COMMAND_PREFIX)
                && responseData.endsWith(COMMAND_SUFFIX);
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (responseData == null || responseData.length() < MIN_RESPONSE_LENGTH) {
            return completed(ResultCode.FAILED, null, "响应数据格式错误");
        }

        if (!responseData.endsWith(COMMAND_SUFFIX)) {
            return completed(ResultCode.FAILED, null, "响应帧尾不匹配");
        }

        try {
            String payloadHex = responseData.substring(6, responseData.length() - 4);
            DualConnectionPageData pageData = parseTopLevelTlv(payloadHex);
            HsLog.i(TAG, "聚合查询解析完成: " + pageData);
            return completed(ResultCode.SUCCESS, pageData, "双连接页聚合查询成功");
        } catch (Exception e) {
            HsLog.e(TAG, "解析双连接页聚合数据失败", e);
            return completed(ResultCode.FAILED, null, "解析失败: " + e.getMessage());
        }
    }

    private DualConnectionPageData parseTopLevelTlv(String payloadHex) {
        DualConnectionPageData pageData = new DualConnectionPageData();
        int i = 0;

        while (i < payloadHex.length()) {
            if (i + 2 > payloadHex.length()) {
                throw new IllegalArgumentException("顶层 TLV 类型字段不完整");
            }

            int topType = Integer.parseInt(payloadHex.substring(i, i + 2), 16);
            i += 2;

            int valueLen;
            if (topType == TOP_TLV_DEVICE_LIST) {
                if (i + 4 > payloadHex.length()) {
                    throw new IllegalArgumentException("顶层 TLV 0x03 长度字段不足");
                }
                valueLen = Integer.parseInt(payloadHex.substring(i, i + 4), 16);
                i += 4;
            } else {
                if (i + 2 > payloadHex.length()) {
                    throw new IllegalArgumentException(
                            "顶层 TLV 0x" + String.format("%02X", topType) + " 长度字段不足");
                }
                valueLen = Integer.parseInt(payloadHex.substring(i, i + 2), 16);
                i += 2;
            }

            if (i + valueLen * 2L > payloadHex.length()) {
                throw new IllegalArgumentException(
                        "顶层 TLV 0x" + String.format("%02X", topType) + " 数据越界");
            }

            String valueHex = payloadHex.substring(i, i + valueLen * 2);
            i += valueLen * 2;

            switch (topType) {
                case TOP_TLV_DUAL_CONNECTION:
                    parseDualConnection(valueHex, pageData);
                    break;
                case TOP_TLV_PRIORITY:
                    parsePriorityEquipment(valueHex, pageData);
                    break;
                case TOP_TLV_DEVICE_LIST:
                    parseDeviceList(valueHex, pageData);
                    break;
                default:
                    HsLog.w(TAG, "未知顶层 TLV 类型: 0x" + String.format("%02X", topType));
                    break;
            }
        }

        return pageData;
    }

    private void parseDualConnection(String valueHex, DualConnectionPageData pageData) {
        if (valueHex == null || valueHex.length() != 2) {
            throw new IllegalArgumentException("双连接开关 TLV 长度错误");
        }
        int status = Integer.parseInt(valueHex.substring(0, 2), 16);
        if (status != 0x00 && status != 0x01) {
            throw new IllegalArgumentException("双连接开关状态超出协议范围");
        }
        pageData.setDualConnection(status);
    }

    private void parsePriorityEquipment(String valueHex, DualConnectionPageData pageData) {
        if (valueHex == null || valueHex.length() < 2) {
            throw new IllegalArgumentException("优先设备 TLV 数据为空");
        }

        int status = Integer.parseInt(valueHex.substring(0, 2), 16);
        pageData.setPriorityStatus(status);

        if (status == 0x00 && valueHex.length() == 14) {
            pageData.setPriorityMac(PairedDeviceTlvParser.hexToMacAddress(
                    valueHex.substring(2, 14)));
        } else if (status == 0x01 && valueHex.length() == 2) {
            pageData.setPriorityMac(null);
        } else {
            throw new IllegalArgumentException("优先设备 TLV 状态或长度错误");
        }
    }

    private void parseDeviceList(String valueHex, DualConnectionPageData pageData) {
        if (valueHex == null || valueHex.length() < 2) {
            pageData.setDevices(new ArrayList<>());
            return;
        }

        int count = Integer.parseInt(valueHex.substring(0, 2), 16);
        HsLog.d(TAG, "已配对设备数量: " + count);

        if (count == 0) {
            pageData.setDevices(new ArrayList<>());
            return;
        }

        String deviceTlvHex = valueHex.substring(2);
        List<PairedDevice> devices = PairedDeviceTlvParser.parseDevices(deviceTlvHex);
        if (devices.size() != count) {
            // 固件 NV 可能存在无效配对记录：数量字节与实际编码不一致时以实际解析结果为准，
            // 避免整个双连接页因单条脏数据失败而拿不到任何信息
            HsLog.w(TAG, "已配对设备数量(" + count + ")与 TLV 实际解析(" + devices.size()
                    + ")不一致，以实际解析结果为准");
        }
        pageData.setDevices(devices);
    }
}
