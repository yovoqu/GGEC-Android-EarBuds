package com.yovo.biz_ggec_hs.internal.protocol.batterycmd;

/**
 * 查询右耳电量。
 */
public final class BatteryRightCommand extends AbstractBatteryLevelCommand {
    public BatteryRightCommand() {
        super(2, "右耳");
    }
}
