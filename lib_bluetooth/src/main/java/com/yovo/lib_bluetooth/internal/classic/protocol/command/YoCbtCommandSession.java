package com.yovo.lib_bluetooth.internal.classic.protocol.command;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.concurrency.YoTaskScheduler;
import com.yovo.lib_bluetooth.internal.common.result.YoResult;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode;
import com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure;
import com.yovo.lib_bluetooth.internal.classic.protocol.codec.YoCbtFramedPacketCodec;
import com.yovo.lib_bluetooth.internal.classic.protocol.codec.YoCbtPacketCodec;
import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacket;
import com.yovo.lib_bluetooth.internal.classic.protocol.retry.YoCbtRetryPolicy;
import com.yovo.lib_bluetooth.internal.classic.protocol.timeout.YoCbtCommandTimeout;
import com.yovo.lib_bluetooth.internal.classic.spp.connection.YoCbtSppConnectionId;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 按 SPP 连接管理的命令会话，负责提交、响应匹配、超时与重试。
 */
public final class YoCbtCommandSession {

    public interface Sender {
        @NonNull
        YoResult<Void> send(@NonNull byte[] bytes);
    }

    private final YoCbtSppConnectionId connectionId;
    private final YoCbtPacketCodec codec;
    private final YoTaskScheduler scheduler;
    private final Sender sender;
    private final Consumer<Runnable> callbacks;
    private final YoCbtCommandQueue queue;
    private final Map<String, Integer> attempts = new HashMap<>();
    private final Map<String, YoCbtCommandTimeout> timeouts = new HashMap<>();
    private final CopyOnWriteArrayList<YoCbtCommandObserver> observers;

    public YoCbtCommandSession(@NonNull YoCbtSppConnectionId connectionId,
                               @NonNull YoCbtPacketCodec codec,
                               @NonNull YoTaskScheduler scheduler,
                               @NonNull Sender sender,
                               @NonNull Consumer<Runnable> callbacks) {
        this.connectionId = connectionId;
        this.codec = codec;
        this.scheduler = scheduler;
        this.sender = sender;
        this.callbacks = callbacks;
        this.queue = new YoCbtCommandQueue(64);
        this.observers = new CopyOnWriteArrayList<>();
    }

