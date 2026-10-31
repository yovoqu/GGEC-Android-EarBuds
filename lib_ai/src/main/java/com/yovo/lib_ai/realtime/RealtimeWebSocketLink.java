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
 * {@link YoRealtimeLink} 的实现：一条 OkHttp WebSocket，跑 Realtime 兼容协议。
 *
 * <p>用 OkHttp 而不是自己写 socket：项目里已经有它（HTTP 和 {@code /v1/responses} 的 WebSocket 都在用），
 * 再加一个 WebSocket 客户端只会多一份要维护的握手、分片、ping/pong 代码。</p>
 *
 * <h2>线程模型</h2>
 * <p>OkHttp 在它自己的分发线程上回调（读线程）。协议解析和 JSON 编码在那条线程上做（很便宜），
 * 但**交给用户的事件一律转投一条单线程执行器**，原因是：</p>
 * <ul>
 *   <li>顺序有保证。文本增量、音频块、打断、结束挤在同一条队列里，出去就是发生的顺序。
 *       如果直接让 OkHttp 线程调 {@code onEvent}，读线程和上层 {@code close()} 的收尾线程会并发进状态机。</li>
 *   <li>用户代码多慢都只慢自己。慢的 {@code AudioTrack.write} 顶多让事件排队，
 *       不会反压到 OkHttp 的读循环上（读满反压会让服务端判定我们这一侧卡住）。</li>
 * </ul>
 *
 * <h2>生命周期</h2>
 * <p>{@code closed} 这一个原子位既是「幂等」的闸门，也是「终态」的闸门：
 * 无论走哪条收尾路径（用户 close、握手失败、读失败、对端关闭、协议错），
 * 只有第一条能通过 CAS，后面的全部是空操作，所以 {@code CLOSED} 只会发一次。</p>
 */
final class RealtimeWebSocketLink implements YoRealtimeLink {

    /** 建立连接的超时。超过它就是连不上，界面要立刻显示原因，不能一直「连接中」。 */
    private static final long CONNECT_TIMEOUT_MILLIS = 10_000L;

    /** 写超时给足：上行是持续的小帧，不该出现攒批写入。 */
    private static final long WRITE_TIMEOUT_MILLIS = 10_000L;

    /**
     * ping 间隔。中间设备（NAT、代理、运营商网关）会把几十秒没流量的连接悄悄掐掉，
     * 而掐掉时不发 close 帧——表现出来就是「还能说话，但再也收不到回答」。
     * 20 秒一次 ping 让链路一直有流量，也让 OkHttp 能及时发现死链。
     */
    private static final long PING_INTERVAL_MILLIS = 20_000L;

    /**
     * 会话建立前允许缓存的上行音频上限（约 16 秒 16k/16bit 单声道）。
     * 正常情况下这里永远是空的（上层收到 {@code OPENED} 才开始采集），
     * 上限是为了防「服务端一直不发 session.created」时把内存吃掉。
     */
    private static final int MAX_BUFFERED_AUDIO_BYTES = 512 * 1024;

    private final AtomicBoolean opened = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean sessionReady = new AtomicBoolean();
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();

    private final Object audioLock = new Object();
    private final ArrayDeque<byte[]> pendingAudio = new ArrayDeque<>();
    private int pendingAudioBytes;

