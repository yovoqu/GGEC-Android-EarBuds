package com.yovo.lib_bluetooth.internal.classic.internal.android.spp;

import android.bluetooth.BluetoothSocket;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.spp.transport.YoCbtSppSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description Android SPP Socket 封装，适配 {@link YoCbtSppSocket} 接口。
 */

public final class YoCbtAndroidSppSocket implements YoCbtSppSocket {

    @NonNull
    private final BluetoothSocket delegate;

    public YoCbtAndroidSppSocket(@NonNull BluetoothSocket delegate) {
        this.delegate = delegate;
    }

    @Override
    public void connect() throws IOException {
        delegate.connect();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return delegate.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return delegate.getOutputStream();
    }

    @Override
    public boolean isConnected() {
        return delegate.isConnected();
    }

    /**
     * 通过反射读取 {@link BluetoothSocket} 私有字段 {@code mPort}，获取连接后真实占用的
     * RFCOMM 通道号。该字段在 UUID 连接（SDP 内部解析）成功后会被填充为协商到的通道。
     * 反射在部分 ROM 上可能被限制，失败时返回 -1。
     */
    @Override
    public int getResolvedRfcommChannel() {
        try {
            Field portField = BluetoothSocket.class.getDeclaredField("mPort");
            portField.setAccessible(true);
            Object value = portField.get(delegate);
            if (value instanceof Integer) {
                int port = (Integer) value;
                return port > 0 ? port : -1;
            }
        } catch (Throwable ignored) {
            // 字段不存在或被隐藏 API 限制时静默回退
        }
        return -1;
    }
}
