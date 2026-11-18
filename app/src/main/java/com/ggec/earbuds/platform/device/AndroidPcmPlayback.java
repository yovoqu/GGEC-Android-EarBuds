package com.ggec.earbuds.platform.device;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.PlaybackOutput;
import com.ggec.earbuds.domain.device.port.PcmPlaybackPort;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 16kHz / 16-bit / Mono PCM 音频播放器 Android 平台实现。
 *
 * <p>在原始播放能力之上补齐两件事：</p>
 * <ul>
 *   <li><b>进度</b>：用 {@link AudioTrack#getPlaybackHeadPosition()} 上报真实出声位置，总时长由文件字节数换算；</li>
 *   <li><b>播放源</b>：枚举系统输出设备（耳机 / 手机扬声器 / 有线 / USB…），
 *       用 {@link AudioTrack#setPreferredDevice(AudioDeviceInfo)} 把播放路由到用户选定的物理播放器。</li>
 * </ul>
 */
public final class AndroidPcmPlayback implements PcmPlaybackPort {

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BYTES_PER_FRAME = 2; // 16-bit mono

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AudioManager audioManager;
    private AudioTrack audioTrack;
    private Thread playThread;
    private volatile boolean isPlaying = false;
    private volatile String currentPlayingPath = null;
    private volatile long currentDurationMs = 0L;

    /** 用户最近一次选择的输出设备 id：新建音轨时优先路由，跨曲目保持选择。 */
    @Nullable
    private String preferredOutputId;

    public AndroidPcmPlayback(@NonNull Context context) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
    }

    @Override
    public synchronized void play(@NonNull String filePath, @NonNull Runnable onCompletion) {
        stop();

        File file = new File(filePath);
        if (!file.exists() || !file.canRead() || file.length() == 0) {
            mainHandler.post(onCompletion);
            return;
        }

        int minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        int bufferSize = Math.max(minBufferSize, 4096);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_CONFIG)
                .setEncoding(AUDIO_FORMAT)
                .build();

        AudioTrack track = new AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );

        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            mainHandler.post(onCompletion);
            return;
        }

        // 用户选过播放源：新音轨创建后立刻把路由钉到该物理设备。
        AudioDeviceInfo preferred = findOutputDevice(preferredOutputId);
        if (preferred != null) {
            track.setPreferredDevice(preferred);
        }

        audioTrack = track;
        currentDurationMs = file.length() / BYTES_PER_FRAME * 1000L / SAMPLE_RATE;
        isPlaying = true;
        currentPlayingPath = filePath;
        audioTrack.play();

        playThread = new Thread(() -> {
            long framesWritten = 0L;
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[bufferSize];
                int bytesRead;
                while (isPlaying && (bytesRead = fis.read(buffer)) != -1) {
                    int written = audioTrack.write(buffer, 0, bytesRead);
                    if (written > 0) {
                        framesWritten += written / BYTES_PER_FRAME;
                    }
                }
                // 等待 AudioTrack 实际把缓冲区的 PCM 帧播放完，避免末尾音频被提前释放丢弃。
                if (framesWritten > 0L) {
                    java.util.concurrent.CountDownLatch drained =
                            new java.util.concurrent.CountDownLatch(1);
                    audioTrack.setPlaybackPositionUpdateListener(
                            new AudioTrack.OnPlaybackPositionUpdateListener() {
                                @Override
                                public void onMarkerReached(AudioTrack track) {
                                    drained.countDown();
                                }

                                @Override
                                public void onPeriodicNotification(AudioTrack track) {
                                }
                            }, mainHandler);
                    audioTrack.setNotificationMarkerPosition(
                            (int) Math.min(framesWritten, Integer.MAX_VALUE));
                    try {
                        drained.await(3L, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (IOException readFailure) {
                android.util.Log.w("PcmPlayback", "pcm read failed", readFailure);
            } finally {
                synchronized (AndroidPcmPlayback.this) {
                    isPlaying = false;
                    currentPlayingPath = null;
                    currentDurationMs = 0L;
                    if (audioTrack != null) {
                        try {
                            audioTrack.stop();
                            audioTrack.release();
                        } catch (RuntimeException releaseFailure) {
                            android.util.Log.w("PcmPlayback", "release after playback failed", releaseFailure);
                        }
                        audioTrack = null;
                    }
                }
                mainHandler.post(onCompletion);
            }
        }, "PcmPlaybackThread");
        playThread.start();
    }

    @Override
    public synchronized void stop() {
        isPlaying = false;
        currentPlayingPath = null;
        currentDurationMs = 0L;
        if (playThread != null) {
            playThread.interrupt();
            playThread = null;
        }
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (RuntimeException releaseFailure) {
                android.util.Log.w("PcmPlayback", "stop release failed", releaseFailure);
            }
            audioTrack = null;
        }
    }

    @Override
    public boolean isPlaying() {
        return isPlaying;
    }

    @Nullable
    @Override
    public String playingFilePath() {
        return currentPlayingPath;
    }

    @Override
    public synchronized long positionMs() {
        AudioTrack track = audioTrack;
        if (track == null) {
            return 0L;
        }
        long frames = track.getPlaybackHeadPosition();
        if (frames <= 0L) {
            return 0L;
        }
        return frames * 1000L / SAMPLE_RATE;
    }

    @Override
    public long durationMs() {
        return currentDurationMs;
    }

    @NonNull
    @Override
    public List<PlaybackOutput> availableOutputs() {
        AudioManager manager = manager();
        if (manager == null) {
            return List.of();
        }
        AudioDeviceInfo[] devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        List<PlaybackOutput> outputs = new ArrayList<>(devices.length);
        for (AudioDeviceInfo device : devices) {
            if (!device.isSink()) {
                continue;
            }
            PlaybackOutput output = toOutput(device);
            if (output != null && !containsEquivalent(outputs, output)) {
                outputs.add(output);
            }
        }
        return outputs;
    }

    @Nullable
    @Override
    public synchronized PlaybackOutput currentOutput() {
        // 用户显式选过播放源：以该选择为准（切换即时生效，不必等系统异步回报路由）。
        if (preferredOutputId != null) {
            AudioDeviceInfo preferred = findOutputDevice(preferredOutputId);
            if (preferred != null) {
                PlaybackOutput output = toOutput(preferred);
                if (output != null) {
                    return output;
                }
            }
        }
        AudioTrack track = audioTrack;
        if (track == null) {
            return null;
        }
        AudioDeviceInfo routed = track.getRoutedDevice();
        if (routed == null) {
            return null;
        }
        return toOutput(routed);
    }

    @Override
    public synchronized boolean selectOutput(@NonNull String outputId) {
        preferredOutputId = outputId;
        AudioTrack track = audioTrack;
        if (track == null) {
            // 没有活动音轨：记住选择，下一首播放时生效。
            return findOutputDevice(outputId) != null;
        }
        AudioDeviceInfo device = findOutputDevice(outputId);
        if (device == null) {
            return false;
        }
        return track.setPreferredDevice(device);
    }

    @Override
    public void release() {
        stop();
    }

    private AudioManager manager() {
        if (audioManager == null) {
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }
        return audioManager;
    }

    @Nullable
    private AudioDeviceInfo findOutputDevice(@Nullable String outputId) {
        if (outputId == null) {
            return null;
        }
        AudioManager manager = manager();
        if (manager == null) {
            return null;
        }
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.isSink() && outputId.equals(String.valueOf(device.getId()))) {
                return device;
            }
        }
        return null;
    }

    @Nullable
    private static PlaybackOutput toOutput(@NonNull AudioDeviceInfo device) {
        PlaybackOutput.Kind kind = kindOf(device.getType());
        if (kind == null) {
            return null;
        }
        CharSequence product = device.getProductName();
        String label = product == null ? "" : product.toString();
        return new PlaybackOutput(String.valueOf(device.getId()), kind, label);
    }

    @Nullable
    private static PlaybackOutput.Kind kindOf(int type) {
        return switch (type) {
            case AudioDeviceInfo.TYPE_BLE_HEADSET,
                 AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                 AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> PlaybackOutput.Kind.EARBUDS;
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> PlaybackOutput.Kind.PHONE_SPEAKER;
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> PlaybackOutput.Kind.EARPIECE;
            case AudioDeviceInfo.TYPE_WIRED_HEADSET,
                 AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> PlaybackOutput.Kind.WIRED;
            case AudioDeviceInfo.TYPE_USB_HEADSET,
                 AudioDeviceInfo.TYPE_USB_DEVICE,
                 AudioDeviceInfo.TYPE_USB_ACCESSORY -> PlaybackOutput.Kind.USB;
            default -> null;
        };
    }

    private static boolean containsEquivalent(@NonNull List<PlaybackOutput> outputs,
                                              @NonNull PlaybackOutput candidate) {
        for (PlaybackOutput output : outputs) {
            if (output.kind() == candidate.kind() && output.label().equals(candidate.label())) {
                return true;
            }
        }
        return false;
    }
}
