package com.ggec.earbuds.platform.ai.voice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.os.Build;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiAudioCapture;

import java.util.Arrays;
import java.util.Objects;

/**
 * {@link AiAudioCapture} 的 Android 实现：一条 {@link AudioRecord} 线程，按固定帧长回调 PCM16 与电平。
 *
 * <p>为什么电平要在这里算、而且必须做对数压缩：语音的能量是抖的，线性 RMS 在安静房间里只有
 * 千分之几，光晕看上去像卡住了；说话时又直接冲到 1，一帧一个样子。换成 dB 之后，
 * "安静但有人"落在 0.05 上下，"正常说话"落在 0.5~0.8，动画才有中间地带可用。
 * 所以电平走 {@code [-55dBFS, -5dBFS] → [0,1]} 的映射，并且只要这一帧还有非零采样就垫一个
 * {@link #MIN_VISIBLE_LEVEL} 的地板——完全数字静音（麦克风被硬件静音）才允许是 0。</p>
 *
 * <p>为什么静音只停上行、不停读数：端口要求静音期间仍然有电平（用户要看到"我在说话，但没发出去"）。
 * 读数照旧，只是回调里的 {@code pcm16} 换成空数组，上层不会把静音期的声音送进实时链路。</p>
 *
 * <p>失败一律通过 {@link Listener#onFailure} 上报，不抛异常：没有录音权限是产品里最常见的一种状态
 * （用户点了"不允许"），把它做成异常只会让调用方到处写 try。</p>
 */
public final class AndroidAudioCapture implements AiAudioCapture {

    /** 16bit：与 {@code ENCODING_PCM_16BIT} 对齐，也是实时链路的要求。 */
    private static final int BYTES_PER_SAMPLE = 2;

    /** 20ms 一帧：既够短（打断、静音判定的延迟是帧级），又够长（读次数不至于把 CPU 叫醒几百次每秒）。 */
    private static final int FRAME_MILLIS = 20;

    /** 电平映射的电平下限/上限，单位 dBFS。安静房间的底噪约 -55~-45，说话平均 -25~-12。 */
    private static final double FLOOR_DB = -55d;
    private static final double CEIL_DB = -5d;

    /** 只要还有非零采样，电平至少是这个值：光晕在极静的房间里要保持"活着"的一点点呼吸。 */
    private static final float MIN_VISIBLE_LEVEL = 0.02f;

    /** 满量程：16bit 有符号采样的绝对值上界。 */
    private static final double FULL_SCALE = 32767d;

    private static final long STOP_JOIN_MILLIS = 500L;
    private static final byte[] NO_AUDIO = new byte[0];

    private final Context context;
    private final Object lock = new Object();

    // 以下字段由 lock 保护。
    private AudioRecord record;
    private Thread thread;
    private Listener listener;
    private int frameBytes;
    private boolean running;

    /**
     * 上行闸门。关掉时仍然读数、仍然算电平，只是回调里的 PCM 换成空数组。
     *
     * <p>默认开。半双工通话里由状态机在助手发声期间关掉：这不是用户静音，
     * 而是防止扬声器的声音被麦克风收回去再上行（实机会表现为助手自问自答）。</p>
     */
    private volatile boolean uplinkEnabled = true;

    /** 回声消除。仅在 {@code VOICE_COMMUNICATION} 音源上可能生效；失败就只靠软件闸门。 */
    private AcousticEchoCanceler echoCanceler;

