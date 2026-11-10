package com.yovo.biz_ggec_hs.internal.protocol.musiccmd;

import com.yovo.biz_ggec_hs.internal.protocol.Command;
import com.yovo.biz_ggec_hs.internal.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.internal.log.HsLog;

/**
 * 设置EQ均衡器模式命令
 * <p>
 * 请求（V11/V12 相同）：99 EC 83 00 01 [mode] 12 34
 * <ul>
 *   <li>0 音乐 / 1 电影 / 2 新闻 / 3 老年人</li>
 *   <li>4 运动 / 5 自定义（V12）</li>
 * </ul>
 * V12 响应：99 EC 83 00 02 [mode] [status] 12 34
 * （status: 0x00 成功，0x01 失败/模式不存在）
 * <p>
 * V11 响应：99 EC 83 00 01 [mode] 12 34（回显模式即成功）
 */
public class EQModeCommand extends Command {
    private static final String TAG = "EQModeCommand";

    private static final String COMMAND_PREFIX = "99EC83";
    private static final String COMMAND_BASE = "99EC830001";
    private static final String V12_RESPONSE_PREFIX = "99EC830002";
    private static final String V11_RESPONSE_PREFIX = "99EC830001";
    private static final String COMMAND_SUFFIX = "1234";

    private static final int V12_RESPONSE_LENGTH = 18;
    private static final int V11_RESPONSE_LENGTH = 16;

    private static final int STATUS_SUCCESS = 0x00;
    private static final int STATUS_MODE_NOT_EXIST = 0x01;

    public static final int MUSIC_MODE = 0;
    public static final int MOVIE_MODE = 1;
    public static final int NEWS_MODE = 2;
    public static final int ELDERLY_MODE = 3;
    public static final int SPORT_MODE = 4;
    public static final int CUSTOM_MODE = 5;

    private final int mode;

    public EQModeCommand(int mode) {
        super();
        if (mode != MUSIC_MODE && mode != MOVIE_MODE && mode != NEWS_MODE
                && mode != ELDERLY_MODE && mode != SPORT_MODE && mode != CUSTOM_MODE) {
            throw new IllegalArgumentException("无效的EQ模式: " + mode);
        }
        this.mode = mode;
        setCommandPrefix(COMMAND_PREFIX);
    }

    @Override
    public String getCommandData() {
        return COMMAND_BASE + String.format("%02X", mode) + COMMAND_SUFFIX;
    }

    @Override
    public Response parseResponse(String responseData) {
        HsLog.d(TAG, "解析响应数据: " + responseData);

        if (isV12Response(responseData)) {
            return parseV12Response(responseData);
        }
        if (isV11Response(responseData)) {
            return parseV11Response(responseData);
        }
        return completed(ResultCode.FAILED, 0, "响应数据格式错误");
    }

    private Response parseV12Response(String responseData) {
        String modeByteHex = responseData.substring(10, 12);
        String statusByteHex = responseData.substring(12, 14);
        try {
            int responseMode = Integer.parseInt(modeByteHex, 16);
            int status = Integer.parseInt(statusByteHex, 16);
            boolean isSuccess = status == STATUS_SUCCESS && responseMode == mode;

            String modeName = getModeNameByCode(mode);
            String result;
            if (isSuccess) {
                result = modeName + "设置成功";
            } else if (status == STATUS_MODE_NOT_EXIST) {
                result = modeName + "设置失败：模式不存在";
            } else {
                result = modeName + "设置失败(status=0x" + statusByteHex
                        + ", mode=0x" + modeByteHex + ")";
            }

            int resultValue = isSuccess ? mode : status;
            return completed(isSuccess ? ResultCode.SUCCESS : ResultCode.FAILED, resultValue, result);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析EQ模式 V12 响应失败: " + responseData, e);
            return completed(ResultCode.FAILED, 0, "解析EQ模式响应失败");
        }
    }

    private Response parseV11Response(String responseData) {
        String modeByteHex = responseData.substring(10, 12);
        try {
            int responseMode = Integer.parseInt(modeByteHex, 16);
            boolean isSuccess = responseMode == mode;
            String modeName = getModeNameByCode(mode);
            String result = isSuccess
                    ? modeName + "设置成功"
                    : modeName + "设置失败(mode=0x" + modeByteHex + ")";
            int resultValue = isSuccess ? mode : 0;
            return completed(isSuccess ? ResultCode.SUCCESS : ResultCode.FAILED, resultValue, result);
        } catch (NumberFormatException e) {
            HsLog.e(TAG, "解析EQ模式 V11 响应失败: " + responseData, e);
            return completed(ResultCode.FAILED, 0, "解析EQ模式响应失败");
        }
    }

    @Override
    public boolean isResponseMatch(String responseData) {
        if (isV12Response(responseData)) {
            return true;
        }
        // V11 回显帧与主动上报同形；仅当 mode 与请求一致时才认作本命令应答，
        // 避免等待期间误完成成其他模式的事件帧（事件通道由 InboundFrameDemux 独立投递）。
        return isV11Response(responseData) && parseModeByte(responseData) == mode;
    }

    private static boolean isV12Response(String responseData) {
        return ProtocolValues.isFixedFrame(responseData, V12_RESPONSE_PREFIX, V12_RESPONSE_LENGTH);
    }

    private static boolean isV11Response(String responseData) {
        return ProtocolValues.isFixedFrame(responseData, V11_RESPONSE_PREFIX, V11_RESPONSE_LENGTH);
    }

    private static int parseModeByte(String responseData) {
        try {
            return Integer.parseInt(responseData.substring(10, 12), 16);
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            return -1;
        }
    }

    private String getModeNameByCode(int modeCode) {
        switch (modeCode) {
            case MUSIC_MODE:
                return "音乐模式";
            case MOVIE_MODE:
                return "电影模式";
            case NEWS_MODE:
                return "新闻模式";
            case ELDERLY_MODE:
                return "老年人模式";
            case SPORT_MODE:
                return "运动模式";
            case CUSTOM_MODE:
                return "自定义模式";
            default:
                return "未知模式";
        }
    }
}
