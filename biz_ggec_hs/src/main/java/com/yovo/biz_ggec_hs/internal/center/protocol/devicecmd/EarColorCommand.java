package com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolValues;
import com.yovo.biz_ggec_hs.provider.lib_common.log.HsLog;

/**
 * Author: tingfeng.yang
 * Date: 2025-07-16
 * Description:
 * 获取耳机颜色命令
 * 负责从耳机获取硬件颜色信息
 *
 * <p>响应等待按查询来源分开，不是一个数值覆盖两种场景：</p>
 * <ul>
 *   <li>{@link QueryOrigin#AUTO}（{@link #AUTO_QUERY_TIMEOUT_MS} = 800ms）：READY 后引擎自主
 *       补查一次。它和设备不支持 {@code 99EC91} 时的快速失败同义，占住串行命令队列的每一毫秒
 *       都会推迟紧随其后的首屏业务命令，所以取短等待。</li>
 *   <li>{@link QueryOrigin#MANUAL}（{@link #MANUAL_QUERY_TIMEOUT_MS} = 2000ms）：用户显式触发，
 *       与其它命令的默认等待一致。设备回包 800–2000ms 属于正常时延，手动查询不能把它判成超时。</li>
 * </ul>
 *
 * <p>超时会隔离（quarantine）{@code 99EC91} 响应族
 * {@link Command#getLateResponseGuardMs()}（1000ms）：隔离窗口内到达的迟到回包被丢弃。
 * 因此手动查询的等待必须明显大于自动补查的 800ms——否则 1500ms 到达的合法回包会先被超时判掉，
 * 再被隔离窗吞掉，手动查询永远拿不到值；反之自动补查用短等待，最坏情况下也只是这一次补查失败，
 * 后续首页 / 业务帧不受影响。</p>
 */
public class EarColorCommand extends Command {
    private static final String TAG = "EarColorCommand";

    /** 自动补查（READY 后一次）的响应等待。 */
    public static final long AUTO_QUERY_TIMEOUT_MS = 800L;

    /** 手动查询的响应等待，与 {@link Command#getTimeoutMs()} 的默认值一致。 */
    public static final long MANUAL_QUERY_TIMEOUT_MS = 2000L;

    /** 这条颜色查询由谁触发：决定响应等待，除此之外两者行为完全相同。 */
    public enum QueryOrigin {
        /** 引擎在会话就绪后自主补查。 */
        AUTO,
        /** 调用方显式查询。 */
        MANUAL
    }

    // 响应前缀
    // 注意：根据协议文档，应该返回99EC8B0001，但实际上返回的是与请求相同的99EC910001
    private static final String RESPONSE_PREFIX = "99EC910001";

    // 发送命令数据
    private static final String COMMAND_DATA = "99EC91000100" + "1234";

    // 耳机颜色常量
    public static final int COLOR_BLACK = 0; // 黑色

    private final long responseTimeoutMs;

    // 耳机颜色结果
    private int earColor = COLOR_BLACK;

    /**
     * 构造方法
     *
     * @param origin 查询来源，决定响应等待；为空视为契约错误，不做兜底
     */
    public EarColorCommand(QueryOrigin origin) {
        super();
        if (origin == null) {
            throw new IllegalArgumentException("耳机颜色查询来源不能为空");
        }
        setCommandPrefix("99EC91");
        this.responseTimeoutMs = origin == QueryOrigin.AUTO
                ? AUTO_QUERY_TIMEOUT_MS
                : MANUAL_QUERY_TIMEOUT_MS;
    }

    @Override
    public String getCommandData() {
        return COMMAND_DATA;
    }

    @Override
    public long getTimeoutMs() {
        return responseTimeoutMs;
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
