package com.yovo.lib_ggec_audio.record;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.junit.Test;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 共享 PCM 管线：断言「启动 → 解码分发 → 停止 → 落盘回调」与失败穿透的唯一实现。
 *
 * <p>HS / WY 现在共用这一份实现，因此这里的行为断言同时覆盖两个产品。</p>
 */
public final class StreamRecordPipelineTest {

    private static final String FAILURE_START = "start-failed";

    private static final class FakeCodecProvider implements AudioCodecProviderPort {
        /** 兼容既有断言：只要任一解码器被释放即为 true。 */
        boolean decoderReleased;
        boolean writerClosed;
        final List<byte[]> written = new ArrayList<>();
        final List<FakeDecoder> decoders = new ArrayList<>();

        /** 每次 createDecoder 产生一个可单独观察释放状态的新实例。 */
        final class FakeDecoder implements AudioDecoder {
            final int id;
            volatile boolean released;

            FakeDecoder(int id) {
                this.id = id;
            }

            @Nullable
            @Override
            public byte[] decode(@NonNull byte[] opusPacket) {
                return new byte[]{0x10, 0x20};
            }

            @Override
            public void release() {
                released = true;
                decoderReleased = true;
            }
        }

        @NonNull
        @Override
        public AudioDecoder createDecoder() {
            FakeDecoder decoder = new FakeDecoder(decoders.size());
            decoders.add(decoder);
            return decoder;
        }

        @NonNull
        @Override
        public AudioFileWriter createPcmFileWriter(@NonNull File targetFile) {
            return new AudioFileWriter() {
                @Override
                public void writePcm(@NonNull byte[] pcmData) {
                    written.add(pcmData);
                }

                @Override
                public void close() {
                    writerClosed = true;
                }

                @NonNull
                @Override
                public File getFile() {
                    return targetFile;
                }

                @Override
                public long getDurationMs() {
                    return 0L;
                }

                @Override
                public long getFileSizeBytes() {
                    return 0L;
                }
            };
        }
    }

    private static final class RecordingListener implements StreamRecordListener {
        int started;
        int stopped;
        int errors;
        final List<Float> amplitudes = new ArrayList<>();
        final List<File> saved = new ArrayList<>();

        @Override
        public void onStarted() {
            started++;
        }

        @Override
        public void onPcmFrame(@NonNull byte[] pcmData, float amplitude) {
            amplitudes.add(amplitude);
        }

        @Override
        public void onFileSaved(@NonNull File file, long durationMs, long fileSizeBytes) {
            saved.add(file);
        }

        @Override
        public void onError(@NonNull String message) {
            errors++;
        }

        @Override
        public void onStopped() {
            stopped++;
        }
    }

    private static File tempDir() {
        File dir = new File(System.getProperty("java.io.tmpdir"),
                "lib_audio_pipeline_" + System.nanoTime());
        assertTrue(dir.mkdirs());
        return dir;
    }

    private static byte[] quad176() {
        int step = 44;
        byte[] chunk = new byte[step * 4];
        for (int i = 0; i < 4; i++) {
            int idx = i + 1;
            chunk[i * step] = (byte) (idx & 0xFF);
            chunk[i * step + 1] = (byte) ((idx >> 8) & 0xFF);
            chunk[i * step + 2] = (byte) ((idx >> 16) & 0xFF);
            chunk[i * step + 3] = (byte) ((idx >> 24) & 0xFF);
        }
        return chunk;
    }

    private static AudioLog silentLog() {
        return new AudioLog() {
            @Override
            public void debug(String message) {
            }

            @Override
            public void info(String message) {
            }

            @Override
            public void warn(String message) {
            }
        };
    }

    private static StreamRecordPipeline<String> pipeline(FakeCodecProvider codec, File dir) {
        return pipeline(codec, dir, Executors.newSingleThreadExecutor());
    }

    private static StreamRecordPipeline<String> pipeline(FakeCodecProvider codec, File dir,
                                                         java.util.concurrent.ExecutorService executor) {
        return new StreamRecordPipeline<>(
                codec,
                () -> dir,
                () -> new File(dir, "default.pcm"),
                Runnable::run,
                silentLog(),
                com.yovo.lib_ggec_audio.AudioFrameTrace.NONE,
                executor);
    }

    /** 手动执行器：把提交的任务排队，由测试显式 drain，用于稳定复现「停→立刻开」的清理竞态。 */
    private static final class ManualExecutor extends AbstractExecutorService {
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            List<Runnable> pending = new ArrayList<>(queue);
            queue.clear();
            return pending;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && queue.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, @NonNull TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(@NonNull Runnable command) {
            queue.addLast(command);
        }

        /** 依次跑完当前排队任务，返回执行个数。 */
        int runPending() {
            int ran = 0;
            Runnable task;
            while ((task = queue.pollFirst()) != null) {
                task.run();
                ran++;
            }
            return ran;
        }
    }

    private static StreamRecordPipeline.Commands<String> okCommands() {
        return new StreamRecordPipeline.Commands<>() {
            @Override
            public void start(@NonNull StreamRecordPipeline.Reply<String> reply) {
                reply.onReply(null);
            }

            @Override
            public void stop(@NonNull StreamRecordPipeline.Reply<String> reply) {
                reply.onReply(null);
            }

            @Override
            public void detach() {
            }
        };
    }

