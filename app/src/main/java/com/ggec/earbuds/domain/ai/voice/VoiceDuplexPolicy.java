package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;

/**
 * 声学环境双工建议策略（纯无状态建议函数）：根据输出路由与硬件回声消除能力评估“是否具备全双工声学条件”。
 *
 * <p>运行期通话的初始双工模式由用户在「AI 设置 → 语音交流模式 → 默认的模式」中配置的偏好决定，
 * 通话中由 {@link VoiceDuplexStrategy} 与用户即时切换单点控制，本类不覆写用户偏好。</p>
 */
public final class VoiceDuplexPolicy {

    private VoiceDuplexPolicy() {
    }

    /**
     * 根据声学环境推荐默认双工方式。
     *
     * @param privateOutput 输出是否私密路由（耳机/蓝牙/USB）
     * @param echoCancellationActive 设备侧 AEC 是否生效
     * @return 推荐的默认双工方式
     */
    @NonNull
    public static VoiceDuplex resolveDefault(boolean privateOutput, boolean echoCancellationActive) {
        return privateOutput || echoCancellationActive
                ? VoiceDuplex.FULL_DUPLEX : VoiceDuplex.HALF_DUPLEX;
    }

    /**
     * 保持纯函数的默认判定。
     */
    @NonNull
    public static VoiceDuplex resolve(boolean privateOutput, boolean echoCancellationActive) {
        return resolveDefault(privateOutput, echoCancellationActive);
    }
}
