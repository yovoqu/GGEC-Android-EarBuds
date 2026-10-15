package com.yovo.lib_ai.internal.transport;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiTransportException;

import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * 一条只搬运文本帧的 WebSocket。不解析协议，不持有通道。
 */
public final class WebSocketPipe {

    public interface Incoming {
        void onText(@NonNull String text);

        void onTransportClosed(@NonNull Throwable cause);
    }

    private final Incoming incoming;
    private final Object sendLock = new Object();
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private final AtomicBoolean localClose = new AtomicBoolean();

    private WebSocketPipe(Incoming incoming) {
        this.incoming = incoming;
    }

    public static WebSocketPipe open(
            OkHttpClient client,
            Request request,
            Incoming incoming,
            long connectTimeoutMillis) {
        WebSocketPipe pipe = new WebSocketPipe(incoming);
        CountDownLatch opened = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                pipe.attach(webSocket);
                opened.countDown();
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                incoming.onText(text);
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable throwable, @Nullable Response response) {
                failure.compareAndSet(null, throwable);
                pipe.reportClosed(throwable);
                opened.countDown();
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                pipe.reportClosed(new YoAiTransportException(
                        reason == null || reason.isBlank() ? "WebSocket closed" : reason,
                        null));
            }
        });
        try {
            if (!opened.await(connectTimeoutMillis, TimeUnit.MILLISECONDS)) {
                pipe.close();
                throw new YoAiTransportException("WebSocket connect timed out", null);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            pipe.close();
            throw new YoAiTransportException("WebSocket connect interrupted", exception);
        }
        if (pipe.socket.get() == null) {
            Throwable cause = failure.get();
            throw new YoAiTransportException(
                    cause == null || cause.getMessage() == null ? "WebSocket connect failed" : cause.getMessage(),
                    cause);
        }
        return pipe;
    }

    public void send(String text) {
        if (localClose.get()) {
            throw new YoAiTransportException("WebSocket is closed", null);
        }
        WebSocket current = socket.get();
        if (current == null) {
            synchronized (sendLock) {
                current = socket.get();
                if (current == null) {
                    pending.add(text);
                    return;
                }
            }
        }
        if (!current.send(text)) {
            throw new YoAiTransportException("WebSocket send failed", null);
        }
    }

    public void close() {
        if (!localClose.compareAndSet(false, true)) {
            return;
        }
        WebSocket current = socket.getAndSet(null);
        if (current != null) {
            current.close(1000, "bye");
        }
        synchronized (sendLock) {
            pending.clear();
        }
    }

    private void attach(WebSocket webSocket) {
        socket.set(webSocket);
        synchronized (sendLock) {
            String text;
            while ((text = pending.poll()) != null) {
                webSocket.send(text);
            }
        }
    }

    private void reportClosed(Throwable cause) {
        if (localClose.get()) {
            return;
        }
        localClose.set(true);
        incoming.onTransportClosed(cause);
    }
}
