package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;

/**
 * 初始双工建议策略：根据输出路由与硬件回声消除能力提供默认推荐值。
 *
 * <p>双工模式受用户单一事实来源（SSOT）严格单点控制，运行期用户指令具有最高决策权，
 * 本策略仅在未显式指定时给出初始化建议。</p>
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
