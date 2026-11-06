package com.ggec.earbuds.domain.ai.voice;

/**
 * 双工策略：把"输出路由"和"回声消除是否生效"两个事实，收敛成一个双工决定。
 *
 * <p><b>为什么需要这一层</b>：全双工的前提不是"设备支持 AEC"，而是"麦克风不会听到
 * 助手自己的声音"。满足这个前提有两条路：</p>
 *
 * <ol>
 *   <li><b>输出是私密路由</b>（有线耳机/蓝牙耳机/USB 耳机）——声音不进扬声器，
 *       麦克风根本收不到，无需 AEC；</li>
 *   <li><b>输出是外放/听筒</b>——必须靠 AEC 把参考信号消掉。</li>
 * </ol>
 *
 * <p>把这两条写成一个纯函数，装配根和测试都用同一套判定，避免"这里判一半、那里判一半"。</p>
 */
public final class VoiceDuplexPolicy {

    private VoiceDuplexPolicy() {
    }

    /**
     * @param privateOutput 输出是否私密路由（耳机/蓝牙/USB），即不会有外放回声风险
     * @param echoCancellationActive 设备侧 AEC 是否真的挂上了
     * @return 可以全双工就 FULL，否则 HALF
     */
    public static VoiceDuplex resolve(boolean privateOutput, boolean echoCancellationActive) {
        return privateOutput || echoCancellationActive
                ? VoiceDuplex.FULL_DUPLEX : VoiceDuplex.HALF_DUPLEX;
    }

    /**
     * 已经请求了 FULL 时，实际能不能保持 FULL。
     *
     * <p>只降不升：请求 FULL、但输出是外放且 AEC 没生效 → 必须落 HALF。
     * 反过来请求 HALF 就保持 HALF（用户/产品显式选择优先）。</p>
     */
    public static VoiceDuplex confirmRequested(VoiceDuplex requested, boolean privateOutput,
                                               boolean echoCancellationActive) {
        if (requested != VoiceDuplex.FULL_DUPLEX) {
            return VoiceDuplex.HALF_DUPLEX;
        }
        return resolve(privateOutput, echoCancellationActive);
    }
}
