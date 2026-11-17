package com.yovo.biz_ggec_wy.internal.record;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.api.StreamRecordConfig;
import com.yovo.biz_ggec_wy.api.StreamRecordListener;
import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.internal.center.port.WyAudioCodecProviderPort;
import com.yovo.biz_ggec_wy.internal.center.port.WyAudioDecoder;
import com.yovo.biz_ggec_wy.internal.center.port.WyAudioFileWriter;
import com.yovo.biz_ggec_wy.internal.record.assembler.WyAudioFrameContract;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WyStreamRecordSessionTest {

    private static class FakeCodecProvider implements WyAudioCodecProviderPort {
        boolean decoderCreated;
        boolean decoderReleased;
        boolean fileWriterCreated;
        boolean fileWriterClosed;

        @NonNull
        @Override
        public WyAudioDecoder createDecoder() {
            decoderCreated = true;
            return new WyAudioDecoder() {
                @Nullable
                @Override
                public byte[] decode(@NonNull byte[] opusPacket) {
                    byte[] pcm = new byte[WyAudioFrameContract.PCM_FRAME_BYTES];
                    // 填充一些测试 PCM 数据 (16-bit)
                    pcm[0] = 0x10;
                    pcm[1] = 0x20;
                    return pcm;
                }

                @Override
                public void release() {
                    decoderReleased = true;
                }
            };
        }

        @NonNull
        @Override
        public WyAudioFileWriter createPcmFileWriter(@NonNull File targetFile) {
            fileWriterCreated = true;
            return new WyAudioFileWriter() {
                @Override
                public void writePcm(@NonNull byte[] pcmData) {
                }

                @Override
                public void close() {
                    fileWriterClosed = true;
                }

                @NonNull
                @Override
                public File getFile() {
                    return targetFile;
                }

                @Override
                public long getDurationMs() {
                    return 1000L;
                }

                @Override
                public long getFileSizeBytes() {
                    return 6400L;
                }
            };
        }
    }

    @Test
    public void testStreamRecordLifecycleAndPcmDelivery() throws Exception {
        FakeCodecProvider codecProvider = new FakeCodecProvider();
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "wy_test_" + System.currentTimeMillis());
        tempDir.mkdirs();

        WyRecordPathResolver pathResolver = new WyRecordPathResolver(null) {
            @NonNull
            @Override
            public File getRootDir() {
                return tempDir;
            }

            @NonNull
            @Override
            public File getStreamDir() {
                return tempDir;
            }
        };

        ExecutorService directExecutor = Executors.newSingleThreadExecutor();
        WyStreamRecordSession recordSession = new WyStreamRecordSession(
                codecProvider, pathResolver, Runnable::run, directExecutor
        );

        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);
        List<byte[]> receivedPcm = new ArrayList<>();
        List<Float> receivedAmplitudes = new ArrayList<>();

        StreamRecordListener listener = new StreamRecordListener() {
            @Override
            public void onStarted() {
                started.set(true);
            }

            @Override
            public void onPcmFrame(@NonNull byte[] pcmData, float amplitude) {
                receivedPcm.add(pcmData);
                receivedAmplitudes.add(amplitude);
            }

            @Override
            public void onStopped() {
                stopped.set(true);
            }
        };

        // 1. 开启录音
        recordSession.startStreamRecord(StreamRecordConfig.streamOnly(), listener);
        assertTrue(recordSession.isStreamRecording());
        assertTrue(started.get());
        assertTrue(codecProvider.decoderCreated);

        // 2. 灌入 176 字节音频帧（4 帧 Opus）
        byte[] quad176 = new byte[176];
        int step = 44;
        for (int i = 0; i < 4; i++) {
            int idx = 1 + i;
            quad176[i * step] = (byte) (idx & 0xFF);
            quad176[i * step + 1] = (byte) ((idx >> 8) & 0xFF);
            quad176[i * step + 2] = (byte) ((idx >> 16) & 0xFF);
            quad176[i * step + 3] = (byte) ((idx >> 24) & 0xFF);
        }
        recordSession.onAudioChunkReceived(quad176);

        // 等待单线程 executor 执行完
        Thread.sleep(100);

        assertEquals(4, receivedPcm.size());
        assertEquals(4, receivedAmplitudes.size());
        assertEquals(WyAudioFrameContract.PCM_FRAME_BYTES, receivedPcm.get(0).length);
        assertTrue(receivedAmplitudes.get(0) > 0f);

        // 3. 停止录音
        AtomicBoolean callbackSuccess = new AtomicBoolean(false);
        recordSession.stopStreamRecord(result -> {
            if (result instanceof WyControl.Outcome.Ok) {
                callbackSuccess.set(true);
            }
        });

        Thread.sleep(100);

        assertFalse(recordSession.isStreamRecording());
        assertTrue(stopped.get());
        assertTrue(callbackSuccess.get());
        assertTrue(codecProvider.decoderReleased);

        recordSession.release();
    }
}