    @Test
    public void decodesDispatchesAndStops() throws Exception {
        FakeCodecProvider codec = new FakeCodecProvider();
        File dir = tempDir();
        StreamRecordPipeline<String> pipeline = pipeline(codec, dir);
        RecordingListener listener = new RecordingListener();

        pipeline.start(StreamRecordConfig.streamOnly(), listener, okCommands());
        assertTrue(pipeline.isRecording());
        assertEquals(1, listener.started);

        pipeline.onAudioChunk(quad176());
        waitUntil(() -> !codec.written.isEmpty() || listener.amplitudes.size() == 4);

        assertEquals(4, listener.amplitudes.size());
        assertTrue(listener.amplitudes.get(0) > 0f);
        assertTrue(codec.written.isEmpty());

        pipeline.stop(failure -> assertNull(failure));
        waitUntil(() -> listener.stopped == 1);

        assertFalse(pipeline.isRecording());
        assertEquals(0, listener.errors);
        assertTrue(codec.decoderReleased);
        assertTrue(codec.written.isEmpty());
    }

    @Test
    public void startFailureGoesThroughProductFailureAndStops() throws Exception {
        FakeCodecProvider codec = new FakeCodecProvider();
        File dir = tempDir();
        StreamRecordPipeline<String> pipeline = pipeline(codec, dir);
        RecordingListener listener = new RecordingListener();

        pipeline.start(StreamRecordConfig.streamOnly(), listener,
                new StreamRecordPipeline.Commands<>() {
                    @Override
                    public void start(@NonNull StreamRecordPipeline.Reply<String> reply) {
                        reply.onReply(FAILURE_START);
                    }

                    @Override
                    public void stop(@NonNull StreamRecordPipeline.Reply<String> reply) {
                        reply.onReply(null);
                    }

                    @Override
                    public void detach() {
                    }
                });

        waitUntil(() -> listener.stopped == 1);
        assertEquals(1, listener.errors);
        assertEquals(1, listener.stopped);
        assertFalse(pipeline.isRecording());
        assertTrue(codec.decoderReleased);
    }

    @Test
    public void stopFailureIsReportedToCallback() throws Exception {
        FakeCodecProvider codec = new FakeCodecProvider();
        File dir = tempDir();
        StreamRecordPipeline<String> pipeline = pipeline(codec, dir);
        RecordingListener listener = new RecordingListener();
        String[] observed = new String[1];

        pipeline.start(StreamRecordConfig.streamOnly(), listener,
                new StreamRecordPipeline.Commands<>() {
                    @Override
                    public void start(@NonNull StreamRecordPipeline.Reply<String> reply) {
                        reply.onReply(null);
                    }

                    @Override
                    public void stop(@NonNull StreamRecordPipeline.Reply<String> reply) {
                        reply.onReply("stop-failed");
                    }

                    @Override
                    public void detach() {
                    }
                });
        pipeline.stop(failure -> observed[0] = failure);

        waitUntil(() -> observed[0] != null);
        assertEquals("stop-failed", observed[0]);
        assertEquals(1, listener.errors);
    }

    /**
     * 快速重启资源隔离：stop 后旧会话的延迟清理尚未执行时立刻 start，旧解码器必须被释放，
     * 新解码器不得被误释放，且新会话仍能正常解码。
     */
    @Test
    public void fastRestartReleasesOldDecoderNotNewOne() {
        FakeCodecProvider codec = new FakeCodecProvider();
        File dir = tempDir();
        ManualExecutor executor = new ManualExecutor();
        StreamRecordPipeline<String> pipeline = pipeline(codec, dir, executor);
        RecordingListener listener = new RecordingListener();

        pipeline.start(StreamRecordConfig.streamOnly(), listener, okCommands());
        assertEquals(1, codec.decoders.size());
        FakeCodecProvider.FakeDecoder firstDecoder = codec.decoders.get(0);

        pipeline.onAudioChunk(quad176());
        executor.runPending();
        assertEquals(4, listener.amplitudes.size());
        assertFalse(firstDecoder.released);

        // 停止第一会话：清理任务入队但尚未执行。
        pipeline.stop(failure -> assertNull(failure));
        assertFalse(pipeline.isRecording());

        // 立刻开始第二会话（旧会话清理仍在队列里）。
        pipeline.start(StreamRecordConfig.streamOnly(), listener, okCommands());
        assertEquals(2, codec.decoders.size());
        FakeCodecProvider.FakeDecoder secondDecoder = codec.decoders.get(1);

        // 执行旧会话的延迟清理：只应释放旧解码器。
        executor.runPending();
        assertTrue(firstDecoder.released);
        assertFalse(secondDecoder.released);

        // 新会话仍可正常解码。
        pipeline.onAudioChunk(quad176());
        executor.runPending();
        assertEquals(8, listener.amplitudes.size());
        assertFalse(secondDecoder.released);
    }

    @Test
    public void resolvesCustomFileNameUnderDefaultStreamDir() {
        File dir = tempDir();
        StreamRecordConfig config = StreamRecordConfig.fileWithCustomName("memo", null);
        File target = StreamRecordTargets.resolve(config, dir, new File(dir, "default.pcm"));
        assertNotNull(target);
        assertEquals(new File(dir, "memo.pcm"), target);

        StreamRecordConfig withSuffix = StreamRecordConfig.fileWithCustomName("memo.pcm", null);
        assertEquals(new File(dir, "memo.pcm"),
                StreamRecordTargets.resolve(withSuffix, dir, new File(dir, "default.pcm")));

        assertEquals(new File(dir, "default.pcm"),
                StreamRecordTargets.resolve(StreamRecordConfig.defaultFile(), dir,
                        new File(dir, "default.pcm")));
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("等待管线状态超时");
            }
            Thread.sleep(10L);
        }
    }
}
