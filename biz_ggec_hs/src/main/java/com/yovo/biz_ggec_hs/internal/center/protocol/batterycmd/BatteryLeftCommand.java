package com.yovo.biz_ggec_hs.internal.center.protocol.batterycmd;

/**
 * 查询左耳电量。
 */
public final class BatteryLeftCommand extends AbstractBatteryLevelCommand {
    public BatteryLeftCommand() {
        super(1, "左耳");
    }
}
