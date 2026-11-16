package com.ggec.earbuds.platform.device;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.port.PcmPlaybackPort;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * 16kHz / 16-bit / Mono PCM 音频播放器 Android 平台实现。
 */
public final class AndroidPcmPlayback implements PcmPlaybackPort {

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AudioTrack audioTrack;
    private Thread playThread;
    private volatile boolean isPlaying = false;
    private volatile String currentPlayingPath = null;

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

        audioTrack = new AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );

        if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            mainHandler.post(onCompletion);
            return;
        }

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
                        framesWritten += written / 2L; // 16-bit mono: 2 bytes per sample/frame
                    }
                }
                // 等待 AudioTrack 实际将缓冲区中的 PCM 帧完整播放完毕，避免末尾音频被提前释放丢弃
                while (isPlaying && framesWritten > 0L) {
                    int head = audioTrack.getPlaybackHeadPosition();
                    if (head >= framesWritten) {
                        break;
                    }
                    try {
                        Thread.sleep(20L);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            } catch (IOException ignored) {
            } finally {
                synchronized (AndroidPcmPlayback.this) {
                    isPlaying = false;
                    currentPlayingPath = null;
                    if (audioTrack != null) {
                        try {
                            audioTrack.stop();
                            audioTrack.release();
                        } catch (Exception ignored) {
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
        if (playThread != null) {
            playThread.interrupt();
            playThread = null;
        }
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception ignored) {
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
    public void release() {
        stop();
    }
}
