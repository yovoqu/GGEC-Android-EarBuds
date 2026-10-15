package com.yovo.lib_bluetooth.internal.classic.testing;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 测试用 Fake {@link BluetoothProfile}，可控制已连接设备及连接状态。
 */
public final class FakeYoCbtProfileProxy implements BluetoothProfile {

    private final int profileType;
    private final List<BluetoothDevice> connectedDevices;
    private final java.util.concurrent.ConcurrentHashMap<BluetoothDevice, Integer> connectionStates =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 为给定 Profile 类型创建假 Profile 代理。
     *
     * @param profileType Android 蓝牙 Profile 常量
     *                    （如 {@link BluetoothProfile#A2DP}、{@link BluetoothProfile#HEADSET}）
     */
    public FakeYoCbtProfileProxy(int profileType) {
        this.profileType = profileType;
        this.connectedDevices = new ArrayList<>();
    }

    /**
     * 返回此代理所代表的 Android Profile 类型常量。
     */
    public int getProfileType() {
        return profileType;
    }

    // ── BluetoothProfile 实现 ──────────────────────────────

    @Override
    @NonNull
    public List<BluetoothDevice> getConnectedDevices() {
        synchronized (connectedDevices) {
            return new ArrayList<>(connectedDevices);
        }
    }

    @Override
    @NonNull
    public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        if (states == null || states.length == 0) {
            return getConnectedDevices();
        }
        synchronized (connectedDevices) {
            List<BluetoothDevice> matching = new ArrayList<>();
            for (BluetoothDevice device : connectedDevices) {
                int state = getConnectionState(device);
                for (int s : states) {
                    if (state == s) {
                        matching.add(device);
                        break;
                    }
                }
            }
            return matching;
        }
    }

    @Override
    public int getConnectionState(@NonNull BluetoothDevice device) {
        Integer state = connectionStates.get(device);
        return state != null ? state : BluetoothProfile.STATE_DISCONNECTED;
    }

    // ── 测试配置 ───────────────────────────────────────────

    /**
     * 设置当前已连接设备列表。
     *
     * <p>列表中每个设备自动设为 {@link BluetoothProfile#STATE_CONNECTED}。
     * 不在此列表中的设备原有连接状态将被移除。
     *
     * @param devices 要报告为已连接的设备（可为空）
     */
    public void setConnectedDevices(@NonNull List<BluetoothDevice> devices) {
        synchronized (connectedDevices) {
            connectedDevices.clear();
            if (devices != null) {
                connectedDevices.addAll(devices);
                for (BluetoothDevice device : devices) {
                    connectionStates.put(device, BluetoothProfile.STATE_CONNECTED);
                }
            }

            // 仅保留当前已连接设备的状态
            connectionStates.keySet().retainAll(connectedDevices);
        }
    }

    /**
     * 设置指定设备的连接状态。
     *
     * @param device 蓝牙设备
     * @param state  {@link BluetoothProfile} 状态常量
     *               （{@code STATE_DISCONNECTED}、{@code STATE_CONNECTING}、
     *               {@code STATE_CONNECTED}、{@code STATE_DISCONNECTING}）
     */
    public void setConnectionState(@NonNull BluetoothDevice device, int state) {
        connectionStates.put(device, state);
        synchronized (connectedDevices) {
            if (state == BluetoothProfile.STATE_CONNECTED && !connectedDevices.contains(device)) {
                connectedDevices.add(device);
            } else if (state != BluetoothProfile.STATE_CONNECTED) {
                connectedDevices.remove(device);
            }
        }
    }

    /**
     * 从代理中完全移除设备。
     *
     * @param device 要移除的设备
     */
    public void removeDevice(@NonNull BluetoothDevice device) {
        synchronized (connectedDevices) {
            connectedDevices.remove(device);
        }
        connectionStates.remove(device);
    }

    /**
     * 清除所有设备并重置所有状态。
     */
    public void clear() {
        synchronized (connectedDevices) {
            connectedDevices.clear();
        }
        connectionStates.clear();
    }
}
