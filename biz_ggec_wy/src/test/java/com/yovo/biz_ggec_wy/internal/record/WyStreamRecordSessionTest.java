package com.yovo.biz_ggec_wy.internal.record;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_wy.api.StreamRecordConfig;
import com.yovo.biz_ggec_wy.api.StreamRecordListener;
import com.yovo.biz_ggec_wy.api.WyControl;
import com.yovo.biz_ggec_wy.internal.center.port.WyClassicRadioPort;
import com.yovo.lib_ggec_audio.AudioFrameContract;
import com.yovo.lib_ggec_audio.record.AudioCodecProviderPort;
import com.yovo.lib_ggec_audio.record.AudioDecoder;
import com.yovo.lib_ggec_audio.record.AudioFileWriter;

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

    private static class FakeCodecProvider implements AudioCodecProviderPort {
        boolean decoderCreated;
        boolean decoderReleased;
        boolean fileWriterCreated;
        boolean fileWriterClosed;

        @NonNull
        @Override
        public AudioDecoder createDecoder() {
            decoderCreated = true;
            return new AudioDecoder() {
                @Nullable
                @Override
                public byte[] decode(@NonNull byte[] opusPacket) {
                    byte[] pcm = new byte[AudioFrameContract.PCM_FRAME_BYTES];
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
        public AudioFileWriter createPcmFileWriter(@NonNull File targetFile) {
            fileWriterCreated = true;
            return new AudioFileWriter() {
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

    private static class FakeRadio implements WyClassicRadioPort {
        boolean audioConnected;
        boolean audioDisconnected;
        byte[] lastSentControl;

        @NonNull
        @Override
        public List<BondedDevice> getBondedDevices() {
            return List.of();
        }

        @Override
        public void startDiscovery(@NonNull DiscoveryListener listener) {
        }

        @Override
        public void stopDiscovery() {
        }

        @Override
        public void connect(@NonNull String classicAddress, @NonNull SppStateListener listener) {
        }

        @Override
        public void disconnect() {
        }

        @Override
        public void connectAudioSpp(@NonNull ActionCallback callback) {
            audioConnected = true;
            callback.onResult(true, null);
        }

        @Override
        public void disconnectAudioSpp() {
            audioDisconnected = true;
            audioConnected = false;
        }

        @Override
        public void sendControl(@NonNull byte[] data, @NonNull SendCallback callback) {
            lastSentControl = data;
            callback.onComplete(true, null);
        }

        @Override
        public boolean isControlConnected() {
            return true;
        }

        @Override
        public boolean isAudioConnected() {
            return audioConnected;
        }

        @Override
        public boolean isClassicConnected(@NonNull String classicAddress) {
            return true;
        }

        @Override
        public void release() {
        }
    }

    private static class DirectDispatcher implements WyStreamRecordSession.MainDispatcher {
        @Override
        public void post(@NonNull Runnable r) {
            r.run();
        }

        @Override
        public void postDelayed(@NonNull Runnable r, long delayMillis) {
        }

        @Override
        public void removeCallbacks(@NonNull Runnable r) {
        }
    }

    @Test
    public void testStreamRecordLifecycleAndPcmDeliveryWithDeca440Burst() throws Exception {
        FakeCodecProvider codecProvider = new FakeCodecProvider();
        FakeRadio radio = new FakeRadio();
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
                radio, codecProvider, pathResolver, new DirectDispatcher(), directExecutor
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

        // 1. 发起开启录音
        recordSession.startStreamRecord(StreamRecordConfig.streamOnly(), listener);

        // 验证已拉起音频 SPP 并发送 99ECD5 命令
        assertTrue(radio.audioConnected);
        assertNotNull(radio.lastSentControl);
        assertEquals((byte) 0x99, radio.lastSentControl[0]);
        assertEquals((byte) 0xEC, radio.lastSentControl[1]);
        assertEquals((byte) 0xD5, radio.lastSentControl[2]);

        // 模拟对端返回 99ECD5 成功响应
        recordSession.onControlDataReceived(new byte[]{
                (byte) 0x99, (byte) 0xEC, (byte) 0xD5, 0x00, 0x01, 0x00, 0x12, 0x34
        });

        assertTrue(recordSession.isStreamRecording());
        assertTrue(started.get());
        assertTrue(codecProvider.decoderCreated);

        // 2. 灌入 WY01 专属数据包：440 字节一包，内含 10 帧（每帧 4B 序号 + 40B Opus）
        byte[] deca440 = new byte[440];
        int step = 44;
        for (int i = 0; i < 10; i++) {
            int idx = 1 + i;
            deca440[i * step] = (byte) (idx & 0xFF);
            deca440[i * step + 1] = (byte) ((idx >> 8) & 0xFF);
            deca440[i * step + 2] = (byte) ((idx >> 16) & 0xFF);
            deca440[i * step + 3] = (byte) ((idx >> 24) & 0xFF);
        }
        recordSession.onAudioChunkReceived(deca440);

        // 等待单线程 executor 执行完解码
        Thread.sleep(100);

        assertEquals(10, receivedPcm.size());
        assertEquals(10, receivedAmplitudes.size());
        assertEquals(AudioFrameContract.PCM_FRAME_BYTES, receivedPcm.get(0).length);
        assertTrue(receivedAmplitudes.get(0) > 0f);

        // 3. 停止录音
        AtomicBoolean callbackSuccess = new AtomicBoolean(false);
        recordSession.stopStreamRecord(result -> {
            if (result instanceof WyControl.Outcome.Ok) {
                callbackSuccess.set(true);
            }
        });

        // 验证发送了 99ECD6 命令
        assertNotNull(radio.lastSentControl);
        assertEquals((byte) 0x99, radio.lastSentControl[0]);
        assertEquals((byte) 0xEC, radio.lastSentControl[1]);
        assertEquals((byte) 0xD6, radio.lastSentControl[2]);

        // 模拟对端返回 99ECD6 成功响应
        recordSession.onControlDataReceived(new byte[]{
                (byte) 0x99, (byte) 0xEC, (byte) 0xD6, 0x00, 0x01, 0x00, 0x12, 0x34
        });

        Thread.sleep(100);

        assertFalse(recordSession.isStreamRecording());
        assertTrue(stopped.get());
        assertTrue(callbackSuccess.get());
        assertTrue(codecProvider.decoderReleased);
        assertTrue(radio.audioDisconnected);

        recordSession.release();
    }

    @Test
    public void testRemoteStopNotificationStopsPipeline() throws Exception {
        FakeCodecProvider codecProvider = new FakeCodecProvider();
        FakeRadio radio = new FakeRadio();
        WyRecordPathResolver pathResolver = new WyRecordPathResolver(null);
        ExecutorService directExecutor = Executors.newSingleThreadExecutor();

        WyStreamRecordSession recordSession = new WyStreamRecordSession(
                radio, codecProvider, pathResolver, new DirectDispatcher(), directExecutor
        );

        AtomicBoolean stopped = new AtomicBoolean(false);
        recordSession.startStreamRecord(StreamRecordConfig.streamOnly(), new StreamRecordListener() {
            @Override
            public void onStarted() {
            }

            @Override
            public void onPcmFrame(@NonNull byte[] pcmData, float amplitude) {
            }

            @Override
            public void onStopped() {
                stopped.set(true);
            }
        });

        // 启动成功
        recordSession.onControlDataReceived(new byte[]{
                (byte) 0x99, (byte) 0xEC, (byte) 0xD5, 0x00, 0x01, 0x00, 0x12, 0x34
        });
        assertTrue(recordSession.isStreamRecording());

        // 耳机端主动按键停录，控制流上报 99 EC D6
        recordSession.onControlDataReceived(new byte[]{
                (byte) 0x99, (byte) 0xEC, (byte) 0xD6, 0x00, 0x01, 0x00, 0x12, 0x34
        });

        Thread.sleep(100);
        assertFalse(recordSession.isStreamRecording());
        assertTrue(stopped.get());
        assertTrue(radio.audioDisconnected);

        recordSession.release();
    }

    @Test
    public void testListAndDeleteLocalSavedFiles() throws Exception {
        FakeCodecProvider codecProvider = new FakeCodecProvider();
        FakeRadio radio = new FakeRadio();
        File tempDir = File.createTempFile("wy_record_test", "");
        tempDir.delete();
        tempDir.mkdirs();
        try {
            WyRecordPathResolver pathResolver = new WyRecordPathResolver(null) {
                @NonNull
                @Override
                public File getRootDir() {
                    return tempDir;
                }
            };
            WyStreamRecordSession recordSession = new WyStreamRecordSession(
                    radio, codecProvider, pathResolver, new DirectDispatcher(), Executors.newSingleThreadExecutor()
            );

            // 创建两个临时音频文件
            File f1 = new File(tempDir, "WY_stream_1.pcm");
            File f2 = new File(tempDir, "WY_stream_2.pcm");
            File f3 = new File(tempDir, "other.txt");
            assertTrue(f1.createNewFile());
            assertTrue(f2.createNewFile());
            assertTrue(f3.createNewFile());
            f1.setLastModified(1000L);
            f2.setLastModified(2000L);

            List<File> files = recordSession.listLocalSavedFiles();
            assertEquals(2, files.size());
            assertEquals("WY_stream_2.pcm", files.get(0).getName());
            assertEquals("WY_stream_1.pcm", files.get(1).getName());

            // 测试删除
            assertTrue(recordSession.deleteLocalSavedFile(f1.getAbsolutePath()));
            assertFalse(f1.exists());
            assertEquals(1, recordSession.listLocalSavedFiles().size());

            recordSession.release();
        } finally {
            File[] remaining = tempDir.listFiles();
            if (remaining != null) {
                for (File f : remaining) {
                    f.delete();
                }
            }
            tempDir.delete();
        }
    }
}
