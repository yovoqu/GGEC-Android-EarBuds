package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 纯状态机：输入 {@code (Model, Event, now)}，输出 {@code (Model, Effects)}。
 *
 * <p>不碰线程、不碰音频硬件、不碰网络；所有副作用都收敛为 {@link VoiceCallEffect} 数据。
 * 每条转移都能单元测试穷举，真机 trace 也能直接回放。</p>
 */
public final class VoiceCallReducer {

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
            long audioMicros,
            int playbackSampleRateHz,
            boolean micMuted,
            boolean playbackStarted,
            int turnsCreated,
            @NonNull VoiceDuplex duplex,
            boolean uplinkEnabled,
            @Nullable AiAudioSource.Descriptor activeSource,
            @NonNull List<AiAudioSource.Descriptor> availableSources) {

        public Model {
            turns = List.copyOf(turns);
            level = Math.max(0f, Math.min(1f, level));
            duplex = Objects.requireNonNull(duplex, "duplex");
            availableSources = List.copyOf(availableSources);
        }

        @NonNull
        public VoiceDuplexStrategy duplexStrategy() {
            return duplex.strategy();
        }

        @NonNull
        public static Model initial(@NonNull VoiceDuplex duplex) {
            return new Model(VoiceCallState.IDLE, List.of(), 0f, null, null, 0L, 0,
                    false, false, 0, duplex, false, null, List.of());
        }

        public boolean terminal() {
            return state == VoiceCallState.FAILED || state == VoiceCallState.ENDED;
        }

        /** 助手正在发声（含“生成完但还没播完”）。 */
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
            return new Model(state, turns, level, failure, assistantTurnId, audioMicros,
                    playbackSampleRateHz, micMuted, playbackStarted, turnsCreated,
                    nextDuplex, uplinkEnabled, activeSource, availableSources);
        }

        @NonNull
        public Model withActiveSource(@Nullable AiAudioSource.Descriptor nextSource) {
            return new Model(state, turns, level, failure, assistantTurnId, audioMicros,
                    playbackSampleRateHz, micMuted, playbackStarted, turnsCreated,
                    duplex, uplinkEnabled, nextSource, availableSources);
        }

        @NonNull
        public Model withAvailableSources(@NonNull List<AiAudioSource.Descriptor> nextSources) {
            return new Model(state, turns, level, failure, assistantTurnId, audioMicros,
                    playbackSampleRateHz, micMuted, playbackStarted, turnsCreated,
                    duplex, uplinkEnabled, activeSource, nextSources);
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
            Model next = with(model, VoiceCallState.ENDED, 0f, null, null, 0L, 0, false, false);
            effects.add(new VoiceCallEffect.StopUplink());
            effects.add(new VoiceCallEffect.StopPlayback());
            effects.add(new VoiceCallEffect.CloseLink());
            effects.add(new VoiceCallEffect.Publish());
            return new Transition(next, effects);
        }
        if (event instanceof VoiceCallEvent.Failed failed) {
            if (model.terminal()) {
                return same(model);
            }
            Model next = with(model, VoiceCallState.FAILED, 0f, failed.failure(), null, 0L, 0, false, false);
            effects.add(new VoiceCallEffect.StopUplink());
            effects.add(new VoiceCallEffect.StopPlayback());
            effects.add(new VoiceCallEffect.CloseLink());
            effects.add(new VoiceCallEffect.Publish());
            return new Transition(next, effects);
        }
        if (model.terminal()) {
            return same(model);
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

        if (event instanceof VoiceCallEvent.SetActiveSource setSource) {
            if (Objects.equals(model.activeSource(), setSource.source())) {
                return same(model);
            }
            Model next = model.withActiveSource(setSource.source());
            effects.add(new VoiceCallEffect.Publish());
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.AvailableSourcesChanged changed) {
            if (Objects.equals(model.availableSources(), changed.sources())) {
                return same(model);
            }
            Model next = model.withAvailableSources(changed.sources());
            effects.add(new VoiceCallEffect.Publish());
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.Start) {
            if (model.assistantActive() || model.playbackStarted()) {
                // 这一路真的还有助手发言/播放没结束，才需要收尾：停播 + 让服务端停止生成。
                // 全新会话时链路还没开，不能凭空发 clear/cancel。
                effects.add(new VoiceCallEffect.StopAssistant(model.generating()));
            }
            effects.add(new VoiceCallEffect.StopPlayback());
            Model next = with(model, VoiceCallState.CONNECTING, 0f, null, null, 0L, 0, false, false);
            next = addGateEffect(model, next, effects);
            effects.add(new VoiceCallEffect.Publish());
            return new Transition(next, effects);
        }
        if (event instanceof VoiceCallEvent.Opened opened) {
            if (model.state() != VoiceCallState.CONNECTING) {
                return same(model);
            }
            Model next = with(model, VoiceCallState.LISTENING, 0f, null, null, 0L, 0,
                    false, model.uplinkEnabled());
            effects.add(new VoiceCallEffect.StartUplink(opened.inputWireRateHz()));
            next = addGateEffect(model, next, effects);
            effects.add(new VoiceCallEffect.Publish());
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.AssistantText text) {
            if (linkNotOpen(model)) {
                // 链路还没 Opened：这段增量属于协议乱序，不进状态机。
                return same(model);
            }
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
                Optional<List<AiVoiceSnapshot.Turn>> appended =
                        appendText(model.turns(), turnId, delta);
                if (appended.isEmpty()) {
                    // 找不到对应回合：宁可显式失败，也不能把这段文字悄悄丢掉。
                    return fail(model, AiFailureKind.PROTOCOL);
                }
                turns = appended.get();
            }
            VoiceCallState state = model.assistantActive() ? model.state() : VoiceCallState.THINKING;
            Model next = new Model(state, turns, model.level(), model.failure(), turnId,
                    model.audioMicros(), model.playbackSampleRateHz(), model.micMuted(),
                    model.playbackStarted(), created, model.duplex(), model.uplinkEnabled(),
                    model.activeSource(), model.availableSources());
            effects.add(new VoiceCallEffect.Publish());
            next = addGateEffect(model, next, effects);
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.AssistantAudio audio) {
            if (linkNotOpen(model)) {
                return same(model);
            }
            if (audio.pcm16().length == 0 || audio.sampleRateHz() <= 0) {
                return same(model);
            }
            int rate = audio.sampleRateHz();
            if (!model.playbackStarted() || model.playbackSampleRateHz() != rate) {
                effects.add(new VoiceCallEffect.StartPlayback(rate));
            }
            effects.add(new VoiceCallEffect.PlayAudio(
                    audio.pcm16(), audio.itemId(), audio.contentIndex()));
            long samples = audio.pcm16().length / 2L;
            long chunkMicros = samples * 1_000_000L / rate;
            VoiceCallState state = model.state() == VoiceCallState.DRAINING
                    ? VoiceCallState.DRAINING : VoiceCallState.SPEAKING;
            Model next = new Model(state, model.turns(), model.level(), model.failure(),
                    model.assistantTurnId(), model.audioMicros() + chunkMicros, rate,
                    model.micMuted(), true, model.turnsCreated(), model.duplex(),
                    model.uplinkEnabled(), model.activeSource(), model.availableSources());
            if (state != model.state()) {
                effects.add(new VoiceCallEffect.Publish());
            }
            next = addGateEffect(model, next, effects);
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.ResponseDone) {
            List<AiVoiceSnapshot.Turn> turns = finalizeTurn(model.turns(), model.assistantTurnId());
            if (model.audioMicros() > 0L) {
                long playbackMillis = (model.audioMicros() + 999L) / 1_000L;
                Model next = new Model(VoiceCallState.DRAINING, turns, 0f, model.failure(), null,
                        model.audioMicros(), model.playbackSampleRateHz(), model.micMuted(),
                        model.playbackStarted(), model.turnsCreated(), model.duplex(),
                        model.uplinkEnabled(), model.activeSource(), model.availableSources());
                effects.add(new VoiceCallEffect.ScheduleDrain(playbackMillis + DRAIN_TAIL_MS));
                effects.add(new VoiceCallEffect.Publish());
                return new Transition(next, effects);
            }
            Model next = new Model(VoiceCallState.LISTENING, turns, 0f, null, null, 0L,
                    model.playbackSampleRateHz(), model.micMuted(), model.playbackStarted(),
                    model.turnsCreated(), model.duplex(), model.uplinkEnabled(),
                    model.activeSource(), model.availableSources());
            effects.add(new VoiceCallEffect.Publish());
            next = addGateEffect(model, next, effects);
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.PlaybackDrained) {
            if (model.state() != VoiceCallState.DRAINING) {
                return same(model);
            }
            Model next = with(model, VoiceCallState.LISTENING, 0f, null, null, 0L,
                    model.playbackSampleRateHz(), model.playbackStarted(), model.uplinkEnabled());
            effects.add(new VoiceCallEffect.Publish());
            next = addGateEffect(model, next, effects);
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.CaptureLevel frame) {
            if (!model.uplinkEnabled()) {
                return same(model);
            }
            if (model.state() == VoiceCallState.LISTENING) {
                float level = Math.max(0f, Math.min(1f, frame.level()));
                if (Math.abs(level - model.level()) >= 0.01f) {
                    Model next = new Model(model.state(), model.turns(), level, model.failure(),
                            model.assistantTurnId(), model.audioMicros(),
                            model.playbackSampleRateHz(), model.micMuted(),
                            model.playbackStarted(), model.turnsCreated(), model.duplex(),
                            model.uplinkEnabled(), model.activeSource(), model.availableSources());
                    effects.add(new VoiceCallEffect.Publish());
                    return new Transition(next, effects);
                }
            }
            return same(model);
        }

        if (event instanceof VoiceCallEvent.SpeechStarted) {
            if (!model.duplexStrategy().shouldInterruptOnSpeech(model.assistantActive())) {
                return same(model);
            }
            return stopAssistant(model, effects);
        }

        if (event instanceof VoiceCallEvent.SpeechStopped) {
            if (model.state() != VoiceCallState.LISTENING) {
                return same(model);
            }
            Model next = new Model(VoiceCallState.THINKING, model.turns(), 0f, model.failure(),
                    model.assistantTurnId(), model.audioMicros(), model.playbackSampleRateHz(),
                    model.micMuted(), model.playbackStarted(), model.turnsCreated(),
                    model.duplex(), model.uplinkEnabled(),
                    model.activeSource(), model.availableSources());
            effects.add(new VoiceCallEffect.Publish());
            next = addGateEffect(model, next, effects);
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.Timeout) {
            if (model.state() != VoiceCallState.THINKING) {
                // 超时只对 THINKING 有意义：其它阶段说明这一轮已经推进，这条定时已经过期。
                return same(model);
            }
            // 服务端始终没给回答：把半截生成取消掉、回到聆听，并把分类留在快照里。
            effects.add(new VoiceCallEffect.StopAssistant(model.generating()));
            Model next = with(model, VoiceCallState.LISTENING, 0f,
                    new AiFailure(AiFailureKind.TIMEOUT, null), null, 0L,
                    model.playbackSampleRateHz(), model.playbackStarted(), model.uplinkEnabled());
            next = addGateEffect(model, next, effects);
            effects.add(new VoiceCallEffect.Publish());
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
                    model.assistantTurnId(), model.audioMicros(), model.playbackSampleRateHz(),
                    model.micMuted(), model.playbackStarted(), created, model.duplex(),
                    model.uplinkEnabled(), model.activeSource(), model.availableSources());
            effects.add(new VoiceCallEffect.Publish());
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.SetMicMuted muteEvent) {
            float level = muteEvent.micMuted() ? 0f : model.level();
            Model next = new Model(model.state(), model.turns(), level, model.failure(),
                    model.assistantTurnId(), model.audioMicros(), model.playbackSampleRateHz(),
                    muteEvent.micMuted(), model.playbackStarted(), model.turnsCreated(),
                    model.duplex(), model.uplinkEnabled(),
                    model.activeSource(), model.availableSources());
            if (muteEvent.micMuted() != model.micMuted()) {
                effects.add(new VoiceCallEffect.Publish());
            }
            next = addGateEffect(model, next, effects);
            return new Transition(next, effects);
        }

        if (event instanceof VoiceCallEvent.Interrupt) {
            if (!model.assistantActive()) {
                return same(model);
            }
            return stopAssistant(model, effects);
        }

        return same(model);
    }

    @NonNull
    private static Transition stopAssistant(@NonNull Model model,
                                            @NonNull List<VoiceCallEffect> effects) {
        boolean cancelGeneration = model.generating();
        effects.add(new VoiceCallEffect.StopAssistant(cancelGeneration));
        Model next = new Model(VoiceCallState.LISTENING,
                finalizeTurn(model.turns(), model.assistantTurnId()), 0f, model.failure(),
                null, 0L, model.playbackSampleRateHz(), model.micMuted(),
                model.playbackStarted(), model.turnsCreated(), model.duplex(),
                model.uplinkEnabled(), model.activeSource(), model.availableSources());
        effects.add(new VoiceCallEffect.Publish());
        next = addGateEffect(model, next, effects);
        return new Transition(next, effects);
    }

    private static Model addGateEffect(@NonNull Model before, @NonNull Model after,
                                       @NonNull List<VoiceCallEffect> effects) {
        Model settled = withGate(after);
        if (settled.uplinkEnabled() != before.uplinkEnabled()) {
            effects.add(new VoiceCallEffect.SetUplinkEnabled(settled.uplinkEnabled()));
        }
        return settled;
    }

    @NonNull
    private static Model withGate(@NonNull Model model) {
        boolean wanted = uplinkWanted(model);
        if (wanted == model.uplinkEnabled()) {
            return model;
        }
        return new Model(model.state(), model.turns(), model.level(), model.failure(),
                model.assistantTurnId(), model.audioMicros(), model.playbackSampleRateHz(),
                model.micMuted(), model.playbackStarted(), model.turnsCreated(),
                model.duplex(), wanted, model.activeSource(), model.availableSources());
    }

    /** 该不该允许上行推流。非活跃通话期或本地静音时关闭；其余由双工策略裁决。 */
    public static boolean uplinkWanted(@NonNull Model model) {
        if (model.micMuted()
                || model.terminal()
                || model.state() == VoiceCallState.IDLE
                || model.state() == VoiceCallState.CONNECTING) {
            return false;
        }
        return model.duplexStrategy().allowsUplink(model.assistantActive());
    }

    @NonNull
    private static Model with(@NonNull Model base, @NonNull VoiceCallState state, float level,
                              @Nullable AiFailure failure, @Nullable String assistantTurnId,
                              long audioMicros, int playbackSampleRateHz,
                              boolean playbackStarted, boolean uplinkEnabled) {
        return new Model(state, finalizeTurn(base.turns(), base.assistantTurnId()), level,
                failure, assistantTurnId, audioMicros, playbackSampleRateHz, base.micMuted(),
                playbackStarted, base.turnsCreated(), base.duplex(), uplinkEnabled,
                base.activeSource(), base.availableSources());
    }

    @NonNull
    private static Transition same(@NonNull Model model) {
        return new Transition(model, List.of());
    }

    /** 链路还没 Opened（甚至还没 Start）：此时到达的下行数据一律不进入状态机。 */
    private static boolean linkNotOpen(@NonNull Model model) {
        return model.state() == VoiceCallState.IDLE || model.state() == VoiceCallState.CONNECTING;
    }

    /** 就地判死：状态机自己发现的不一致一律按分类失败收场，不留在中间态里。 */
    @NonNull
    private static Transition fail(@NonNull Model model, @NonNull AiFailureKind kind) {
        List<VoiceCallEffect> effects = new ArrayList<>();
        Model next = with(model, VoiceCallState.FAILED, 0f, new AiFailure(kind, null), null,
                0L, 0, false, false);
        effects.add(new VoiceCallEffect.StopUplink());
        effects.add(new VoiceCallEffect.StopPlayback());
        effects.add(new VoiceCallEffect.CloseLink());
        effects.add(new VoiceCallEffect.Publish());
        return new Transition(next, effects);
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

    /**
     * 把增量拼到指定回合上。
     *
     * <p>找不到回合（或该 id 落在用户发言上）时返回空，由调用方升级成显式失败：
     * 返回原列表冒充成功等于把这段文字静默吞掉。</p>
     */
    @NonNull
    private static Optional<List<AiVoiceSnapshot.Turn>> appendText(
            @NonNull List<AiVoiceSnapshot.Turn> turns, @NonNull String id, @NonNull String delta) {
        List<AiVoiceSnapshot.Turn> next = new ArrayList<>(turns);
        for (int i = 0; i < next.size(); i++) {
            AiVoiceSnapshot.Turn turn = next.get(i);
            if (turn.id().equals(id) && !turn.user()) {
                next.set(i, new AiVoiceSnapshot.Turn(id, false, turn.text() + delta,
                        turn.finalTranscript(), turn.atMillis()));
                return Optional.of(next);
            }
        }
        return Optional.empty();
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
