package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;

import java.util.ArrayList;
import java.util.List;

/**
 * 纯状态机：输入 (Model, Event, now)，输出 (Model, Effects)。
 *
 * <p>不碰线程、不碰 Android、不碰音频；所有副作用都变成数据。每条转移都能单测穷举，
 * 真机 trace 也能直接回放。</p>
 *
 * <h2>为什么要有"上行闸门"</h2>
 *
 * <p>实机证据：用户只问一句，助手连答 6 轮，且 3 次 {@code SpeechStarted} 全部发生在助手播放期间。
 * 服务端把这三次"开口"判给了用户，但那时用户并没有说话——是扬声器的声音被麦克风收回去又上行了一次。
 * 问题不在模型分不清谁在说话，而在<b>我们把助手自己的声音当成用户声音送了上去</b>。</p>
 *
 * <h2>为什么"停止助手"是一条复合转移</h2>
 *
 * <p>打断要做四件事：停本地播放、让服务端停止生成、按用户真正听到的毫秒截断、
 * 丢掉未提交的上行。前三件之间有数据依赖（截断毫秒来自播放进度），所以它们必须由
 * 同一条 effect 在 actor 里按固定顺序执行，而不是由 reducer 凭空给一个数。</p>
 *
 * <h2>为什么 DRAINING 必须存在</h2>
 *
 * <p>服务端 {@code response.done} 只说明"生成完了"，本地可能还有几秒音频在队列里。
 * 此时就回 LISTENING 会把麦克风打开，助手立刻听到自己的尾巴，服务端 VAD 再判成插话。</p>
 */
public final class VoiceCallReducer {

    /** 下行固定 24k PCM16 单声道。 */
    private static final long OUTPUT_BYTES_PER_MS = 24_000L * 2L / 1_000L;

    /** 排空后再多留一点余量，吸收播放器/混音器的尾部延迟。 */
    private static final long DRAIN_TAIL_MS = 120L;

    private VoiceCallReducer() {
    }

    public record Model(
            @NonNull VoiceCallState state,
            @NonNull List<AiVoiceSnapshot.Turn> turns,
            float level,
            @Nullable AiFailure failure,
            @Nullable String assistantTurnId,
            long audioBytes,
            boolean muted,
            boolean playbackStarted,
            int turnsCreated,
            @NonNull VoiceDuplex duplex,
            boolean uplinkEnabled) {

        public Model {
            turns = List.copyOf(turns);
            level = Math.max(0f, Math.min(1f, level));
        }

        @NonNull
        public static Model initial(@NonNull VoiceDuplex duplex) {
            return new Model(VoiceCallState.IDLE, List.of(), 0f, null, null, 0,
                    false, false, 0, duplex, false);
        }

        public boolean terminal() {
            return state == VoiceCallState.FAILED || state == VoiceCallState.ENDED;
        }

        /** 助手正在发声（含"生成完但还没播完"）。 */
        public boolean assistantActive() {
            return state == VoiceCallState.THINKING
                    || state == VoiceCallState.SPEAKING
                    || state == VoiceCallState.DRAINING;
        }

        /** 还有未完成的生成（DRAINING 表示生成已结束）。 */
        public boolean generating() {
            return state == VoiceCallState.THINKING || state == VoiceCallState.SPEAKING;
        }

        @NonNull
        public Model withDuplex(@NonNull VoiceDuplex nextDuplex) {
            return new Model(state, turns, level, failure, assistantTurnId, audioBytes,
                    muted, playbackStarted, turnsCreated, nextDuplex, uplinkEnabled);
        }
    }

    public record Transition(@NonNull Model model, @NonNull List<VoiceCallEffect> effects) {
        public Transition(Model model, List<VoiceCallEffect> effects) {
            this.model = model;
            this.effects = List.copyOf(effects);
        }
    }

