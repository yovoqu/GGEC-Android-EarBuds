package com.yovo.lib_bluetooth.internal.classic.testing;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP 测试用 Fake Socket，提供内存读写流模拟 {@link BluetoothSocket} 行为。
 */
public final class FakeYoCbtSppSocket {

    private final BluetoothDevice device;
    private final UUID uuid;
    private final int channel;

    private boolean connected;
    private boolean closed;

    private final ByteArrayOutputStream outputStream;
    private byte[] preloadedInputData;
    private int inputReadOffset;

    /**
     * 创建绑定到给定设备与 UUID 端点的假 Socket。
     *
     * @param device 远端蓝牙设备（可通过
     *               {@link android.bluetooth.BluetoothAdapter#getRemoteDevice(String)} 获取）
     * @param uuid   SPP 服务 UUID
     */
    public FakeYoCbtSppSocket(@NonNull BluetoothDevice device, @NonNull UUID uuid) {
        this(device, uuid, -1);
    }

    /**
     * 创建绑定到给定设备、UUID 与 RFCOMM 信道的假 Socket。
     *
     * @param device  远端蓝牙设备
     * @param uuid    SPP 服务 UUID（基于信道的连接可为 null）
     * @param channel RFCOMM 信道号，未指定时为 -1
     */
    public FakeYoCbtSppSocket(@NonNull BluetoothDevice device,
                              @Nullable UUID uuid,
                              int channel) {
        this.device = device;
        this.uuid = uuid;
        this.channel = channel;
        this.connected = false;
        this.closed = false;
        this.outputStream = new ByteArrayOutputStream();
        this.preloadedInputData = new byte[0];
        this.inputReadOffset = 0;
    }

    // ── 连接生命周期 ─────────────────────────────────────────

    /**
     * 模拟连接 Socket。
     *
     * <p>将 connected 标志设为 {@code true}。若 Socket 已关闭则抛出 {@link IOException}。
     *
     * @throws IOException 若 Socket 已关闭
     */
    public void connect() throws IOException {
        if (closed) {
            throw new IOException("Socket closed");
        }
        connected = true;
    }

    /**
     * 模拟关闭 Socket。
     *
     * <p>将 Socket 标记为已关闭且未连接。
     */
    public void close() {
        closed = true;
        connected = false;
    }

    /**
     * 返回 Socket 的连接状态。
     *
     * @return 已连接且尚未关闭时为 {@code true}
     */
    public boolean isConnected() {
        return connected && !closed;
    }

    /**
     * 返回此 Socket 所连接（或曾连接）的远端蓝牙设备。
     */
    @NonNull
    public BluetoothDevice getRemoteDevice() {
        return device;
    }

    // ── 流访问 ────────────────────────────────────────────────

    /**
     * 返回从预加载数据读取的输入流。
     *
     * @return 基于 {@link #setInputData(byte[])} 所设数据的 {@link ByteArrayInputStream}
     * @throws IOException 若 Socket 未连接或已关闭
     */
    @NonNull
    public InputStream getInputStream() throws IOException {
        checkConnected();
        return new ByteArrayInputStream(
                preloadedInputData, inputReadOffset,
                preloadedInputData.length - inputReadOffset);
    }

    /**
     * 返回捕获所有写入字节的输出流。
     *
     * @return 累积写入数据的 {@link ByteArrayOutputStream}
     * @throws IOException 若 Socket 未连接或已关闭
     */
    @NonNull
    public OutputStream getOutputStream() throws IOException {
        checkConnected();
        return outputStream;
    }

    // ── 测试配置 ───────────────────────────────────────────

    /**
     * 预加载输入流后续读取将返回的数据，模拟从远端设备接收的数据。
     *
     * @param data 可供读取的字节
     */
    public void setInputData(@NonNull byte[] data) {
        this.preloadedInputData = data != null ? data : new byte[0];
        this.inputReadOffset = 0;
    }

    /**
     * 向预加载输入缓冲区追加数据。
     *
     * @param data 要追加的字节
     */
    public void appendInputData(@NonNull byte[] data) {
        if (data == null || data.length == 0) return;
        byte[] combined = new byte[preloadedInputData.length + data.length];
        System.arraycopy(preloadedInputData, 0, combined, 0, preloadedInputData.length);
        System.arraycopy(data, 0, combined, preloadedInputData.length, data.length);
        this.preloadedInputData = combined;
    }

    /**
     * 返回自上次重置以来写入输出流的所有字节，模拟发往远端设备的数据。
     *
     * @return 累积的已写字节
     */
    @NonNull
    public byte[] getWrittenBytes() {
        return outputStream.toByteArray();
    }

    /**
     * 重置已写字节缓冲区，清除所有已记录输出。
     */
    public void resetWrittenBytes() {
        outputStream.reset();
    }

    /**
     * 返回此 Socket UUID 的 {@link ParcelUuid} 表示。
     */
    @Nullable
    public ParcelUuid getUuid() {
        return uuid != null ? new ParcelUuid(uuid) : null;
    }

    /**
     * 返回 RFCOMM 信道号。
     */
    public int getChannel() {
        return channel;
    }

    // ── 内部 ─────────────────────────────────────────────────────

    private void checkConnected() throws IOException {
        if (closed) {
            throw new IOException("Socket closed");
        }
        if (!connected) {
            throw new IOException("Socket not connected");
        }
    }
}
