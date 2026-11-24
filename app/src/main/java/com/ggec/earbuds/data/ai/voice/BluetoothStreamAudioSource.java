package com.ggec.earbuds.data.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.domain.device.record.RecordFailure;

import java.util.Objects;

/**
 * 蓝牙设备随录随传音频输入源：将 {@link RecordPort#startStreamAudio} 的实时 PCM16 音频流
 * 桥接为 AI 通话的 {@link AiAudioSource}。
 *
 * <p>显式状态机（{@link State}）：
 * <ul>
 *   <li>{@code IDLE} → {@code STARTING} → {@code STREAMING} → {@code STOPPING} → {@code IDLE}；</li>
 *   <li>若在 {@code STOPPING} 期间再次收到 {@code start}（快速来回切换输入源），记录
 *       {@code pendingStartListener}，待底层 {@code stopStreamRecord} 收口后自动重启；</li>
 *   <li>{@code stop()} 幂等：离开 {@code STARTING/STREAMING} 时立即清空 {@code listener}，
 *       确保停止后不再向旧上行管线投递任何帧。</li>
 * </ul>
 * </p>
 */
public final class BluetoothStreamAudioSource implements AiAudioSource {

    public static final String SOURCE_ID = AiAudioSource.BLUETOOTH_STREAM_ID;
    public static final int STREAM_SAMPLE_RATE_HZ = 16_000;

    private enum State {
        IDLE,
        STARTING,
        STREAMING,
        STOPPING
    }

    private final RecordPort recordPort;
    private final Descriptor descriptor;
    private final Object lock = new Object();

    private State state = State.IDLE;
    @Nullable
    private Listener listener;
    @Nullable
    private Listener pendingStartListener;

    private final RecordPort.StreamAudioObserver streamObserver = new RecordPort.StreamAudioObserver() {
        @Override
        public void onStarted() {
            synchronized (lock) {
                if (state == State.STARTING) {
                    state = State.STREAMING;
                }
            }
        }

        @Override
        public void onPcm(@NonNull byte[] pcm16, int sampleRateHz) {
            if (pcm16.length < 2 || sampleRateHz <= 0) {
                return;
            }
            Listener target;
            synchronized (lock) {
                if (state == State.STARTING) {
                    state = State.STREAMING;
                }
                if (state != State.STREAMING || listener == null) {
                    return;
                }
                target = listener;
            }
            target.onPcm(pcm16, sampleRateHz);
        }

        @Override
        public void onError(@NonNull RecordFailure failure) {
            Listener target = null;
            synchronized (lock) {
                pendingStartListener = null;
                if (state == State.STARTING || state == State.STREAMING) {
                    target = listener;
                }
                listener = null;
                state = State.IDLE;
            }
            if (target != null && failure != RecordFailure.NOT_CONNECTED) {
                target.onFailure(mapFailure(failure), failure.name());
            }
        }

        @Override
        public void onStopped() {
            finishStopAndMaybeRestart();
        }
    };

    public BluetoothStreamAudioSource(@NonNull RecordPort recordPort, @NonNull String label) {
        this.recordPort = Objects.requireNonNull(recordPort, "recordPort");
        String safeLabel = Objects.requireNonNull(label, "label").isBlank()
                ? "Bluetooth Device"
                : label.strip();
        this.descriptor = new Descriptor(SOURCE_ID, Kind.BLUETOOTH_BLE, safeLabel, STREAM_SAMPLE_RATE_HZ);
    }

    @NonNull
    @Override
    public Descriptor descriptor() {
        return descriptor;
    }

    @Override
    public void start(@NonNull Listener target) {
        Objects.requireNonNull(target, "target");
        boolean shouldStart = false;
        synchronized (lock) {
            switch (state) {
                case IDLE -> {
                    state = State.STARTING;
                    listener = target;
                    pendingStartListener = null;
                    shouldStart = true;
                }
                case STARTING, STREAMING -> {
                    listener = target;
                    pendingStartListener = null;
                }
                case STOPPING -> pendingStartListener = target;
            }
        }
        if (shouldStart) {
            recordPort.startStreamAudio(streamObserver);
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            listener = null;
            pendingStartListener = null;
        }
    }

    @Override
    public void release() {
        boolean shouldStop = false;
        synchronized (lock) {
            listener = null;
            pendingStartListener = null;
            if (state == State.STARTING || state == State.STREAMING) {
                state = State.STOPPING;
                shouldStop = true;
            }
        }
        if (shouldStop) {
            recordPort.stopStreamRecord(outcome -> finishStopAndMaybeRestart());
        }
    }

    private void finishStopAndMaybeRestart() {
        Listener restartTarget = null;
        synchronized (lock) {
            if (state == State.IDLE) {
                return;
            }
            if (pendingStartListener != null) {
                restartTarget = pendingStartListener;
                pendingStartListener = null;
                listener = restartTarget;
                state = State.STARTING;
            } else {
                listener = null;
                state = State.IDLE;
            }
        }
        if (restartTarget != null) {
            recordPort.startStreamAudio(streamObserver);
        }
    }

    @NonNull
    private static AiFailureKind mapFailure(@NonNull RecordFailure failure) {
        return switch (failure) {
            case TIMEOUT -> AiFailureKind.TIMEOUT;
            case NOT_CONNECTED, DEVICE_ERROR, REJECTED, UNSUPPORTED, BUSY, IO -> AiFailureKind.UNKNOWN;
        };
    }
}