    @NonNull
    public static Transition reduce(@NonNull Model model, @NonNull VoiceCallEvent event, long now) {
        List<VoiceCallEffect> effects = new ArrayList<>();

        if (event instanceof VoiceCallEvent.Stop) {
            if (model.terminal()) {
                return same(model);
            }
            Model next = with(model, VoiceCallState.ENDED, 0f, null, null, 0, false, false);
            effects.add(new VoiceCallEffect.StopCapture());
            effects.add(new VoiceCallEffect.StopPlayback());
            effects.add(new VoiceCallEffect.CloseLink());
            effects.add(new VoiceCallEffect.Publish());
            return new Transition(next, effects);
        }
        if (event instanceof VoiceCallEvent.Failed failed) {
            if (model.terminal()) {
                return same(model);
            }
            Model next = with(model, VoiceCallState.FAILED, 0f, failed.failure(), null, 0, false, false);
            effects.add(new VoiceCallEffect.StopCapture());
            effects.add(new VoiceCallEffect.StopPlayback());
            effects.add(new VoiceCallEffect.CloseLink());
            effects.add(new VoiceCallEffect.Publish());
            return new Transition(next, effects);
        }
        if (model.terminal()) {
            return same(model);
        }

        if (event instanceof VoiceCallEvent.DuplexResolved resolved) {
            if (model.duplex() == resolved.duplex()) {
                return same(model);
            }
            Model next = model.withDuplex(resolved.duplex());
            effects.add(new VoiceCallEffect.Publish());
            next = addGateEffect(model, next, effects);
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.SetDuplex set) {
            if (model.duplex() == set.duplex()) {
                return same(model);
            }
            Model next = model.withDuplex(set.duplex());
            effects.add(new VoiceCallEffect.Publish());
            next = addGateEffect(model, next, effects);
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.Start) {
            Model next = with(model, VoiceCallState.CONNECTING, 0f, null, null, 0, false, false);
            effects.add(new VoiceCallEffect.Publish());
            return new Transition(next, effects);
        }
        if (event instanceof VoiceCallEvent.Opened) {
            if (model.state() != VoiceCallState.CONNECTING) {
                return same(model);
            }
            Model next = with(model, VoiceCallState.LISTENING, 0f, null, null, 0, false, true);
            effects.add(new VoiceCallEffect.StartCapture());
            effects.add(new VoiceCallEffect.SetUplinkEnabled(true));
            effects.add(new VoiceCallEffect.Publish());
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.AssistantText text) {
            String delta = text.delta();
            if (delta == null || delta.isEmpty()) {
                return same(model);
            }
            String turnId = model.assistantTurnId();
            int created = model.turnsCreated();
            List<AiVoiceSnapshot.Turn> turns;
            if (turnId == null) {
                created++;
                turnId = "turn-" + created;
                turns = appendTurn(model.turns(), turnId, delta, now);
            } else {
                turns = appendText(model.turns(), turnId, delta);
            }
            VoiceCallState state = model.assistantActive() ? model.state() : VoiceCallState.THINKING;
            Model next = new Model(state, turns, model.level(), model.failure(), turnId,
                    model.audioBytes(), model.muted(), model.playbackStarted(), created,
                    model.duplex(), model.uplinkEnabled());
            effects.add(new VoiceCallEffect.Publish());
            next = addGateEffect(model, next, effects);
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.AssistantAudio audio) {
            if (audio.pcm16().length == 0) {
                return same(model);
            }
            if (!model.playbackStarted()) {
                effects.add(new VoiceCallEffect.StartPlayback(audio.sampleRateHz()));
            }
            effects.add(new VoiceCallEffect.PlayAudio(
                    audio.pcm16(), audio.itemId(), audio.contentIndex()));
            VoiceCallState state = model.state() == VoiceCallState.DRAINING
                    ? VoiceCallState.DRAINING : VoiceCallState.SPEAKING;
            Model next = new Model(state, model.turns(), model.level(), model.failure(),
                    model.assistantTurnId(), model.audioBytes() + audio.pcm16().length,
                    model.muted(), true, model.turnsCreated(), model.duplex(),
                    model.uplinkEnabled());
            if (state != model.state()) {
                effects.add(new VoiceCallEffect.Publish());
            }
            next = addGateEffect(model, next, effects);
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.ResponseDone) {
            List<AiVoiceSnapshot.Turn> turns = finalizeTurn(model.turns(), model.assistantTurnId());
            if (model.audioBytes() > 0) {
                long playbackMillis = model.audioBytes() / OUTPUT_BYTES_PER_MS;
                Model next = new Model(VoiceCallState.DRAINING, turns, 0f, model.failure(), null,
                        model.audioBytes(), model.muted(), model.playbackStarted(),
                        model.turnsCreated(), model.duplex(), model.uplinkEnabled());
                effects.add(new VoiceCallEffect.ScheduleDrain(playbackMillis + DRAIN_TAIL_MS));
                effects.add(new VoiceCallEffect.Publish());
                return new Transition(next, effects);
            }
            Model next = with(model, VoiceCallState.LISTENING, 0f, null, null, 0,
                    model.playbackStarted(), true);
            next = new Model(next.state(), turns, next.level(), next.failure(),
                    next.assistantTurnId(), next.audioBytes(), next.muted(),
                    next.playbackStarted(), next.turnsCreated(), next.duplex(),
                    next.uplinkEnabled());
            effects.add(new VoiceCallEffect.Publish());
            next = addGateEffect(model, next, effects);
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.PlaybackDrained) {
            if (model.state() != VoiceCallState.DRAINING) {
                return same(model);
            }
            Model next = with(model, VoiceCallState.LISTENING, 0f, null, null, 0,
                    model.playbackStarted(), true);
            effects.add(new VoiceCallEffect.Publish());
            next = addGateEffect(model, next, effects);
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.CaptureFrame frame) {
            if (!model.uplinkEnabled()) {
                // 闸门关着：既不上行，也不让电平动。半双工下光晕应该显示"它在说话"，
                // 而不是跟着回声一起跳。
                return same(model);
            }
            if (model.state() == VoiceCallState.LISTENING || model.state() == VoiceCallState.MUTED) {
                float level = Math.max(0f, Math.min(1f, frame.level()));
                if (Math.abs(level - model.level()) >= 0.01f) {
                    Model next = new Model(model.state(), model.turns(), level, model.failure(),
                            model.assistantTurnId(), model.audioBytes(), model.muted(),
                            model.playbackStarted(), model.turnsCreated(), model.duplex(),
                            model.uplinkEnabled());
                    effects.add(new VoiceCallEffect.Publish());
                    return new Transition(next, effects);
                }
            }
            return same(model);
        }

        if (event instanceof VoiceCallEvent.SpeechStarted) {
            if (!model.assistantActive()) {
                return same(model);
            }
            // 助手正在发声却被判成"用户开口"：只有全双工才可能是真的插话。
            // 半双工下上行本来是关的，这条事件只可能来自服务端对残余音频的判定，忽略。
            if (!model.duplex().streamsWhileAssistantSpeaks()) {
                return same(model);
            }
            return stopAssistant(model, effects, now);
        }

        if (event instanceof VoiceCallEvent.SpeechStopped) {
            if (model.state() != VoiceCallState.LISTENING) {
                return same(model);
            }
            Model next = new Model(VoiceCallState.THINKING, model.turns(), 0f, model.failure(),
                    model.assistantTurnId(), model.audioBytes(), model.muted(),
                    model.playbackStarted(), model.turnsCreated(), model.duplex(),
                    model.uplinkEnabled());
            effects.add(new VoiceCallEffect.Publish());
            next = addGateEffect(model, next, effects);
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.UserTranscript user) {
            String text = user.transcript().trim();
            if (text.isEmpty()) {
                return same(model);
            }
            int created = model.turnsCreated() + 1;
            String turnId = user.itemId() != null && !user.itemId().isBlank()
                    ? user.itemId()
                    : "user-" + created;
            List<AiVoiceSnapshot.Turn> turns = appendUserTurn(model.turns(), turnId, text, now);
            Model next = new Model(model.state(), turns, model.level(), model.failure(),
                    model.assistantTurnId(), model.audioBytes(), model.muted(),
                    model.playbackStarted(), created, model.duplex(),
                    model.uplinkEnabled());
            effects.add(new VoiceCallEffect.Publish());
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.Muted muted) {
            VoiceCallState state = model.state();
            if (state == VoiceCallState.LISTENING || state == VoiceCallState.MUTED) {
                state = muted.muted() ? VoiceCallState.MUTED : VoiceCallState.LISTENING;
            }
            Model next = new Model(state, model.turns(), model.level(), model.failure(),
                    model.assistantTurnId(), model.audioBytes(), muted.muted(),
                    model.playbackStarted(), model.turnsCreated(), model.duplex(),
                    model.uplinkEnabled());
            if (state != model.state() || muted.muted() != model.muted()) {
                effects.add(new VoiceCallEffect.Publish());
            }
            next = addGateEffect(model, next, effects);
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.Interrupt) {
            if (!model.assistantActive()) {
                return same(model);
            }
            return stopAssistant(model, effects, now);
        }

        return same(model);
    }

