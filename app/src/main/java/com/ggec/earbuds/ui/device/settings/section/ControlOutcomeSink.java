package com.ggec.earbuds.ui.device.settings.section;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.device.DeviceControlOutcome;

/**
 * 持有「未确认」控件状态的子组件所用的回调：写入失败时丢弃该状态并回到未上报呈现。
 *
 * <p>只在一个设置页内部的渲染宿主与其子卡片之间使用，不外泄。</p>
 */
interface ControlOutcomeSink {

    /**
     * @return {@code true} 表示这次失败与本单位持有的未确认状态有关（需要重建）。
     */
    boolean onControlOutcome(@NonNull DeviceControlOutcome outcome);
}
