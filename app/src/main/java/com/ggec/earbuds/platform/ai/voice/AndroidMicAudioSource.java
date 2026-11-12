package com.ggec.earbuds.platform.ai.voice;

import android.content.Context;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android 系统音频输入源：支持内置麦克风、蓝牙 SCO/BLE 耳机麦、有线耳机与 USB 麦克风。
 *
 * <p>严格单一职责：仅按硬件实际支持的最佳采样率采集 PCM16 单声道音频块并随真实采样率
 * {@code actualRateHz} 推送给上行管线；不感知重采样、协议线速率、静音闸门或电平 UI。</p>
 */
public final class AndroidMicAudioSource implements AiAudioSource {

    public static final String DEFAULT_BUILTIN_ID = "android:mic:default";

    private static final int[] FALLBACK_RATES_HZ = {16_000, 48_000, 8_000};
    private static final int FRAME_MS = 20;
    private static final long STOP_JOIN_MILLIS = 400L;

    private final Context context;
    private final Descriptor descriptor;
    @Nullable
    private final AudioDeviceInfo preferredDevice;
    private final boolean requiresSco;

    private final Object lock = new Object();
    private volatile boolean running;
    @Nullable
    private AudioRecord activeRecord;
    @Nullable
    private Thread readerThread;
    private boolean scoStarted;

    public AndroidMicAudioSource(@NonNull Context context) {
        this(context,
                new Descriptor(DEFAULT_BUILTIN_ID, Kind.BUILTIN_MIC, "Built-in Mic", 16_000),
                null);
    }