    /**
     * 用户打断（或全双工下 VAD 判定插话）的统一出口。
     *
     * <p>顺序不能变：先停播并取走"已听到"的进度，再让服务端取消/截断，最后回聆听。
     * {@code cancelGeneration} 只在真的还有生成在跑时为 true——通义对没有活动响应的
     * {@code response.cancel} 会回错误事件。</p>
     */
    @NonNull
    private static Transition stopAssistant(@NonNull Model model,
                                            @NonNull List<VoiceCallEffect> effects, long now) {
        boolean cancelGeneration = model.generating();
        effects.add(new VoiceCallEffect.StopAssistant(cancelGeneration));
        Model next = new Model(VoiceCallState.LISTENING,
                finalizeTurn(model.turns(), model.assistantTurnId()), 0f, model.failure(),
                null, 0, model.muted(), model.playbackStarted(), model.turnsCreated(),
                model.duplex(), true);
        effects.add(new VoiceCallEffect.Publish());
        next = addGateEffect(model, next, effects);
        return new Transition(next, effects);
    }

    /**
     * 上行闸门只在"应该开/应该关"真的变了的时候发一条 effect。
     * 每帧都发会把状态机线程和采集线程刷成热点，而且没有意义。
     */
    private static Model addGateEffect(@NonNull Model before, @NonNull Model after,
                                      @NonNull List<VoiceCallEffect> effects) {
        Model settled = withGate(after);
        if (settled.uplinkEnabled() != before.uplinkEnabled()) {
            effects.add(new VoiceCallEffect.SetUplinkEnabled(settled.uplinkEnabled()));
        }
        return settled;
    }

