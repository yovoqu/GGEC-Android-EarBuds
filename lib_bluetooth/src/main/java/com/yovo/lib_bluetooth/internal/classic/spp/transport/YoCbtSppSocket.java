package com.yovo.lib_bluetooth.internal.classic.spp.transport;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP BluetoothSocket 封装，提供连接、流访问与关闭操作。
 */

public interface YoCbtSppSocket extends Closeable {

    void connect() throws IOException;

    @Override
    void close() throws IOException;

    InputStream getInputStream() throws IOException;

    OutputStream getOutputStream() throws IOException;

    boolean isConnected();

    /**
     * 返回底层 RFCOMM socket 实际协商到的物理通道号；无法获取时返回 -1。
     * 用于把 UUID 连接归一到其真实占用的 RFCOMM 通道，做跨端点冲突检测。
     */
    default int getResolvedRfcommChannel() {
        return -1;
    }
}
