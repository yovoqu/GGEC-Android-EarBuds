package com.ggec.earbuds.platform.ai.voice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.port.AiAudioPlayback;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * {@link AiAudioPlayback} 的 Android 实现：一条 {@link AudioTrack} 流式写入，加一条排空线程。
 *
 * <p>为什么写要排队而不是直接 {@code AudioTrack.write}：{@code write} 在缓冲区满时会阻塞，
 * 而调用它的是状态机线程（打断、静音、挂断都在那条线程上）。直接写会让"用户打断"排在
 * 几秒钟的音频后面才生效。这里改成入队 + 独立线程阻塞写，{@link #flush()} 只要清空队列，
 * 就已经做到"下一帧不会再播"。</p>
 *
 * <p>为什么 flush 要先 pause 再 flush 再 play：{@code AudioTrack.flush()} 在播放状态下会
 * 重置写指针，部分实现里之后写进去的数据会被丢掉。按"暂停→丢弃→继续"三步走，
 * 保证停完之后还能接着播下一段。</p>
 *
 * <p>为什么扬声器要分两条路：API 31 起路由的正规做法是
 * {@link AudioManager#setCommunicationDevice}，而 {@code setSpeakerphoneOn} 只是兼容开关，
 * 在部分厂商实现上已经不生效。新版先试通信设备，找不到目标设备再退回旧开关——
 * 两条路都走不通时至少不会把通话静音，声音留在默认路由上。</p>
 */
public final class AndroidAudioPlayback implements AiAudioPlayback {

    /** 队列上限 1MB ≈ 16kHz/16bit 单声道 32 秒：超过就丢最旧的，音频不许越播越延迟。 */
    private static final int MAX_QUEUED_BYTES = 1 << 20;

    private static final long STOP_JOIN_MILLIS = 500L;

    private final Context context;
    private final Object lock = new Object();
    private final Deque<byte[]> queue = new ArrayDeque<>();

    // 以下字段由 lock 保护。
    private AudioTrack track;
    private Thread writer;
    private int queuedBytes;
    private boolean running;

    private AudioManager audioManager;
    private boolean ownsAudioMode;

    public AndroidAudioPlayback(@NonNull Context context) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
    }

    @Override
    public void start(int sampleRateHz) {
        synchronized (lock) {
            if (track != null) {
                return;
            }
            int rate = sampleRateHz > 0 ? sampleRateHz : 16_000;
            int minBufferBytes = AudioTrack.getMinBufferSize(
                    rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufferBytes = Math.max(minBufferBytes > 0 ? minBufferBytes : rate, 4096) * 2;
            AudioTrack created = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            if (created.getState() != AudioTrack.STATE_INITIALIZED) {
                created.release();
                throw new IllegalStateException("AudioTrack 没有初始化成功（设备不支持该采样率或输出被占用）");
            }
            created.play();
            track = created;
            running = true;
            writer = new Thread(this::drainQueue, "ai-voice-playback");
            writer.setDaemon(true);
            writer.start();
        }
    }

    @Override
    public void write(byte[] pcm16) {
        if (pcm16 == null || pcm16.length == 0) {
            return;
        }
        synchronized (lock) {
            if (track == null) {
                // 没 start 就写：丢掉。排进一个永远不会被排空的队列只会在 start 之后炸出一段旧声音。
                return;
            }
            queue.addLast(pcm16.clone());
            queuedBytes += pcm16.length;
            while (queuedBytes > MAX_QUEUED_BYTES && !queue.isEmpty()) {
                queuedBytes -= queue.removeFirst().length;
            }
            lock.notifyAll();
        }
    }

    @Override
    public void flush() {
        AudioTrack active;
        synchronized (lock) {
            queue.clear();
            queuedBytes = 0;
            active = track;
        }
        if (active == null || active.getState() != AudioTrack.STATE_INITIALIZED) {
            return;
        }
        active.pause();
        active.flush();
        active.play();
    }

    @Override
    public void setSpeakerphone(boolean speaker) {
        AudioManager manager = manager();
        if (manager == null) {
            return;
        }
        // 通话用模式：不切模式的话，扬声器/听筒的路由请求在多数设备上不会真的生效。
        if (!ownsAudioMode) {
            manager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            ownsAudioMode = true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioDeviceInfo target = communicationDevice(manager, speaker);
            if (target != null) {
                manager.setCommunicationDevice(target);
                return;
            }
        }
        manager.setSpeakerphoneOn(speaker);
    }

    @Override
    public void stop() {
        AudioTrack stopping;
        Thread working;
        synchronized (lock) {
            running = false;
            queue.clear();
            queuedBytes = 0;
            stopping = track;
            track = null;
            working = writer;
            writer = null;
            lock.notifyAll();
        }
        if (working != null && working != Thread.currentThread()) {
            try {
                working.join(STOP_JOIN_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (stopping != null) {
            if (stopping.getState() == AudioTrack.STATE_INITIALIZED) {
                stopping.pause();
                stopping.flush();
            }
            stopping.stop();
            stopping.release();
        }
        AudioManager manager = audioManager;
        if (ownsAudioMode && manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.clearCommunicationDevice();
            } else {
                manager.setSpeakerphoneOn(false);
            }
            manager.setMode(AudioManager.MODE_NORMAL);
            ownsAudioMode = false;
        }
    }

    /** 排空线程：队列空就等，有数据就阻塞写。写失败说明设备没了，直接退出，不空转。 */
    private void drainQueue() {
        while (true) {
            byte[] chunk;
            AudioTrack active;
            synchronized (lock) {
                while (running && queue.isEmpty()) {
                    try {
                        lock.wait();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (!running) {
                    return;
                }
                chunk = queue.removeFirst();
                queuedBytes -= chunk.length;
                active = track;
            }
            if (active == null) {
                return;
            }
            int written = active.write(chunk, 0, chunk.length);
            if (written < 0) {
                return;
            }
        }
    }

    private AudioManager manager() {
        if (audioManager == null) {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }
        return audioManager;
    }

    /** 在可用通信设备里找目标那一类；找不到返回 null，让调用方退回旧开关。 */
    private static AudioDeviceInfo communicationDevice(AudioManager manager, boolean speaker) {
        List<AudioDeviceInfo> devices = manager.getAvailableCommunicationDevices();
        int wanted = speaker ? AudioDeviceInfo.TYPE_BUILTIN_SPEAKER : AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
        for (AudioDeviceInfo device : devices) {
            if (device.getType() == wanted) {
                return device;
            }
        }
        return null;
    }
}
