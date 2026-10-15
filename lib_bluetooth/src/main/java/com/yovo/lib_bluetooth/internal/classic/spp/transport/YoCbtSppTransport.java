package com.yovo.lib_bluetooth.internal.classic.spp.transport;

import androidx.annotation.NonNull;

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
        closeQuietly(input);
        closeQuietly(output);
        closeQuietly(socket);
    }

    private static void closeQuietly(Object closeable) {
        if (closeable == null) return;
        try {
            if (closeable instanceof Closeable) {
                ((Closeable) closeable).close();
            } else if (closeable instanceof InputStream) {
                ((InputStream) closeable).close();
            } else if (closeable instanceof OutputStream) {
                ((OutputStream) closeable).close();
            }
        } catch (Throwable ignored) {
        }
    }
}
