package com.yovo.biz_ggec_hs.internal.protocol;

import com.yovo.biz_ggec_hs.internal.contract.HsCommandLink;
import com.yovo.biz_ggec_hs.internal.protocol.inbound.DeviceDomainStore;
import com.yovo.biz_ggec_hs.internal.protocol.inbound.DeviceEventPipeline;
import com.yovo.biz_ggec_hs.internal.protocol.inbound.InboundFrameDemux;
import com.yovo.biz_ggec_hs.model.HsBattery;
import com.yovo.biz_ggec_hs.model.HsCommandResult;
import com.yovo.biz_ggec_hs.model.HsDeviceObserver;
import com.yovo.biz_ggec_hs.model.HsResultCallback;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HS 命令运行时。调度、分帧和事件都在这里，链路只通过 {@link HsCommandLink} 进出字节。
 */
public final class HsProtocolEngine {
    private final HsCommandLink link;
    private final ScheduledExecutorService executor;
    private final CommandScheduler scheduler;
    private final DeviceEventPipeline events;
    private final InboundFrameDemux demux;
    private final AtomicInteger sessionSequence = new AtomicInteger();

    private volatile long sessionId;
    private volatile boolean commandReady;
    private volatile boolean released;

    public HsProtocolEngine(HsCommandLink link) {
        if (link == null) {
            throw new IllegalArgumentException("命令链路不能为空");
        }
        this.link = link;
        this.executor = Executors.newSingleThreadScheduledExecutor(new ProtocolThreadFactory());
        this.events = new DeviceEventPipeline();
        CommandScheduler.Dispatcher dispatcher = new ExecutorDispatcher();
        this.scheduler = new CommandScheduler(
                dispatcher,
                new LinkTransport(),
                snapshot -> { },
                new CommandPolicyRegistry());
        this.demux = new InboundFrameDemux(
                events::onEvent,
                scheduler::onResponseCandidate,
                () -> { },
                id -> id > 0L && id == sessionId);
        link.setListener(new LinkListener());
    }

    public void connect(String macAddress) {
        link.open(macAddress);
    }

    public void disconnect() {
        link.close();
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        link.setListener(null);
        link.release();
        executor.execute(() -> {
            scheduler.release();
            sessionId = 0L;
            commandReady = false;
        });
        executor.shutdown();
    }

    public boolean isReady() {
        return commandReady && !released;
    }

    public void submit(Command command, HsResultCallback callback) {
        if (command == null) {
            return;
        }
        if (released) {
            command.abort(CommandCompletion.stringResult(
                    Command.ResultCode.RESERVED, "命令调度器已释放"));
            return;
        }
        if (callback != null) {
            command.addCompletionListener((ignored, completion) ->
                    callback.onCompleted(toResult(completion)));
        }
        executor.execute(() -> scheduler.submit(command, sessionId));
    }

    public void setDeviceObserver(HsDeviceObserver observer) {
        events.setObserver(observer);
    }

    public void setRemoteStreamStop(Runnable trigger) {
        events.setRemoteStreamStop(trigger);
    }

    public HsBattery battery() {
        DeviceDomainStore.BatterySnapshot snapshot = events.batterySnapshot();
        return new HsBattery(
                text(snapshot.getLeftLevel()),
                text(snapshot.getRightLevel()),
                text(snapshot.getCaseLevel()),
                snapshot.isCaseCharging(),
                snapshot.getEarInCaseStatus());
    }

    private static String text(String value) {
        return value == null ? "--" : value;
    }

    private void onLinkReady(boolean ready) {
        if (released) {
            return;
        }
        executor.execute(() -> {
            if (released) {
                return;
            }
            if (ready) {
                sessionId = sessionSequence.incrementAndGet();
                commandReady = true;
                scheduler.setSession(sessionId, true);
            } else {
                commandReady = false;
                scheduler.setSession(0L, false);
                sessionId = 0L;
            }
        });
    }

    private static HsCommandResult toResult(CommandCompletion completion) {
        int value = completion.getShape() == CommandCompletion.Shape.INTEGER
                ? completion.getResultValue() : 0;
        Object data = completion.getShape() == CommandCompletion.Shape.OBJECT
                ? completion.getData() : null;
        return new HsCommandResult(completion.getCode(), value, completion.getMessage(), data);
    }

    private final class LinkListener implements HsCommandLink.Listener {
        @Override
        public void onReady(boolean ready) {
            onLinkReady(ready);
        }

        @Override
        public void onInbound(byte[] value) {
            if (released) {
                return;
            }
            String frame = HsHex.encode(value);
            executor.execute(() -> {
                long current = sessionId;
                if (current > 0L) {
                    demux.onFrame(current, frame);
                }
            });
        }

        @Override
        public void onLinkError(String message) {
            if (released) {
                return;
            }
            String reason = message == null ? "命令链路失败" : message;
            executor.execute(() -> scheduler.abortCurrent(reason));
        }
    }

    private final class LinkTransport implements CommandScheduler.Transport {
        @Override
        public boolean send(long requestSessionId, String payload, Callback callback) {
            if (requestSessionId != sessionId || !commandReady) {
                return false;
            }
            final byte[] bytes;
            try {
                bytes = HsHex.decode(payload);
            } catch (IllegalArgumentException ignored) {
                return false;
            }
            return link.write(bytes, new HsCommandLink.WriteCallback() {
                @Override
                public void onStarted() {
                    callback.onStarted();
                }

                @Override
                public void onCompleted(int status) {
                    callback.onCompleted(status);
                }

                @Override
                public void onTimeout() {
                    callback.onTimeout();
                }
            });
        }
    }

    private final class ExecutorDispatcher implements CommandScheduler.Dispatcher {
        @Override
        public void execute(Runnable runnable) {
            if (runnable != null && !released) {
                executor.execute(runnable);
            }
        }

        @Override
        public CommandScheduler.Cancelable schedule(Runnable runnable, long delayMs) {
            if (runnable == null || released) {
                return () -> { };
            }
            java.util.concurrent.ScheduledFuture<?> future = executor.schedule(
                    runnable, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        }

        @Override
        public long nowMs() {
            return System.nanoTime() / 1_000_000L;
        }
    }

    private static final class ProtocolThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "yo-hs-protocol");
            thread.setDaemon(true);
            return thread;
        }
    }
}
