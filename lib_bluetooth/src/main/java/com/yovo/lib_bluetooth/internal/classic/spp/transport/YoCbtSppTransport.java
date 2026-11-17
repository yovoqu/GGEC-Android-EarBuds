package com.yovo.lib_bluetooth.internal.classic.spp.transport;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description SPP 底层传输封装，管理 InputStream/OutputStream 与 socket 关闭。
 */

public final class YoCbtSppTransport {

    private static final String TAG = "YoCbtSppTransport";

    private final Closeable socket;
    private final InputStream input;
    private final OutputStream output;

    public YoCbtSppTransport(@NonNull Closeable socket,
                             @NonNull InputStream input,
                             @NonNull OutputStream output) {
        this.socket = socket;
        this.input = input;
        this.output = output;
    }

    public int read(@NonNull byte[] buffer) throws IOException {
        return input.read(buffer);
    }

    public synchronized void write(@NonNull byte[] data) throws IOException {
        output.write(data);
        output.flush();
    }

    public void close() {
        closeStream(input);
        closeStream(output);
        closeStream(socket);
    }

    /**
     * 逐个关闭同一条 SPP 链路上的流与 socket：任一步失败都不能阻止其余两步关闭，
     * 因此不向上抛，但失败必须留证（关闭失败会泄漏 fd）。
     */
    private static void closeStream(@NonNull Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException closeFailure) {
            YoBtLogger.w(TAG, "SPP stream close failed", closeFailure);
        }
    }
}
