package com.yovo.lib_bluetooth.model.ble;

/**
 * GATT 操作排队优先级。
 */
public enum BleOperationPriority {
    HIGH(0),
    NORMAL(1),
    LOW(2);

    private final int rank;

    BleOperationPriority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