    public AndroidAudioCapture(@NonNull Context context) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
    }

    @Override
    public void start(int sampleRateHz, @NonNull Listener target) {
        Objects.requireNonNull(target, "listener");
        if (sampleRateHz <= 0) {
            target.onFailure(AiFailureKind.BAD_REQUEST, Integer.toString(sampleRateHz));
            return;
        }
        synchronized (lock) {
            if (record != null) {
                // 已经在采集：重复 start 不叠第二条读取线程（两条线程抢同一个 AudioRecord 会互相截断）。
                return;
            }
            if (!canRecord()) {
                target.onFailure(AiFailureKind.PERMISSION, "");
                return;
            }
            int minBufferBytes = AudioRecord.getMinBufferSize(
                    sampleRateHz, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBufferBytes <= 0) {
                target.onFailure(AiFailureKind.UNSUPPORTED_CAPABILITY, Integer.toString(sampleRateHz));
                return;
            }
            int frame = Math.max(BYTES_PER_SAMPLE,
                    sampleRateHz / 1000 * FRAME_MILLIS * BYTES_PER_SAMPLE);
            AudioRecord created = open(sampleRateHz, Math.max(minBufferBytes, frame * 4));
            if (created == null) {
                target.onFailure(AiFailureKind.UNSUPPORTED_CAPABILITY, "");
                return;
            }
            try {
                created.startRecording();
            } catch (RuntimeException unavailable) {
                created.release();
                target.onFailure(AiFailureKind.UNKNOWN, message(unavailable));
                return;
            }
            if (created.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                created.release();
                target.onFailure(AiFailureKind.UNKNOWN, "");
                return;
            }
            record = created;
            frameBytes = frame;
            listener = target;
            running = true;
            thread = new Thread(this::readLoop, "ai-voice-capture");
            thread.setDaemon(true);
            thread.start();
        }
    }

    @Override
    public void stop() {
        AudioRecord stopping;
        Thread working;
        synchronized (lock) {
            running = false;
            listener = null;
            stopping = record;
            record = null;
            working = thread;
            thread = null;
        }
        // 先 stop() 把阻塞在 read() 上的线程放出来，再等它退出，最后 release()：反过来 release 会让读线程踩到已释放对象。
        if (stopping != null && stopping.getState() == AudioRecord.STATE_INITIALIZED) {
            if (stopping.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                stopping.stop();
            }
        }
        if (working != null && working != Thread.currentThread()) {
            try {
                working.join(STOP_JOIN_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (stopping != null) {
            stopping.release();
        }
        releaseEchoCanceler();
    }

    @Override
    public void setUplinkEnabled(boolean enabled) {
        uplinkEnabled = enabled;
    }

    /**
     * 打开麦克风。
     *
     * <p>音源顺序：{@code VOICE_COMMUNICATION} → {@code VOICE_RECOGNITION} → {@code MIC}。
     * 把 {@code VOICE_COMMUNICATION} 放第一位是有原因的：只有它允许挂 {@link AcousticEchoCanceler}，
     * 而回声消除是"助手说话时还能开着麦克风"的唯一硬件前提。前面几轮实测里用
     * {@code VOICE_RECOGNITION}，它的降噪会把外放声音当成噪声保留下来，服务端 VAD 于是判成用户开口，
     * 助手自问自答。</p>
     *
     * <p>回声消除挂不上（设备不支持或已被别的进程占用）时不算失败：返回的 {@code record}
     * 照样能用，调用方靠软件上行闸门兜底。</p>
     */
    private AudioRecord open(int sampleRateHz, int bufferBytes) {
        int[] sources = {
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
        };
        for (int source : sources) {
            AudioRecord candidate;
            try {
                candidate = new AudioRecord(source, sampleRateHz, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, bufferBytes);
            } catch (RuntimeException unsupported) {
                continue;
            }
            if (candidate.getState() == AudioRecord.STATE_INITIALIZED) {
                attachEchoCanceler(candidate);
                return candidate;
            }
            candidate.release();
        }
        return null;
    }

    /** 尽力打开回声消除；失败保持为 null，不影响通话（上层闸门仍在）。 */
    private void attachEchoCanceler(@NonNull AudioRecord target) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN
                || !AcousticEchoCanceler.isAvailable()) {
            return;
        }
        try {
            AcousticEchoCanceler created = AcousticEchoCanceler.create(target.getAudioSessionId());
            if (created != null && created.getEnabled()) {
                echoCanceler = created;
            } else if (created != null) {
                created.release();
            }
        } catch (RuntimeException unavailable) {
            // 被别的录音占用或厂商实现缺失：退回软件闸门。
        }
    }

    private void releaseEchoCanceler() {
        AcousticEchoCanceler current = echoCanceler;
        echoCanceler = null;
        if (current != null) {
            try {
                current.setEnabled(false);
            } catch (RuntimeException ignored) {
                // 释放路径不抛。
            }
            current.release();
        }
    }

    @Override
    public boolean echoCancellationActive() {
        return echoCanceler != null;
    }

    private void readLoop() {
        byte[] frame = new byte[frameBytes];
        try {
            while (isRunning()) {
                AudioRecord active;
                Listener target;
                synchronized (lock) {
                    active = record;
                    target = listener;
                }
                if (active == null || target == null) {
                    return;
                }
                int read;
                try {
                    read = active.read(frame, 0, frame.length);
                } catch (RuntimeException broken) {
                    report(AiFailureKind.UNKNOWN, message(broken));
                    return;
                }
                if (read < 0) {
                    report(AiFailureKind.UNKNOWN, Integer.toString(read));
                    return;
                }
                if (read == 0) {
                    continue;
                }
                float level = levelOf(frame, read);
                byte[] payload = uplinkEnabled ? Arrays.copyOf(frame, read) : NO_AUDIO;
                target.onFrame(payload, level);
            }
        } finally {
            releaseRecord();
        }
    }

    private boolean isRunning() {
        synchronized (lock) {
            return running;
        }
    }

    /** 读线程自己退出时也要释放设备，否则下一次 start 会拿到"设备忙"。 */
    private void releaseRecord() {
        AudioRecord leftover;
        synchronized (lock) {
            leftover = record;
            record = null;
            running = false;
            listener = null;
        }
        if (leftover == null) {
            return;
        }
        if (leftover.getState() == AudioRecord.STATE_INITIALIZED
                && leftover.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            leftover.stop();
        }
        leftover.release();
        releaseEchoCanceler();
    }

    private void report(AiFailureKind kind, String detail) {
        Listener target;
        synchronized (lock) {
            running = false;
            target = listener;
            listener = null;
        }
        if (target != null) {
            target.onFailure(kind, detail);
        }
    }

    private boolean canRecord() {
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 一帧的 RMS 折算成 0..1。对数压缩的理由见类注释；完全静音返回 0，让页面能区分
     * "麦克风被硬件静音/没在采"和"房间很安静"。
     */
    static float levelOf(byte[] pcm16, int length) {
        int samples = length / BYTES_PER_SAMPLE;
        if (samples == 0) {
            return 0f;
        }
        double sum = 0d;
        boolean nonZero = false;
        for (int i = 0; i < samples; i++) {
            int low = pcm16[i * 2] & 0xff;
            int high = pcm16[i * 2 + 1];
            int sample = (high << 8) | low;
            if (sample != 0) {
                nonZero = true;
            }
            sum += (double) sample * sample;
        }
        if (!nonZero) {
            return 0f;
        }
        double rms = Math.sqrt(sum / samples);
        double db = 20d * Math.log10(Math.max(rms, 1d) / FULL_SCALE);
        float level = (float) ((db - FLOOR_DB) / (CEIL_DB - FLOOR_DB));
        return Math.max(MIN_VISIBLE_LEVEL, Math.min(1f, level));
    }

    private static String message(Throwable error) {
        String detail = error.getMessage();
        return detail == null || detail.isBlank() ? error.getClass().getSimpleName() : detail;
    }
}