    /** 事件出口。单线程 + 无界队列，天然按提交顺序串行。 */
    private final ExecutorService callbacks = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "lib_ai-realtime");
        thread.setDaemon(true);
        return thread;
    });

    @Nullable
    private volatile YoRealtimeConfig config;
    @Nullable
    private volatile RealtimeSink listener;
    @Nullable
    private volatile OkHttpClient client;

    private final WebSocketListener socketListener = new WebSocketListener() {
        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            socket.set(webSocket);
            if (closed.get()) {
                // close() 抢在连上之前发生：这时候还没有 socket 可关，在这里补一刀，
                // 否则这条连接会一直挂到服务端超时。
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
        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            if (closed.get()) {
                // 我们自己 close 的，收尾已经做过了。
                return;
            }
            terminate(false, YoAiErrorKind.NETWORK, "连接被服务端关闭（" + code
                    + (reason.isEmpty() ? "" : " " + reason) + "）");
        }
    };

    public RealtimeWebSocketLink() {
    }

    @Override
    public void open(@NonNull YoRealtimeConfig config, @NonNull RealtimeSink listener) {
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
            OkHttpClient http = newClient();
            client = http;
            Request.Builder builder = new Request.Builder().url(RealtimeProtocol.webSocketUrl(config));
            // 逐个 header 而不是 Headers.of(Map)：后者是 OkHttp 的 Kotlin 静态方法，
            // 从这里出发只会多拉一层 Kotlin 运行时依赖，对两个头没有收益。
            for (Map.Entry<String, String> header : RealtimeProtocol.handshakeHeaders(config).entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
            Request request = builder.build();
            // newWebSocket 是异步的：返回时还没连上，结果走 socketListener。
            http.newWebSocket(request, socketListener);
        } catch (RuntimeException exception) {
            // 地址非法、头里有换行之类的问题在这里暴露；open 没有返回值，
            // 只能把它变成一条 ERROR + CLOSED，让上层统一处理。
            terminate(false, YoAiErrorKind.PROTOCOL, "建立 WebSocket 失败：" + messageOf(exception));
        }
    }

    @Override
    public void appendAudio(@NonNull byte[] pcm16) {
        Objects.requireNonNull(pcm16, "pcm16");
        if (closed.get() || pcm16.length < 2) {
            return;
        }
        // PCM16 的采样是两字节一组。奇数长度说明最后半个采样被截断了，
        // 留着会让后面所有采样错位半字节——听起来是持续的沙沙声，比丢一字节难查得多。
        byte[] frame = (pcm16.length & 1) == 0 ? pcm16 : Arrays.copyOf(pcm16, pcm16.length - 1);
        if (!sessionReady.get()) {
            buffer(frame);
            return;
        }
        sendAudio(frame);
    }

    @Override
    public void cancelResponse() {
        sendControl(RealtimeProtocol::encodeResponseCancel, "response.cancel");
    }

    @Override
    public void commitInput() {
        sendControl(RealtimeProtocol::encodeInputCommit, "input_audio_buffer.commit");
    }

    @Override
    public void clearInput() {
        sendControl(RealtimeProtocol::encodeInputClear, "input_audio_buffer.clear");
    }

    @Override
    public void createResponse() {
        sendControl(RealtimeProtocol::encodeResponseCreate, "response.create");
    }

    @Override
    public void truncate(@NonNull String itemId, int contentIndex, int audioEndMs) {
        if (closed.get() || itemId.isBlank()) {
            return;
        }
        try {
            send(RealtimeProtocol.encodeTruncate(itemId, contentIndex, audioEndMs));
        } catch (JSONException exception) {
            terminate(false, YoAiErrorKind.PROTOCOL, "conversation.item.truncate 编码失败：" + messageOf(exception));
        }
    }

    @Override
    public void submitToolOutput(@NonNull String callId, @NonNull String output) {
        if (closed.get() || callId.isBlank()) {
            return;
        }
        try {
            send(RealtimeProtocol.encodeToolOutput(callId, output));
        } catch (JSONException exception) {
            terminate(false, YoAiErrorKind.PROTOCOL, "function_call_output 编码失败：" + messageOf(exception));
        }
    }

    private void sendControl(ControlEncoder encoder, @NonNull String name) {
        if (closed.get()) {
            return;
        }
        try {
            send(encoder.encode());
        } catch (JSONException exception) {
            terminate(false, YoAiErrorKind.PROTOCOL, name + " 编码失败：" + messageOf(exception));
        }
    }

    @FunctionalInterface
    private interface ControlEncoder {
        @NonNull
        JSONObject encode() throws JSONException;
    }

    @Override
    public void close() {
        // 幂等：第一条路径做收尾，后面的全部直接返回。
        terminate(true, null, null);
    }

    // ---------------------------------------------------------------- 收帧

    private void onFrame(@NonNull String text) {
        if (closed.get()) {
            return;
        }
        YoRealtimeEvent event;
        try {
            event = RealtimeProtocol.parse(text);
        } catch (YoAiProtocolException exception) {
            // 一条畸形帧说明对端不是 Realtime 端点。继续读只会重复报同一个错，
            // 所以这里断链，让上层决定要不要重开。
            terminate(false, YoAiErrorKind.PROTOCOL, messageOf(exception));
            return;
        }
        if (event == null) {
            // 未知或不关心的事件：忽略。协议一直在加新帧，忽略才能继续跑。
            return;
        }
        if (event.type() == YoRealtimeEventType.OPENED) {
            sendSessionUpdate();
            // GA 要等 session.updated 再放行：update 还在路上时送出的音频会按服务端默认格式解释。
            // beta 和通义有的网关不回 updated，干等会把整通电话卡在「已连接但没声音」。
            YoRealtimeConfig snapshot = config;
            boolean hold = snapshot != null && snapshot.dialect().holdsAudioUntilConfigured();
            if (!hold && sessionReady.compareAndSet(false, true)) {
                flushBufferedAudio();
            }
        }
        if (event.type() == YoRealtimeEventType.SESSION_UPDATED
                && sessionReady.compareAndSet(false, true)) {
            flushBufferedAudio();
        }
        post(event);
    }

    private void sendSessionUpdate() {
        YoRealtimeConfig snapshot = config;
        if (snapshot == null) {
            return;
        }
        try {
            send(RealtimeProtocol.encodeSessionUpdate(snapshot));
        } catch (JSONException exception) {
            terminate(false, YoAiErrorKind.PROTOCOL, "session.update 编码失败：" + messageOf(exception));
        }
    }

    private void sendAudio(@NonNull byte[] pcm16) {
        try {
            if (!send(RealtimeProtocol.encodeAudioAppend(pcm16))) {
                terminate(false, YoAiErrorKind.NETWORK, "上行音频发送失败");
            }
        } catch (JSONException exception) {
            terminate(false, YoAiErrorKind.PROTOCOL, "input_audio_buffer.append 编码失败：" + messageOf(exception));
        }
    }

    /**
     * @return {@code false} 表示此时还没有 socket（还没连上或已经关掉）。
     */
    private boolean send(@NonNull JSONObject frame) {
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
                // 实时语音里过期音频没有价值：宁可丢最旧的几帧，也不要让内存跟着涨。
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

    /**
     * 唯一的收尾路径：幂等、发终态事件、断 socket、清缓存。
     *
     * @param graceful {@code true} 表示用户挂断（发正常 close 帧，对端能记成正常结束）；
     *                 {@code false} 表示异常（直接 cancel，不等对端回 close）。
     * @param kind 非空时先发一条 {@code ERROR}；用户主动挂断时不发。
     */
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
        if (kind != null) {
            post(YoRealtimeEvent.error(kind, message == null ? "" : message));
        }
        post(YoRealtimeEvent.closed());
        // shutdown（不是 shutdownNow）：已入队的事件仍然会按序跑完，之后这条线程自己退出。
        callbacks.shutdown();
        OkHttpClient owner = client;
        client = null;
        if (owner != null) {
            // 只回收空闲连接，不动 OkHttp 的分发线程：强关会让还在飞的 close 帧发不出去，
            // 服务端于是把这次通话记成异常断开。分发线程本身闲着 60 秒会自己退出。
            owner.connectionPool().evictAll();
        }
    }

    private void post(@NonNull YoRealtimeEvent event) {
        try {
            callbacks.execute(() -> deliver(event));
        } catch (RejectedExecutionException ignored) {
            // 已经收尾：晚到的事件没有接收者了。
        }
    }

    private void deliver(@NonNull YoRealtimeEvent event) {
        RealtimeSink target = listener;
        if (target == null) {
            return;
        }
        try {
            target.onEvent(event);
        } catch (RuntimeException ignored) {
            // 监听器自己抛异常不能拖垮后面的音频：这一条丢了，链路继续。
        }
    }

    // ---------------------------------------------------------------- 失败分类

    @NonNull
    private static OkHttpClient newClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                // 长连接不能用读超时：服务端几十秒没有帧是正常的（用户在思考、没说话），
                // 读超时会把这种正常空闲当成断线，通话就莫名其妙结束了。
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .writeTimeout(WRITE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                .pingInterval(PING_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                // 重连不在这一层。让失败原样冒出来，而不是 OkHttp 偷偷重试一次：
                // 重试会让「握手 401」晚到，上层已经把状态画成「已连接」了。
                .retryOnConnectionFailure(false)
                .build();
    }

    @NonNull
    private static YoAiErrorKind failureKind(@Nullable Response response, @NonNull Throwable throwable) {
        if (response != null) {
            // 握手阶段被拒（401/403/404…）。复用整库唯一的状态码分类规则。
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
        // 状态码要出现在文案里：用户改的是 baseUrl 和密钥，这两件事对应 404 和 401。
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