    public AndroidMicAudioSource(
            @NonNull Context context,
            @NonNull Descriptor descriptor,
            @Nullable AudioDeviceInfo preferredDevice) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.preferredDevice = preferredDevice;
        this.requiresSco = descriptor.kind() == Kind.BLUETOOTH_SCO;
    }

    @NonNull
    @Override
    public Descriptor descriptor() {
        return descriptor;
    }

    @Override
    public void start(@NonNull Listener listener) {
        Objects.requireNonNull(listener, "listener");
        stop();

        AudioRecord created = null;
        int chosenRate = 0;
        boolean scoActive = false;

        if (requiresSco) {
            scoActive = startBluetoothSco();
        }

        try {
            for (int rate : candidateRates(descriptor.preferredSampleRateHz())) {
                AudioRecord candidate = tryOpenRecord(rate);
                if (candidate != null) {
                    created = candidate;
                    chosenRate = rate;
                    break;
                }
            }
        } catch (SecurityException denied) {
            if (scoActive) {
                stopBluetoothSco();
            }
            listener.onFailure(AiFailureKind.PERMISSION, detailOf(denied));
            return;
        } catch (RuntimeException broken) {
            if (scoActive) {
                stopBluetoothSco();
            }
            listener.onFailure(AiFailureKind.UNKNOWN, detailOf(broken));
            return;
        }

        if (created == null) {
            if (scoActive) {
                stopBluetoothSco();
            }
            listener.onFailure(AiFailureKind.UNKNOWN,
                    "AudioRecord initialization failed for source " + descriptor.id());
            return;
        }

        final AudioRecord recorder = created;
        final int actualRateHz = chosenRate;
        final boolean ownedSco = scoActive;

        synchronized (lock) {
            this.activeRecord = recorder;
            this.scoStarted = ownedSco;
            this.running = true;
            Thread worker = new Thread(
                    () -> readLoop(recorder, actualRateHz, ownedSco, listener),
                    "ai-mic-" + sanitizeThreadName(descriptor.id()));
            worker.setDaemon(true);
            this.readerThread = worker;
            worker.start();
        }
    }

    @Override
    public void stop() {
        AudioRecord toStop;
        Thread toJoin;
        boolean toStopSco;
        synchronized (lock) {
            running = false;
            toStop = activeRecord;
            activeRecord = null;
            toJoin = readerThread;
            readerThread = null;
            toStopSco = scoStarted;
            scoStarted = false;
        }
        if (toStop != null) {
            try {
                if (toStop.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    toStop.stop();
                }
            } catch (RuntimeException ignored) {
            }
        }
        if (toJoin != null && toJoin != Thread.currentThread()) {
            try {
                toJoin.join(STOP_JOIN_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (toStopSco) {
            stopBluetoothSco();
        }
    }

    private void readLoop(
            @NonNull AudioRecord recorder,
            int sampleRateHz,
            boolean ownedSco,
            @NonNull Listener listener) {
        AcousticEchoCanceler aec = null;
        AtomicBoolean failureSent = new AtomicBoolean();
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    aec = AcousticEchoCanceler.create(recorder.getAudioSessionId());
                    if (aec != null) {
                        aec.setEnabled(true);
                    }
                } catch (RuntimeException ignored) {
                }
            }

            recorder.startRecording();
            int frameBytes = Math.max(2, (sampleRateHz * FRAME_MS / 1_000) * 2);
            byte[] buffer = new byte[frameBytes];
            while (running) {
                int read = recorder.read(buffer, 0, buffer.length);
                if (!running) {
                    break;
                }
                if (read > 0) {
                    int evenBytes = read & ~1;
                    if (evenBytes >= 2) {
                        byte[] frame = evenBytes == buffer.length
                                ? buffer.clone()
                                : Arrays.copyOf(buffer, evenBytes);
                        listener.onPcm(frame, sampleRateHz);
                    }
                } else if (read < 0 && running && failureSent.compareAndSet(false, true)) {
                    listener.onFailure(AiFailureKind.UNKNOWN, "AudioRecord read error: " + read);
                    break;
                }
            }
        } catch (SecurityException denied) {
            if (running && failureSent.compareAndSet(false, true)) {
                listener.onFailure(AiFailureKind.PERMISSION, detailOf(denied));
            }
        } catch (RuntimeException broken) {
            if (running && failureSent.compareAndSet(false, true)) {
                listener.onFailure(AiFailureKind.UNKNOWN, detailOf(broken));
            }
        } finally {
            synchronized (lock) {
                if (activeRecord == recorder) {
                    activeRecord = null;
                }
                if (readerThread == Thread.currentThread()) {
                    readerThread = null;
                }
                if (ownedSco && scoStarted) {
                    scoStarted = false;
                } else {
                    ownedSco = false;
                }
            }
            if (aec != null) {
                try {
                    aec.release();
                } catch (RuntimeException ignored) {
                }
            }
            try {
                if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop();
                }
            } catch (RuntimeException ignored) {
            }
            try {
                recorder.release();
            } catch (RuntimeException ignored) {
            }
            if (ownedSco) {
                stopBluetoothSco();
            }
        }
    }

    @Nullable
    private AudioRecord tryOpenRecord(int sampleRateHz) {
        // 显式权限守卫：未授予 RECORD_AUDIO 时不创建 AudioRecord（避免 SecurityException 与 lint MissingPermission）。
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        int minBytes = AudioRecord.getMinBufferSize(
                sampleRateHz, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBytes <= 0) {
            return null;
        }
        int frameBytes = Math.max(2, (sampleRateHz * FRAME_MS / 1_000) * 2);
        int bufferBytes = Math.max(minBytes * 2, frameBytes * 4);
        AudioRecord record = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferBytes);
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            record.release();
            return null;
        }
        if (preferredDevice != null) {
            try {
                record.setPreferredDevice(preferredDevice);
            } catch (RuntimeException ignored) {
            }
        }
        return record;
    }

    private boolean startBluetoothSco() {
        AudioManager manager = audioManager();
        if (manager == null) {
            return false;
        }
        try {
            manager.startBluetoothSco();
            manager.setBluetoothScoOn(true);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void stopBluetoothSco() {
        AudioManager manager = audioManager();
        if (manager == null) {
            return;
        }
        try {
            manager.setBluetoothScoOn(false);
            manager.stopBluetoothSco();
        } catch (RuntimeException ignored) {
        }
    }

    @Nullable
    private AudioManager audioManager() {
        return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    private static int[] candidateRates(int preferredRateHz) {
        int[] raw = new int[1 + FALLBACK_RATES_HZ.length];
        int count = 0;
        if (preferredRateHz > 0) {
            raw[count++] = preferredRateHz;
        }
        for (int candidate : FALLBACK_RATES_HZ) {
            boolean duplicate = false;
            for (int i = 0; i < count; i++) {
                if (raw[i] == candidate) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                raw[count++] = candidate;
            }
        }
        return Arrays.copyOf(raw, count);
    }

    @NonNull
    private static String sanitizeThreadName(@NonNull String id) {
        int colon = id.lastIndexOf(':');
        return colon >= 0 && colon + 1 < id.length() ? id.substring(colon + 1) : id;
    }

    @NonNull
    private static String detailOf(@NonNull Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }
}
