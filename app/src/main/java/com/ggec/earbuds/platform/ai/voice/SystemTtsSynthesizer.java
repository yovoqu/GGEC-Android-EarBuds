package com.ggec.earbuds.platform.ai.voice;

import android.content.Context;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiSpeechSynthesizer;
import com.ggec.earbuds.domain.ai.voice.AiVoiceException;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link AiSpeechSynthesizer} 的系统 TTS 实现：{@code android.speech.tts.TextToSpeech}。
 *
 * <p>为什么用系统 TTS 而不是平台的声音：级联链路里"能对话的模型"和"能念中文的声音"常常不是同一家，
 * 而系统 TTS 离线可用、不花额度、不需要额外密钥。代价是音色少，所以端口留了 {@code voice} 参数，
 * 将来接平台音色时换一个实现即可，调用方不用改。</p>
 *
 * <p>为什么 {@code speak} 要阻塞到念完：端口的监听器只报"开始念了/念完了"，如果 {@code speak} 立刻返回，
 * 调用方就得多维护一套"某次朗读还没结束"的计数。阻塞在实现里、用 {@link UtteranceProgressListener}
 * 放行，调用方只需要处理三个回调。所以本类必须被后台线程调用——端口注释里已经写明。</p>
 *
 * <p>初始化失败（设备没有 TTS 引擎、没有中文语音包、引擎被停用）不崩：失败原因存在字段里，
 * 第一次 {@code speak} 时折成 {@link AiVoiceException} 抛给调用方，由快照显示中文原因。</p>
 *
 * <p>{@link #stop()} 幂等，并且会把阻塞在 {@code speak} 上的线程叫醒：打断时"停"必须立刻生效，
 * 不能等这一句念完。</p>
 */
public final class SystemTtsSynthesizer implements AiSpeechSynthesizer, AutoCloseable {

    private static final long INIT_TIMEOUT_MILLIS = 5_000L;
    /** 单次朗读的总时限。按中文正常语速，一万字也不到 40 分钟；给 120s 是为了兜住引擎卡死。 */
    private static final long SPEAK_TIMEOUT_MILLIS = 120_000L;

    private final Context context;
    private final Object lock = new Object();
    private final CountDownLatch initLatch = new CountDownLatch(1);
    private final AtomicInteger utterances = new AtomicInteger();
    private final UtteranceProgressListener progress = new Progress();

    private TextToSpeech engine;
    private boolean initStarted;
    private int initStatus = TextToSpeech.ERROR;
    private boolean ready;
    @Nullable
    private AiFailure initFailure;

    /** 当前这次朗读。回调靠它把 {@code utteranceId} 对回调用方的监听器；旧 id 的回调一律忽略。 */
    @Nullable
    private Call current;

    public SystemTtsSynthesizer(@NonNull Context context) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
    }

    @Override
    public void speak(@NonNull String text, @Nullable String voice, @NonNull Listener listener)
            throws AiVoiceException {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(listener, "listener");
        if (text.isBlank()) {
            listener.onDone();
            return;
        }
        TextToSpeech engine = readyEngine();
        applyVoice(engine, voice);
        String utteranceId = "ai-voice-" + utterances.incrementAndGet();
        CountDownLatch finished = new CountDownLatch(1);
        synchronized (lock) {
            current = new Call(utteranceId, listener, finished);
        }
        int queued = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        if (queued != TextToSpeech.SUCCESS) {
            synchronized (lock) {
                current = null;
            }
            throw new AiVoiceException(new AiFailure(AiFailureKind.UNKNOWN,
                    "系统语音合成拒绝朗读这段回答（引擎返回 " + queued + "）。"));
        }
        boolean done;
        try {
            done = finished.await(SPEAK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            discard(utteranceId);
            throw new AiVoiceException(new AiFailure(AiFailureKind.CANCELLED, "朗读被线程中断打断。"));
        }
        if (!done) {
            engine.stop();
            discard(utteranceId);
            throw new AiVoiceException(new AiFailure(AiFailureKind.TIMEOUT, "系统语音合成长时间没有念完。"));
        }
        discard(utteranceId);
    }

    @Override
    public void stop() {
        TextToSpeech engine;
        Call pending;
        synchronized (lock) {
            engine = this.engine;
            pending = current;
            current = null;
        }
        if (engine != null) {
            engine.stop();
        }
        // 手动放行：引擎的 onStop 回调不保证在 stop() 返回前到达，阻塞中的 speak 不能等它。
        if (pending != null) {
            pending.finished.countDown();
        }
    }

    @Override
    public void close() {
        stop();
        TextToSpeech engine;
        synchronized (lock) {
            engine = this.engine;
            this.engine = null;
            ready = false;
        }
        if (engine != null) {
            engine.shutdown();
        }
    }

    /**
     * 拿到可用的引擎；不可用就抛，把中文原因交给上层放进快照。
     */
    private TextToSpeech readyEngine() throws AiVoiceException {
        ensureInitStarted();
        try {
            if (!initLatch.await(INIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                throw new AiVoiceException(new AiFailure(AiFailureKind.TIMEOUT,
                        "系统语音合成迟迟没有初始化完成。"));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiVoiceException(new AiFailure(AiFailureKind.CANCELLED, "等待语音合成初始化时被打断。"));
        }
        TextToSpeech available;
        AiFailure reason;
        int status;
        synchronized (lock) {
            available = engine;
            reason = initFailure;
            status = initStatus;
        }
        if (reason != null) {
            throw new AiVoiceException(reason);
        }
        if (available == null || status != TextToSpeech.SUCCESS) {
            throw new AiVoiceException(new AiFailure(AiFailureKind.UNSUPPORTED_CAPABILITY,
                    "这台设备没有可用的系统语音合成引擎。"));
        }
        synchronized (lock) {
            if (ready) {
                return available;
            }
        }
        // 中文是硬需求：回答都是中文，缺语音包时用英文引擎念会变成一串乱码，宁可明确报错。
        int language = available.setLanguage(Locale.CHINA);
        if (language == TextToSpeech.LANG_MISSING_DATA
                || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            AiFailure missing = new AiFailure(AiFailureKind.UNSUPPORTED_CAPABILITY,
                    "系统的语音合成引擎没有中文语音包，请先在系统设置里安装中文 TTS 数据。");
            synchronized (lock) {
                initFailure = missing;
            }
            throw new AiVoiceException(missing);
        }
        available.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        available.setOnUtteranceProgressListener(progress);
        synchronized (lock) {
            ready = true;
        }
        return available;
    }

    private void ensureInitStarted() {
        synchronized (lock) {
            if (initStarted) {
                return;
            }
            initStarted = true;
        }
        try {
            TextToSpeech created = new TextToSpeech(context, completion -> {
                synchronized (lock) {
                    initStatus = completion;
                    if (completion != TextToSpeech.SUCCESS) {
                        initFailure = new AiFailure(AiFailureKind.UNSUPPORTED_CAPABILITY,
                                "系统语音合成初始化失败（引擎缺失或被停用）。");
                    }
                }
                initLatch.countDown();
            });
            // init 回调可能比构造返回更早，也可能更晚；两边都只写自己的字段，不互相覆盖。
            synchronized (lock) {
                engine = created;
            }
        } catch (RuntimeException unavailable) {
            synchronized (lock) {
                initFailure = new AiFailure(AiFailureKind.UNSUPPORTED_CAPABILITY,
                        "无法创建系统语音合成引擎：" + detailOf(unavailable), unavailable);
            }
            initLatch.countDown();
        }
    }

    private void applyVoice(TextToSpeech engine, @Nullable String voiceName) {
        if (voiceName == null || voiceName.isBlank()) {
            return;
        }
        Set<android.speech.tts.Voice> voices = engine.getVoices();
        if (voices == null) {
            return;
        }
        for (android.speech.tts.Voice voice : voices) {
            if (voiceName.trim().equals(voice.getName())) {
                engine.setVoice(voice);
                return;
            }
        }
        // 名字对不上就用引擎默认音色：音色偏好不该让整句回答念不出来。
    }

    private void discard(String utteranceId) {
        synchronized (lock) {
            if (current != null && current.utteranceId.equals(utteranceId)) {
                current = null;
            }
        }
    }

    /** 回调只认当前 utteranceId：上一句的迟到回调不能把这一句的状态改掉。 */
    @Nullable
    private Call matching(String utteranceId) {
        synchronized (lock) {
            Call active = current;
            if (active == null || !active.utteranceId.equals(utteranceId)) {
                return null;
            }
            return active;
        }
    }

    private final class Progress extends UtteranceProgressListener {

        @Override
        public void onStart(String utteranceId) {
            Call active = matching(utteranceId);
            if (active != null) {
                active.listener.onStarted();
            }
        }

        @Override
        public void onDone(String utteranceId) {
            Call active = matching(utteranceId);
            if (active != null) {
                active.finished.countDown();
                active.listener.onDone();
            }
        }

        @Override
        public void onError(String utteranceId) {
            onError(utteranceId, TextToSpeech.ERROR);
        }

        @Override
        public void onError(String utteranceId, int errorCode) {
            Call active = matching(utteranceId);
            if (active != null) {
                active.finished.countDown();
                active.listener.onFailure(new AiFailure(AiFailureKind.UNKNOWN,
                        "系统语音合成朗读失败（错误码 " + errorCode + "）。"));
            }
        }

        @Override
        public void onStop(String utteranceId, boolean interrupted) {
            Call active = matching(utteranceId);
            if (active != null) {
                // 被打断不是失败：这一句确实停了，speak 必须返回，状态机自己决定要不要回聆听。
                active.finished.countDown();
                active.listener.onDone();
            }
        }
    }

    /** 一次朗读的三件套：id 用来对回调，latch 用来放行阻塞的调用线程。 */
    private record Call(String utteranceId, Listener listener, CountDownLatch finished) {
    }

    private static String detailOf(Throwable error) {
        String detail = error.getMessage();
        return detail == null || detail.isBlank() ? error.getClass().getSimpleName() : detail;
    }
}
