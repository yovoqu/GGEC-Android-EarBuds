package com.ggec.earbuds.domain.record.model;

/**
 * 扫描到的附近 Record 设备（APP 呈现词汇）。
 */
public record RecordNearbyDevice(String macAddress, String name, int rssi) {
}
