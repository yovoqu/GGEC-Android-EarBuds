package com.ggec.earbuds.ui.device.scan;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * 扫描卡片动效的唯一参数。
 *
 * <p>时长与曲线取自 Material 3 motion：进入用 emphasized decelerate，
 * 位移用 standard，离开用 emphasized accelerate。RecyclerView 只负责执行，
 * 不在动画器里再写一套数字。</p>
 */
public final class ScanListMotion {

    /** 新卡片出现。Material duration medium1。 */
    public static final int APPEAR_MS = 250;

    /** 排序换位。Material duration medium2。 */
    public static final int MOVE_MS = 300;

    /** 卡片离开。Material duration short4。 */
    public static final int REMOVE_MS = 200;

    /** 出现时自下方移入的距离（dp）。 */
    public static final float APPEAR_OFFSET_DP = 12f;

    /** cubic-bezier(0.05, 0.7, 0.1, 1) */
    public static final Interpolator APPEAR = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    /** cubic-bezier(0.2, 0, 0, 1) */
    public static final Interpolator MOVE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    /** cubic-bezier(0.3, 0, 0.8, 0.15) */
    public static final Interpolator EXIT = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    private ScanListMotion() {
    }
}
