package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;
import com.yovo.lib_ai.error.YoAiHttpException;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.error.YoAiTransportException;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * {@link YoRealtime.Connection} 的 WebSocket 实现。
 *
 * <p>特性：</p>
 * <ul>
 *   <li>共享静态基础 {@link OkHttpClient} 连接池与分发器，避免每次通话重复拉起线程池；</li>
 *   <li>保存 {@code newWebSocket()} 返回句柄，握手未完成时调用 {@link #close()} 可立刻取消建连；</li>
 *   <li>解析出的 {@link YoRealtimeOutput} 通过单线程顺序派发给调用方，读循环无阻塞；</li>
 *   <li>配置中的 {@link YoRealtimeDialect#holdsAudioUntilConfigured()} 控制是否在 {@code session.updated} 前暂存音频。</li>
 * </ul>
 */
final class RealtimeWebSocketLink implements YoRealtime.Connection {

    private static final long CONNECT_TIMEOUT_MILLIS = 10_000L;
    private static final long WRITE_TIMEOUT_MILLIS = 10_000L;
    private static final long PING_INTERVAL_MILLIS = 20_000L;
    private static final int MAX_BUFFERED_AUDIO_BYTES = 512 * 1024;

    private static final OkHttpClient SHARED_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(WRITE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .pingInterval(PING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build();

    private final AtomicBoolean opened = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean sessionReady = new AtomicBoolean();
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();

    private final Object audioLock = new Object();
    private final ArrayDeque<byte[]> pendingAudio = new ArrayDeque<>();
    private int pendingAudioBytes;

    private final ExecutorService callbacks = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "lib_ai-realtime");
        thread.setDaemon(true);
        return thread;
    });

    @Nullable
    private volatile YoRealtimeConfig config;
    @Nullable
    private volatile YoRealtimeListener listener;

    private final WebSocketListener socketListener = new WebSocketListener() {
        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            socket.set(webSocket);
            if (closed.get()) {
                webSocket.close(1000, "bye");
            }
        }

        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            onFrame(text);
        }

        @Override
        public void onFailure(
                @NonNull WebSocket webSocket,
                @NonNull Throwable throwable,
                @Nullable Response response) {
            if (closed.get()) {
                return;
            }
            terminate(false, failureKind(response, throwable), failureMessage(response, throwable));
        }

        @Override
        public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            webSocket.close(1000, "bye");
            if (closed.get()) {
                return;
            }
            terminate(false, YoAiErrorKind.NETWORK, "连接被服务端关闭（" + code
                    + (reason.isEmpty() ? "" : " " + reason) + "）");
        }

        @Override
        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            if (closed.get()) {
                return;
            }
            terminate(false, YoAiErrorKind.NETWORK, "连接被服务端关闭（" + code
                    + (reason.isEmpty() ? "" : " " + reason) + "）");
        }
    };

    RealtimeWebSocketLink() {
    }

    void open(@NonNull YoRealtimeConfig config, @NonNull YoRealtimeListener listener) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(listener, "listener");
        if (closed.get()) {
            throw new IllegalStateException("这个链接已经关闭，重开请新建一个实例");
        }
        if (!opened.compareAndSet(false, true)) {
            throw new IllegalStateException("同一个链接只能 open 一次");
        }
        this.config = config;
        this.listener = listener;
        try {
            Request.Builder builder = new Request.Builder().url(RealtimeProtocol.webSocketUrl(config));
            for (Map.Entry<String, String> header : RealtimeProtocol.handshakeHeaders(config).entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
            WebSocket handle = SHARED_CLIENT.newWebSocket(builder.build(), socketListener);
            socket.compareAndSet(null, handle);
            if (closed.get()) {
                handle.cancel();
            }
        } catch (RuntimeException exception) {
            terminate(false, YoAiErrorKind.PROTOCOL, "建立 WebSocket 失败：" + messageOf(exception));
        }
    }

    @Override
    public void send(@NonNull YoRealtimeInput input) {
        Objects.requireNonNull(input, "input");
        if (closed.get()) {
            return;
        }
        switch (input) {
            case YoRealtimeInput.Audio audio -> appendAudio(audio.pcm16());
            case YoRealtimeInput.Cancel ignored ->
                    sendControl(RealtimeProtocol::encodeResponseCancel, "response.cancel");
            case YoRealtimeInput.Truncate truncate -> {
                try {
                    sendFrame(RealtimeProtocol.encodeTruncate(
                            truncate.itemId(), truncate.contentIndex(), truncate.audioEndMs()));
                } catch (JSONException exception) {
                    terminate(false, YoAiErrorKind.PROTOCOL,
                            "conversation.item.truncate 编码失败：" + messageOf(exception));
                }
            }
            case YoRealtimeInput.Commit ignored ->
                    sendControl(RealtimeProtocol::encodeInputCommit, "input_audio_buffer.commit");
            case YoRealtimeInput.Clear ignored ->
                    sendControl(RealtimeProtocol::encodeInputClear, "input_audio_buffer.clear");
            case YoRealtimeInput.CreateResponse ignored ->
                    sendControl(RealtimeProtocol::encodeResponseCreate, "response.create");
            case YoRealtimeInput.ToolOutput tool -> {
                try {
                    sendFrame(RealtimeProtocol.encodeToolOutput(tool.callId(), tool.output()));
                } catch (JSONException exception) {
                    terminate(false, YoAiErrorKind.PROTOCOL,
                            "function_call_output 编码失败：" + messageOf(exception));
                }
            }
        }
    }

    @Override
    public void close() {
        terminate(true, null, null);
    }

    // ---------------------------------------------------------------- 上行

    private void appendAudio(@NonNull byte[] pcm16) {
        if (pcm16.length < 2) {
            return;
        }
        byte[] frame = (pcm16.length & 1) == 0 ? pcm16 : Arrays.copyOf(pcm16, pcm16.length - 1);
        if (!sessionReady.get()) {
            buffer(frame);
            return;
        }
        sendAudio(frame);
    }

    private void sendControl(ControlEncoder encoder, @NonNull String name) {
        if (closed.get()) {
            return;
        }
        try {
            sendFrame(encoder.encode());
        } catch (JSONException exception) {
            terminate(false, YoAiErrorKind.PROTOCOL, name + " 编码失败：" + messageOf(exception));
        }
    }

    @FunctionalInterface
    private interface ControlEncoder {
        @NonNull
        JSONObject encode() throws JSONException;
    }

    // ---------------------------------------------------------------- 收帧

    private void onFrame(@NonNull String text) {
        if (closed.get()) {
            return;
        }
        RealtimeProtocol.Frame parsed;
        try {
            parsed = RealtimeProtocol.parse(text);
        } catch (YoAiProtocolException exception) {
            terminate(false, YoAiErrorKind.PROTOCOL, messageOf(exception));
            return;
        }
        if (parsed == null) {
            return;
        }
        switch (parsed) {
            case RealtimeProtocol.Frame.SessionCreated ignored -> {
                YoRealtimeConfig snapshot = config;
                if (snapshot == null) {
                    return;
                }
                sendSessionUpdate(snapshot);
                if (!snapshot.dialect().holdsAudioUntilConfigured()
                        && sessionReady.compareAndSet(false, true)) {
                    flushBufferedAudio();
                }
                post(new YoRealtimeOutput.Opened(
                        snapshot.inputWireFormat().sampleRateHz(),
                        snapshot.outputWireFormat().sampleRateHz()));
            }
            case RealtimeProtocol.Frame.SessionUpdated ignored -> {
                if (sessionReady.compareAndSet(false, true)) {
                    flushBufferedAudio();
                }
            }
            case RealtimeProtocol.Frame.Output outputFrame -> post(outputFrame.output());
        }
    }

    private void sendSessionUpdate(@NonNull YoRealtimeConfig snapshot) {
        try {
            sendFrame(RealtimeProtocol.encodeSessionUpdate(snapshot));
        } catch (JSONException exception) {
            terminate(false, YoAiErrorKind.PROTOCOL, "session.update 编码失败：" + messageOf(exception));
        }
    }

    private void sendAudio(@NonNull byte[] pcm16) {
        try {
            if (!sendFrame(RealtimeProtocol.encodeAudioAppend(pcm16))) {
                terminate(false, YoAiErrorKind.NETWORK, "上行音频发送失败");
            }
        } catch (JSONException exception) {
            terminate(false, YoAiErrorKind.PROTOCOL, "input_audio_buffer.append 编码失败：" + messageOf(exception));
        }
    }

    private boolean sendFrame(@NonNull JSONObject frame) {
        WebSocket current = socket.get();
        if (current == null) {
            return false;
        }
        return current.send(frame.toString());
    }

    private void buffer(@NonNull byte[] frame) {
        synchronized (audioLock) {
            pendingAudio.addLast(frame);
            pendingAudioBytes += frame.length;
            while (pendingAudioBytes > MAX_BUFFERED_AUDIO_BYTES) {
                byte[] oldest = pendingAudio.pollFirst();
                if (oldest == null) {
                    break;
                }
                pendingAudioBytes -= oldest.length;
            }
        }
    }

    private void flushBufferedAudio() {
        while (!closed.get()) {
            byte[] next;
            synchronized (audioLock) {
                next = pendingAudio.pollFirst();
                if (next != null) {
                    pendingAudioBytes -= next.length;
                }
            }
            if (next == null) {
                return;
            }
            sendAudio(next);
        }
    }

    // ---------------------------------------------------------------- 收尾

    private void terminate(boolean graceful, @Nullable YoAiErrorKind kind, @Nullable String message) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        sessionReady.set(false);
        WebSocket current = socket.getAndSet(null);
        if (current != null) {
            if (graceful) {
                current.close(1000, "bye");
            } else {
                current.cancel();
            }
        }
        synchronized (audioLock) {
            pendingAudio.clear();
            pendingAudioBytes = 0;
        }
        if (!graceful) {
            if (kind != null) {
                postTerminal(new YoRealtimeOutput.Failed(kind, message == null ? "" : message));
            }
            postTerminal(new YoRealtimeOutput.Closed());
        } else {
            listener = null;
        }
        callbacks.shutdown();
    }

    private void post(@NonNull YoRealtimeOutput output) {
        try {
            callbacks.execute(() -> deliver(output, false));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void postTerminal(@NonNull YoRealtimeOutput output) {
        try {
            callbacks.execute(() -> deliver(output, true));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void deliver(@NonNull YoRealtimeOutput output, boolean terminal) {
        if (!terminal && closed.get()) {
            return;
        }
        YoRealtimeListener target = listener;
        if (target == null) {
            return;
        }
        try {
            target.onOutput(output);
        } catch (RuntimeException ignored) {
        }
    }

    // ---------------------------------------------------------------- 失败分类

    @NonNull
    private static YoAiErrorKind failureKind(@Nullable Response response, @NonNull Throwable throwable) {
        if (response != null) {
            return YoAiErrors.kindOf(new YoAiHttpException(
                    "WebSocket 握手被拒绝", response.code(), null, null, null));
        }
        return YoAiErrors.kindOf(new YoAiTransportException(messageOf(throwable), throwable));
    }

    @NonNull
    private static String failureMessage(@Nullable Response response, @NonNull Throwable throwable) {
        String detail = messageOf(throwable);
        if (response == null) {
            return detail;
        }
        return "WebSocket 握手被拒绝（HTTP " + response.code() + "）：" + detail;
    }

    @NonNull
    private static String messageOf(@Nullable Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank()
                ? throwable.getClass().getSimpleName()
                : message;
    }
}