    @NonNull
    public YoResult<Void> submit(@NonNull YoCbtCommandRequest request) {
        if (queue.isFull()) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_COMMAND_QUEUE_FULL,
                    "command queue full for " + connectionId, null));
        }

        byte[] bytes;
        try {
            bytes = codec.encode(request.getCommand().getPacket());
        } catch (Throwable e) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_PACKET_ENCODE_FAILED,
                    "command encode failed", e));
        }

        YoResult<Void> sent = sender.send(bytes);
        if (sent.isFailure()) {
            return YoResult.failure(sent.getFailure());
        }

        String commandId = request.getCommandId();
        if (!queue.enqueue(request, () -> handleTimeout(commandId))) {
            return YoResult.failure(failure(YoCbtErrorCode.YO_CBT_COMMAND_QUEUE_FULL,
                    "command queue full for " + connectionId, null));
        }
        attempts.put(commandId, 1);
        scheduleTimeout(request);

        notifyResult(new YoCbtCommandResult(commandId, YoCbtCommandStatus.QUEUED, null, ""));
        return YoResult.success(null);
    }

    public void onBytesReceived(@NonNull byte[] bytes) {
        if (codec instanceof YoCbtFramedPacketCodec) {
            ((YoCbtFramedPacketCodec) codec).feedAndDecode(bytes, this::dispatchPacket);
            return;
        }
        try {
            dispatchPacket(codec.decode(bytes));
        } catch (Throwable e) {
            if (!queue.isEmpty()) {
                notifyResult(new YoCbtCommandResult("", YoCbtCommandStatus.FAILED, null,
                        "packet decode failed: " + e.getMessage()));
            }
        }
    }

    public void observe(@NonNull YoCbtCommandObserver observer) {
        observers.addIfAbsent(observer);
    }

    public void removeObserver(@NonNull YoCbtCommandObserver observer) {
        observers.remove(observer);
    }

    public void clear() {
        cancelAllTimeouts();
        queue.clear();
        attempts.clear();
        observers.clear();
        if (codec instanceof YoCbtFramedPacketCodec) {
            ((YoCbtFramedPacketCodec) codec).resetFramer();
        }
    }

    private void dispatchPacket(@NonNull YoCbtPacket packet) {
        YoCbtCommandRequest matched = queue.matchAndRemove(packet);
        if (matched == null) {
            if (!queue.isEmpty()) {
                notifyResult(new YoCbtCommandResult("", YoCbtCommandStatus.FAILED, null,
                        YoCbtErrorCode.YO_CBT_RESPONSE_NOT_MATCHED.name()));
            }
            return;
        }

        cancelTimeout(matched.getCommandId());
        attempts.remove(matched.getCommandId());

        YoCbtCommandResponse response = new YoCbtCommandResponse(
                matched.getCommandId(), packet, System.currentTimeMillis());
        notifyResult(new YoCbtCommandResult(matched.getCommandId(),
                YoCbtCommandStatus.RESPONSE_RECEIVED, response, ""));
    }

    private void scheduleTimeout(@NonNull YoCbtCommandRequest request) {
        String commandId = request.getCommandId();
        YoCbtCommandTimeout timeout = new YoCbtCommandTimeout(commandId, request.getTimeoutMillis());
        timeout.setHandle(scheduler.schedule(() -> handleTimeout(commandId), request.getTimeoutMillis()));
        timeouts.put(commandId, timeout);
    }

    private void handleTimeout(@NonNull String commandId) {
        YoCbtCommandRequest request = queue.remove(commandId);
        if (request == null) {
            cancelTimeout(commandId);
            return;
        }

        int attempt = attempts.getOrDefault(commandId, 1);
        YoCbtRetryPolicy policy = request.getRetryPolicy();
        if (attempt < policy.getMaxAttempts()) {
            attempts.put(commandId, attempt + 1);
            retrySend(request, attempt + 1);
            return;
        }

        cancelTimeout(commandId);
        attempts.remove(commandId);
        notifyResult(new YoCbtCommandResult(commandId, YoCbtCommandStatus.TIMEOUT, null,
                "command timeout after " + attempt + " attempts"));
    }

    private void retrySend(@NonNull YoCbtCommandRequest request, int attempt) {
        String commandId = request.getCommandId();
        try {
            byte[] bytes = codec.encode(request.getCommand().getPacket());
            sender.send(bytes);
            if (!queue.enqueue(request, () -> handleTimeout(commandId))) {
                attempts.remove(commandId);
                notifyResult(new YoCbtCommandResult(commandId, YoCbtCommandStatus.FAILED, null,
                        "command queue full during retry"));
                return;
            }
            scheduleTimeout(request);
        } catch (Throwable e) {
            attempts.remove(commandId);
            notifyResult(new YoCbtCommandResult(commandId, YoCbtCommandStatus.FAILED, null,
                    "retry encode failed: " + e.getMessage()));
        }
    }

    private void cancelTimeout(@NonNull String commandId) {
        YoCbtCommandTimeout timeout = timeouts.remove(commandId);
        if (timeout != null) {
            timeout.cancel();
        }
    }

    private void cancelAllTimeouts() {
        for (YoCbtCommandTimeout timeout : timeouts.values()) {
            timeout.cancel();
        }
        timeouts.clear();
    }

    private void notifyResult(@NonNull YoCbtCommandResult result) {
        callbacks.accept(() -> {
            for (YoCbtCommandObserver observer : observers) {
                observer.onCommandResult(connectionId, result);
            }
        });
    }

    private YoCbtFailure failure(@NonNull YoCbtErrorCode code, @NonNull String message,
                                   @NonNull Throwable cause) {
        return new YoCbtFailure(code, message, cause, true, false, "protocol",
                null, null, connectionId, null, "");
    }
}
