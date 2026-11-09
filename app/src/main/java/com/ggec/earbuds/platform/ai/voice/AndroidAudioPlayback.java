package com.ggec.earbuds.platform.ai.voice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.port.AiAudioPlayback;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * {@link AiAudioPlayback} 的 Android 实现：一条 {@link AudioTrack} 流式写入，加一条排空线程。
 *
 * <p>特性：</p>
 * <ul>
 *   <li>支持运行期若服务端变更下行采样率时自动平滑重建 {@link AudioTrack}；</li>
 *   <li>API 31+ 在关闭外放模式时优先路由至已连接的蓝牙（BLE/SCO）或有线/USB 耳机，
 *       无耳机时退回内置听筒。</li>
 * </ul>
 */
public final class AndroidAudioPlayback implements AiAudioPlayback {

    /** 队列上限 1MB ≈ 16kHz/16bit 单声道 32 秒：超过就丢最旧的，音频不许越播越延迟。 */
    private static final int MAX_QUEUED_BYTES = 1 << 20;

    private static final long STOP_JOIN_MILLIS = 500L;

    private final Context context;
    private final Object lock = new Object();
    private final Deque<Chunk> queue = new ArrayDeque<>();

    /** 排队的一段音频：除了字节，还要记住它属于哪一条回答，截断才能落到正确的 item 上。 */
    private record Chunk(byte[] pcm16, String itemId, int contentIndex) {
    }

    // 以下字段由 lock 保护。
    private AudioTrack track;
    /** 播放进度：只记真正写进 AudioTrack 的字节，用来算打断时的截断毫秒。 */
    private String playedItemId;
    private int playedContentIndex;
    private long playedBytes;
    private int playedSampleRateHz = 24_000;
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
        int rate = sampleRateHz > 0 ? sampleRateHz : 24_000;
        AudioTrack previousTrack = null;
        Thread previousWriter = null;
        synchronized (lock) {
            if (track != null) {
                if (playedSampleRateHz == rate) {
                    return;
                }
                running = false;
                queue.clear();
                queuedBytes = 0;
                previousTrack = track;
                track = null;
                previousWriter = writer;
                writer = null;
                lock.notifyAll();
            }
        }
        if (previousTrack != null) {
            releaseTrack(previousTrack, previousWriter);
        }

        synchronized (lock) {
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
                throw new IllegalStateException("AudioTrack init failed");
            }
            created.play();
            track = created;
            playedSampleRateHz = rate;
            running = true;
            writer = new Thread(this::drainQueue, "ai-voice-playback");
            writer.setDaemon(true);
            writer.start();
        }
    }

    @Override
    public void write(byte[] pcm16, String itemId, int contentIndex) {
        if (pcm16 == null || pcm16.length == 0) {
            return;
        }
        synchronized (lock) {
            if (track == null) {
                return;
            }
            queue.addLast(new Chunk(pcm16.clone(), itemId, contentIndex));
            queuedBytes += pcm16.length;
            while (queuedBytes > MAX_QUEUED_BYTES && !queue.isEmpty()) {
                queuedBytes -= queue.removeFirst().pcm16().length;
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
    public Heard captureHeard() {
        synchronized (lock) {
            if (playedItemId == null || playedBytes <= 0) {
                playedItemId = null;
                playedBytes = 0;
                return null;
            }
            int elapsedMs = (int) Math.min(Integer.MAX_VALUE,
                    playedBytes / 2L * 1000L / Math.max(1, playedSampleRateHz));
            Heard heard = new Heard(playedItemId, playedContentIndex, elapsedMs);
            playedItemId = null;
            playedBytes = 0;
            return heard;
        }
    }

    @Override
    public void setSpeakerphone(boolean speaker) {
        AudioManager manager = manager();
        if (manager == null) {
            return;
        }
        try {
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
        } catch (RuntimeException ignored) {
        }
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
        releaseTrack(stopping, working);

        AudioManager manager = audioManager;
        if (ownsAudioMode && manager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    manager.clearCommunicationDevice();
                } else {
                    manager.setSpeakerphoneOn(false);
                }
                manager.setMode(AudioManager.MODE_NORMAL);
            } catch (RuntimeException ignored) {
            }
            ownsAudioMode = false;
        }
    }

    private static void releaseTrack(@Nullable AudioTrack stopping, @Nullable Thread working) {
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
    }

    /** 排空线程：队列空就等，有数据就阻塞写。写失败说明设备没了，直接退出，不空转。 */
    private void drainQueue() {
        while (true) {
            Chunk chunk;
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
                queuedBytes -= chunk.pcm16().length;
                active = track;
            }
            if (active == null) {
                return;
            }
            int written = active.write(chunk.pcm16(), 0, chunk.pcm16().length);
            if (written < 0) {
                return;
            }
            synchronized (lock) {
                String id = chunk.itemId();
                if (id != null && !id.equals(playedItemId)) {
                    playedItemId = id;
                    playedContentIndex = chunk.contentIndex();
                    playedBytes = 0;
                }
                if (id != null) {
                    playedBytes += written;
                }
            }
        }
    }

    private AudioManager manager() {
        if (audioManager == null) {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }
        return audioManager;
    }

    /** 在可用通信设备里按外放/非外放优先路由查找目标设备；找不到返回 null 退回旧开关。 */
    @Nullable
    private static AudioDeviceInfo communicationDevice(@NonNull AudioManager manager, boolean speaker) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return null;
        }
        List<AudioDeviceInfo> devices = manager.getAvailableCommunicationDevices();
        if (speaker) {
            return findByType(devices, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER);
        }
        AudioDeviceInfo preferred = findByType(devices, AudioDeviceInfo.TYPE_BLE_HEADSET);
        if (preferred != null) {
            return preferred;
        }
        preferred = findByType(devices, AudioDeviceInfo.TYPE_BLUETOOTH_SCO);
        if (preferred != null) {
            return preferred;
        }
        preferred = findByType(devices, AudioDeviceInfo.TYPE_WIRED_HEADSET);
        if (preferred != null) {
            return preferred;
        }
        preferred = findByType(devices, AudioDeviceInfo.TYPE_USB_HEADSET);
        if (preferred != null) {
            return preferred;
        }
        return findByType(devices, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE);
    }

    @Nullable
    private static AudioDeviceInfo findByType(@NonNull List<AudioDeviceInfo> devices, int targetType) {
        for (AudioDeviceInfo device : devices) {
            if (device.getType() == targetType) {
                return device;
            }
        }
        return null;
    }
}
