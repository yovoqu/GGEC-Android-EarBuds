package com.ggec.earbuds.domain.device.model;

/** 电量快照。-1 表示未知（界面显示 --）。 */
public record BatterySnapshot(int left, int right, int caseLevel, boolean caseCharging) {

    public static final int UNKNOWN = -1;

    public static BatterySnapshot unknown() {
        return new BatterySnapshot(UNKNOWN, UNKNOWN, UNKNOWN, false);
    }

    public boolean hasLeft() {
        return left >= 0;
    }

    public boolean hasRight() {
        return right >= 0;
    }

    public boolean hasCase() {
        return caseLevel >= 0;
    }
}
