package com.yovo.biz_ggec_hs.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 双连接页聚合查询解析结果。
 */
public class DualConnectionPageData {

    /** 未返回双连接开关状态时使用的占位值 */
    public static final int DUAL_CONNECTION_UNKNOWN = -1;

    /** 双连接开关：0x00=单连接，0x01=双连接 */
    private int dualConnection = DUAL_CONNECTION_UNKNOWN;

    /** 优先设备状态：0x00=已设置，0x01=未设置 */
    private int priorityStatus = 0x01;

    /** 优先设备 MAC；无优先设备或未返回时为 null */
    private String priorityMac;

    private final List<PairedDevice> devices = new ArrayList<>();

    public int getDualConnection() {
        return dualConnection;
    }

    public void setDualConnection(int dualConnection) {
        this.dualConnection = dualConnection;
    }

    public int getPriorityStatus() {
        return priorityStatus;
    }

    public void setPriorityStatus(int priorityStatus) {
        this.priorityStatus = priorityStatus;
    }

    public String getPriorityMac() {
        return priorityMac;
    }

    public void setPriorityMac(String priorityMac) {
        this.priorityMac = priorityMac;
    }

    public List<PairedDevice> getDevices() {
        return Collections.unmodifiableList(devices);
    }

    public void setDevices(List<PairedDevice> deviceList) {
        devices.clear();
        if (deviceList != null) {
            devices.addAll(deviceList);
        }
    }

    @Override
    public String toString() {
        return "DualConnectionPageData{" +
                "dualConnection=" + String.format("0x%02X", dualConnection) +
                ", priorityStatus=" + String.format("0x%02X", priorityStatus) +
                ", priorityMac='" + priorityMac + '\'' +
                ", devices=" + devices +
                '}';
    }
}
