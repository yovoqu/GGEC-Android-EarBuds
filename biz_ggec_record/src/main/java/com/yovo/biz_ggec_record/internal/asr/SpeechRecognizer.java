package com.yovo.biz_ggec_record.internal.asr;

import android.content.Context;
import android.content.res.AssetManager;
import com.yovo.biz_ggec_record.provider.lib_common.log.RecordLog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.yovo.biz_ggec_record.internal.audio.PcmNormalizer;
import com.yovo.biz_ggec_record.model.RecordAsrModel;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * sherpa-onnx 流式识别封装。音频入队不阻塞；模型加载、推理和释放均在专用线程。
 */
public final class SpeechRecognizer {

    private static final String TAG = "SpeechRecognizer";
    private static final int MAX_QUEUED_SAMPLES = 16_000 * 30;

    public interface Listener {
        void onAsrStatus(@NonNull AsrStatus status);

        void onAsrPartial(@NonNull String text);

        void onAsrFinal(@NonNull String text);

        void onAsrError(@NonNull String message);
    }

    private final AssetManager assetManager;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final ArrayDeque<float[]> samplesQueue = new ArrayDeque<>();
    private int queuedSamples;

    @Nullable
    private volatile Listener listener;
    private volatile boolean running;
    private volatile boolean finishRequested;
    @Nullable
    private Thread worker;
    private volatile int sessionId;
    @NonNull
    private volatile AsrStatus status = AsrStatus.IDLE;
    public final AtomicLong droppedSamples = new AtomicLong();

    public SpeechRecognizer(@NonNull Context context) {
        this.assetManager = context.getApplicationContext().getAssets();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @NonNull
    public AsrStatus status() {
        return status;
    }

    public void start(@NonNull RecordAsrModel model) {
        stop();
        lock.lock();
        try {
            samplesQueue.clear();
            queuedSamples = 0;
        } finally {
            lock.unlock();
        }
        finishRequested = false;
        running = true;
        int id = ++sessionId;
        worker = new Thread(() -> recognitionLoop(model, id), "speech-recognizer");
        worker.start();
    }

    public void acceptPcm(@NonNull float[] samples) {
        if (!running || finishRequested || samples.length == 0) {
            return;
        }
        lock.lock();
        try {
            while (queuedSamples + samples.length > MAX_QUEUED_SAMPLES && !samplesQueue.isEmpty()) {
                queuedSamples -= samplesQueue.removeFirst().length;
                droppedSamples.incrementAndGet();
            }
            if (samples.length <= MAX_QUEUED_SAMPLES) {
                samplesQueue.addLast(samples);
                queuedSamples += samples.length;
                notEmpty.signal();
            } else {
                droppedSamples.addAndGet(samples.length);
            }
        } finally {
            lock.unlock();
        }
    }

    /** 本地文件读完后调用；等待队列消费完并输出最终结果。 */
    public void finishInput() {
        if (!running) {
            return;
        }
        finishRequested = true;
        lock.lock();
        try {
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        sessionId++;
        running = false;
        finishRequested = false;
        lock.lock();
        try {
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
        Thread thread = worker;
        if (thread != null) {
            try {
                thread.join(2_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        worker = null;
        lock.lock();
        try {
            samplesQueue.clear();
            queuedSamples = 0;
        } finally {
            lock.unlock();
        }
        status = AsrStatus.IDLE;
    }

    @Nullable
    private float[] takeSamples() {
        lock.lock();
        try {
            if (samplesQueue.isEmpty()) {
                notEmpty.await(20, TimeUnit.MILLISECONDS);
            }
            float[] samples = samplesQueue.pollFirst();
            if (samples != null) {
                queuedSamples -= samples.length;
            }
            return samples;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            lock.unlock();
        }
    }

    private boolean isActive(int id) {
        return running && id == sessionId;
    }

    private void recognitionLoop(@NonNull RecordAsrModel model, int id) {
        OnlineRecognizer recognizer = null;
        OnlineStream stream = null;
        try {
            updateStatus(AsrStatus.LOADING, id);
            OnlineRecognizer nativeRecognizer = new OnlineRecognizer(
                    assetManager, AsrModelConfigs.recognizerConfig(model));
            recognizer = nativeRecognizer;
            if (!isActive(id)) {
                return;
            }
            OnlineStream nativeStream = nativeRecognizer.createStream("");
            stream = nativeStream;
            updateStatus(AsrStatus.READY, id);

            while (isActive(id)) {
                float[] samples = takeSamples();
                if (!isActive(id)) {
                    break;
                }
                if (samples != null) {
                    nativeStream.acceptWaveform(samples, PcmNormalizer.TARGET_SAMPLE_RATE);
                    processStream(nativeRecognizer, nativeStream, model);
                } else if (finishRequested) {
                    nativeStream.inputFinished();
                    String remaining = processStream(nativeRecognizer, nativeStream, model);
                    if (!remaining.isBlank()) {
                        Listener current = listener;
                        if (current != null) {
                            current.onAsrFinal(remaining);
                        }
                    }
                    Listener current = listener;
                    if (current != null) {
                        current.onAsrPartial("");
                    }
                    updateStatus(AsrStatus.FINISHED, id);
                    break;
                }
            }
        } catch (Exception e) {
            if (isActive(id)) {
                RecordLog.e(TAG, "语音识别异常", e);
                Listener current = listener;
                if (current != null) {
                    current.onAsrError("语音识别异常: " + e.getMessage());
                }
            }
        } finally {
            if (stream != null) {
                try {
                    stream.release();
                } catch (Exception ignored) {
                }
            }
            if (recognizer != null) {
                try {
                    recognizer.release();
                } catch (Exception ignored) {
                }
            }
            if (id == sessionId && status != AsrStatus.FINISHED) {
                status = AsrStatus.IDLE;
            }
        }
    }

    /** 返回尚未因 endpoint 提交的当前文本。 */
    @NonNull
    private String processStream(@NonNull OnlineRecognizer recognizer,
                                 @NonNull OnlineStream stream,
                                 @NonNull RecordAsrModel model) {
        while (recognizer.isReady(stream)) {
            recognizer.decode(stream);
        }
        boolean endpoint = recognizer.isEndpoint(stream);
        if (endpoint && model == RecordAsrModel.PARAFORMER) {
            stream.acceptWaveform(new float[(int) (0.8 * 16_000)], 16_000);
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream);
            }
        }
        String text = recognizer.getResult(stream).getText();
        if (text == null) {
            text = "";
        }
        Listener current = listener;
        if (endpoint) {
            if (!text.isBlank() && current != null) {
                current.onAsrFinal(text);
            }
            recognizer.reset(stream);
            if (current != null) {
                current.onAsrPartial("");
            }
            return "";
        }
        if (current != null) {
            current.onAsrPartial(text);
        }
        return text;
    }

    private void updateStatus(@NonNull AsrStatus value, int id) {
        if (id != sessionId) {
            return;
        }
        status = value;
        Listener current = listener;
        if (current != null) {
            current.onAsrStatus(value);
        }
    }
}
