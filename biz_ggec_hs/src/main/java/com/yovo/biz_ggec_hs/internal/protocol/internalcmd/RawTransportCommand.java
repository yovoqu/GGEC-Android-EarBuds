package com.yovo.biz_ggec_hs.internal.protocol.internalcmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;

import java.util.Locale;

/**
 * 调试工具使用的受调度原始协议命令，避免绕过命令队列破坏请求/响应相关性。
 */
public final class RawTransportCommand extends Command {
    private static final long TIMEOUT_MS = 5_000L;

    private final String payload;
    private final String responsePrefix;

    public RawTransportCommand(String hexPayload) {
        String normalized = hexPayload == null
                ? "" : hexPayload.replaceAll("\\s", "").toUpperCase(Locale.US);
        if (!ProtocolValues.isStandardFrame(normalized)) {
            throw new IllegalArgumentException(
                    "原始命令必须是长度字段与 1234 尾标完整的标准协议帧");
        }
        payload = normalized;
        responsePrefix = normalized.substring(0, 6);
        setCommandPrefix(responsePrefix);
    }

    @Override
    public String getCommandData() {
        return payload;
    }

    @Override
    public Response parseResponse(String responseData) {
        return completed(ResultCode.SUCCESS, responseData, "原始调试命令收到响应");
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        return responseData != null
                && responseData.regionMatches(true, 0, responsePrefix, 0, responsePrefix.length());
    }

    @Override
    public boolean isResponseFrameValid(String responseData) {
        return ProtocolValues.isProtocolFrame(responseData);
    }

    @Override
    public long getTimeoutMs() {
        return TIMEOUT_MS;
    }
}
