package com.yovo.biz_ggec_hs.internal.center.link;

import com.yovo.biz_ggec_hs.internal.center.port.HsClassicLink;

/**
 * 经典蓝牙下一步。已连接是「已配对且 A2DP 或 HFP 已连上」，与 HS 建链规划一致。
 * 音频事实还没到、或正在配对时只等，不发下一条命令。
 */
public final class HsClassicPlan {

    /** 与 HS {@code SppConnectConfig} 的默认等待一致。配对弹窗期间不计这个时限。 */
    public static final long WAIT_MILLIS = 15_000L;

    public enum Step {
        READY,
        CREATE_BOND,
        CONNECT_AUDIO,
        WAIT,
        FAIL
    }

    private HsClassicPlan() {
    }

    public static Step step(HsClassicLink.Bond bond, HsClassicLink.Audio audio, boolean bondAttempted) {
        if (bond == HsClassicLink.Bond.BONDED && audio == HsClassicLink.Audio.UP) {
            return Step.READY;
        }
        if (bond == HsClassicLink.Bond.BONDING
                || audio == HsClassicLink.Audio.BUSY
                || audio == HsClassicLink.Audio.UNKNOWN) {
            return Step.WAIT;
        }
        if (bond == HsClassicLink.Bond.NONE && bondAttempted) {
            return Step.FAIL;
        }
        if (bond == HsClassicLink.Bond.NONE) {
            return Step.CREATE_BOND;
        }
        if (bond == HsClassicLink.Bond.BONDED) {
            return Step.CONNECT_AUDIO;
        }
        return Step.FAIL;
    }

    /** {@code createBond} 被拒绝后看当场的配对事实，不把「正在配对」记成失败。 */
    public static Step afterBondRejected(HsClassicLink.Bond bond) {
        if (bond == HsClassicLink.Bond.BONDED) {
            return Step.CONNECT_AUDIO;
        }
        if (bond == HsClassicLink.Bond.BONDING) {
            return Step.WAIT;
        }
        return Step.FAIL;
    }

    /** 音频连接请求被拒绝后，已经连上的 Profile 仍然算就绪。 */
    public static Step afterAudioRejected(HsClassicLink.Audio audio) {
        if (audio == HsClassicLink.Audio.UP) {
            return Step.READY;
        }
        if (audio == HsClassicLink.Audio.BUSY || audio == HsClassicLink.Audio.UNKNOWN) {
            return Step.WAIT;
        }
        return Step.FAIL;
    }

    /** 配对中不计时。其余等待从这次打开起算。 */
    public static boolean countsTowardTimeout(Step step, HsClassicLink.Bond bond) {
        if (step == Step.READY || step == Step.FAIL) {
            return false;
        }
        return bond != HsClassicLink.Bond.BONDING;
    }
}