    /** 把"应该开/应该关"落到模型上，供下一次转移比较。 */
    @NonNull
    private static Model withGate(@NonNull Model model) {
        boolean wanted = uplinkWanted(model);
        if (wanted == model.uplinkEnabled()) {
            return model;
        }
        return new Model(model.state(), model.turns(), model.level(), model.failure(),
                model.assistantTurnId(), model.audioBytes(), model.muted(), model.playbackStarted(),
                model.turnsCreated(), model.duplex(), wanted);
    }

    /**
     * 该不该让麦克风上行。
     *
     * <p>规则只有三条：静音时不开；半双工下助手发声时不开；其余情况开。
     * 全双工在助手发声时也开——那是它的定义，前提是设备侧回声消除生效。</p>
     */
    public static boolean uplinkWanted(@NonNull Model model) {
        if (model.muted()) {
            return false;
        }
        if (model.assistantActive() && !model.duplex().streamsWhileAssistantSpeaks()) {
            return false;
        }
        return true;
    }

    @NonNull
    private static Model with(@NonNull Model base, @NonNull VoiceCallState state, float level,
                              @Nullable AiFailure failure, @Nullable String assistantTurnId,
                              long audioBytes, boolean playbackStarted, boolean uplinkEnabled) {
        return new Model(state, finalizeTurn(base.turns(), base.assistantTurnId()), level,
                failure, assistantTurnId, audioBytes, base.muted(), playbackStarted,
                base.turnsCreated(), base.duplex(), uplinkEnabled);
    }

    @NonNull
    private static Transition same(@NonNull Model model) {
        return new Transition(model, List.of());
    }

    @NonNull
    private static List<AiVoiceSnapshot.Turn> appendTurn(
            @NonNull List<AiVoiceSnapshot.Turn> turns, @NonNull String id,
            @NonNull String text, long now) {
        List<AiVoiceSnapshot.Turn> next = new ArrayList<>(turns);
        next.add(new AiVoiceSnapshot.Turn(id, false, text, false, now));
        return next;
    }

    @NonNull
    private static List<AiVoiceSnapshot.Turn> appendUserTurn(
            @NonNull List<AiVoiceSnapshot.Turn> turns, @NonNull String id,
            @NonNull String text, long now) {
        List<AiVoiceSnapshot.Turn> next = new ArrayList<>(turns);
        for (int i = 0; i < next.size(); i++) {
            AiVoiceSnapshot.Turn existing = next.get(i);
            if (existing.id().equals(id)) {
                next.set(i, new AiVoiceSnapshot.Turn(id, true, text, true, existing.atMillis()));
                return next;
            }
        }
        next.add(new AiVoiceSnapshot.Turn(id, true, text, true, now));
        return next;
    }

    @NonNull
    private static List<AiVoiceSnapshot.Turn> appendText(
            @NonNull List<AiVoiceSnapshot.Turn> turns, @NonNull String id, @NonNull String delta) {
        List<AiVoiceSnapshot.Turn> next = new ArrayList<>(turns);
        for (int i = 0; i < next.size(); i++) {
            AiVoiceSnapshot.Turn turn = next.get(i);
            if (turn.id().equals(id)) {
                next.set(i, new AiVoiceSnapshot.Turn(id, turn.user(), turn.text() + delta,
                        turn.finalTranscript(), turn.atMillis()));
                return next;
            }
        }
        return turns;
    }

    @NonNull
    private static List<AiVoiceSnapshot.Turn> finalizeTurn(
            @NonNull List<AiVoiceSnapshot.Turn> turns, @Nullable String id) {
        if (id == null) {
            return turns;
        }
        List<AiVoiceSnapshot.Turn> next = new ArrayList<>(turns);
        for (int i = 0; i < next.size(); i++) {
            AiVoiceSnapshot.Turn turn = next.get(i);
            if (turn.id().equals(id) && !turn.finalTranscript()) {
                next.set(i, new AiVoiceSnapshot.Turn(id, turn.user(), turn.text(), true,
                        turn.atMillis()));
                return next;
            }
        }
        return turns;
    }
}
