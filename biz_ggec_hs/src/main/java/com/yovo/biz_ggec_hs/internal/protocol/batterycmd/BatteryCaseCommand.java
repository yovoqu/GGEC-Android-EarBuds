package com.yovo.biz_ggec_hs.internal.protocol.batterycmd;

/**
 * 查询充电盒电量。
 */
public final class BatteryCaseCommand extends AbstractBatteryLevelCommand {
    public BatteryCaseCommand() {
        super(3, "充电盒");
    }
}
