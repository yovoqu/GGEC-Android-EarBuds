package com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd;



import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolPaired;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;

import java.util.ArrayList;
import java.util.List;

/**
 * 获取蓝牙缓存已配对设备列表命令（协议 EC A7）。
 * <p>
 * 发送：99 EC A7 00 01 00 12 34
 * 响应：99 EC A7 [LEN_H][LEN_L] [COUNT] [TLV entries...] 12 34
 * <p>
 * TLV 子项定义与解析见 {@link PairedDeviceTlvParser}。
 */
public class GetPairedDevicesCommand extends Command {
    private static final String TAG = "GetPairedDevicesCommand";

    private static final String COMMAND_PREFIX = "99ECA7";
    private static final String COMMAND_DATA = "99ECA7000100" + "1234";

    public GetPairedDevicesCommand() {
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
    public Response parseResponse(String responseData) {
        try {
            List<ProtocolPaired> devices = parseDevices(responseData);
            if (devices == null) {
                return completed(ResultCode.FAILED, null, "响应数据格式错误或设备数量不一致");
            }
            HsLog.d(TAG, "解析完成，共 " + devices.size() + " 个设备");
            return completed(ResultCode.SUCCESS, devices, "获取配对设备列表成功");
        } catch (Exception e) {
            HsLog.e(TAG, "解析配对设备列表失败", e);
            return completed(ResultCode.FAILED, null, "解析失败: " + e.getMessage());
        }
    }

    /**
     * 解析 A7 响应帧为设备列表（供请求应答与主动上报复用）。
     * <p>
     * 格式非法或长度不足时返回 null；合法空列表返回空集合。
     * 设备数量字节与 TLV 实际记录数不一致时，以实际解析结果为准并告警
     * （固件 NV 中可能存在无效配对记录，整体失败会让连接中心页面拿不到任何数据）。
     */
    public static List<ProtocolPaired> parseDevices(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (!ProtocolValues.isCompleteFrame(responseData, COMMAND_PREFIX)
                || responseData.length() < 16) {
            return null;
        }

        // 解析数据总长度（prefix之后4个hex字符 = 2字节）
        int totalDataLen = Integer.parseInt(responseData.substring(6, 10), 16);

        // 解析设备数量（1字节）
        int count = Integer.parseInt(responseData.substring(10, 12), 16);
        HsLog.d(TAG, "已配对设备数量: " + count);

        if (count == 0) {
            return new ArrayList<>();
        }

        // TLV 数据区：从 offset 12 开始，长度 = (totalDataLen - 1) 字节（减去count本身1字节）
        int tlvByteLen = totalDataLen - 1;
        int tlvEndIndex = 12 + tlvByteLen * 2;

        if (responseData.length() < tlvEndIndex) {
            return null;
        }

        String tlvHex = responseData.substring(12, tlvEndIndex);
        List<ProtocolPaired> devices = PairedDeviceTlvParser.parseDevices(tlvHex);
        if (devices.size() != count) {
            HsLog.w(TAG, "已配对设备数量(" + count + ")与 TLV 实际解析(" + devices.size()
                    + ")不一致，以实际解析结果为准");
        }
        return devices;
    }
}
