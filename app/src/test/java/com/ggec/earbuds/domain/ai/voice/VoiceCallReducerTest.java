package com.ggec.earbuds.domain.ai.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VoiceCallReducerTest {

    private static final String ITEM = "item-1";

    private VoiceCallReducer.Model model =
            VoiceCallReducer.Model.initial(VoiceDuplex.HALF_DUPLEX);

    private VoiceCallReducer.Transition send(VoiceCallEvent event, long now) {
        VoiceCallReducer.Transition transition = VoiceCallReducer.reduce(model, event, now);
        model = transition.model();
        return transition;
    }

    private boolean has(VoiceCallReducer.Transition transition, Class<?> type) {
        return transition.effects().stream().anyMatch(type::isInstance);
    }

    @Test
    public void userTranscriptAppendsUserTurn() {
        send(new VoiceCallEvent.Start(), 1000);
        send(new VoiceCallEvent.Opened(), 1000);
        send(new VoiceCallEvent.UserTranscript("今天天气如何", "user-item-1"), 1050);

        assertEquals(1, model.turns().size());
        AiVoiceSnapshot.Turn turn = model.turns().get(0);
        assertTrue(turn.user());
        assertEquals("今天天气如何", turn.text());
        assertTrue(turn.finalTranscript());

        send(new VoiceCallEvent.AssistantText("今天晴空万里"), 1100);
        assertEquals(2, model.turns().size());
        assertFalse(model.turns().get(1).user());
        assertEquals("今天晴空万里", model.turns().get(1).text());
    }

    @Test
    public void responseDoneWithAudioGoesThroughDrainingBeforeListening() {
        send(new VoiceCallEvent.Start(), 1000);
        send(new VoiceCallEvent.Opened(), 1000);
        send(new VoiceCallEvent.AssistantText("你好"), 1100);
        VoiceCallReducer.Transition audio = send(new VoiceCallEvent.AssistantAudio(
                new byte[24_000], 24_000, ITEM, 0), 1200);

        assertEquals(VoiceCallState.SPEAKING, model.state());
        assertTrue(has(audio, VoiceCallEffect.StartPlayback.class));
        assertTrue(has(audio, VoiceCallEffect.PlayAudio.class));

        VoiceCallReducer.Transition done = send(new VoiceCallEvent.ResponseDone(), 1300);
        assertEquals(VoiceCallState.DRAINING, model.state());
        assertTrue(has(done, VoiceCallEffect.ScheduleDrain.class));

        send(new VoiceCallEvent.PlaybackDrained(), 1930);
        assertEquals(VoiceCallState.LISTENING, model.state());
        assertEquals(0, model.audioBytes());
    }

    @Test
    public void responseDoneWithoutAudioGoesStraightToListening() {
        send(new VoiceCallEvent.Start(), 1000);
        send(new VoiceCallEvent.Opened(), 1000);
        send(new VoiceCallEvent.AssistantText("只返回文字"), 1100);
        send(new VoiceCallEvent.ResponseDone(), 1200);
        assertEquals(VoiceCallState.LISTENING, model.state());
        assertTrue(model.turns().get(0).finalTranscript());
    }

    @Test
    public void halfDuplexClosesUplinkWhileAssistantIsActive() {
        send(new VoiceCallEvent.Start(), 1000);
        VoiceCallReducer.Transition opened = send(new VoiceCallEvent.Opened(), 1000);
        assertTrue("聆听时必须开上行", model.uplinkEnabled());
        assertTrue(has(opened, VoiceCallEffect.SetUplinkEnabled.class));

        send(new VoiceCallEvent.AssistantText("说"), 1100);
        assertFalse("半双工：思考/说话期间必须关上行", model.uplinkEnabled());

        send(new VoiceCallEvent.AssistantAudio(new byte[480], 24_000, ITEM, 0), 1200);
        assertFalse(model.uplinkEnabled());

        send(new VoiceCallEvent.ResponseDone(), 1300);
        send(new VoiceCallEvent.PlaybackDrained(), 2000);
        assertTrue("播完回到聆听才重新开麦", model.uplinkEnabled());
    }

    @Test
    public void halfDuplexIgnoresSpeechStartedWhileAssistantIsSpeaking() {
        send(new VoiceCallEvent.Start(), 1000);
        send(new VoiceCallEvent.Opened(), 1000);
        send(new VoiceCallEvent.AssistantText("说"), 1100);
        send(new VoiceCallEvent.AssistantAudio(new byte[480], 24_000, ITEM, 0), 1200);

        VoiceCallReducer.Transition echo = send(new VoiceCallEvent.SpeechStarted(), 1300);

        assertEquals("回声称不上插话", VoiceCallState.SPEAKING, model.state());
        assertTrue("不许因为回声中止当前回答", echo.effects().isEmpty());
    }

    @Test
    public void fullDuplexBargeInStopsAssistantAndSkipsCancelWhenGeneratingFinished() {
        model = VoiceCallReducer.Model.initial(VoiceDuplex.FULL_DUPLEX);
        send(new VoiceCallEvent.Start(), 1000);
        send(new VoiceCallEvent.Opened(), 1000);
        send(new VoiceCallEvent.AssistantAudio(new byte[480], 24_000, ITEM, 0), 1200);
        assertTrue("全双工播放期间上行保持开启", model.uplinkEnabled());

        VoiceCallReducer.Transition barge = send(new VoiceCallEvent.SpeechStarted(), 1300);

        assertEquals(VoiceCallState.LISTENING, model.state());
        assertTrue(has(barge, VoiceCallEffect.StopAssistant.class));
        // 还在 SPEAKING（生成未结束）→ 需要 cancel。
        assertTrue(stopAssistantCancels(barge));
    }

    @Test
    public void interruptWhileDrainingDoesNotCancelGeneration() {
        send(new VoiceCallEvent.Start(), 1000);
        send(new VoiceCallEvent.Opened(), 1000);
        send(new VoiceCallEvent.AssistantAudio(new byte[480], 24_000, ITEM, 0), 1200);
        send(new VoiceCallEvent.ResponseDone(), 1300);
        assertEquals(VoiceCallState.DRAINING, model.state());

        VoiceCallReducer.Transition interrupted = send(new VoiceCallEvent.Interrupt(), 1400);

        assertEquals(VoiceCallState.LISTENING, model.state());
        assertTrue(has(interrupted, VoiceCallEffect.StopAssistant.class));
        assertFalse("生成已经结束，通义对空 cancel 会回错误事件",
                stopAssistantCancels(interrupted));
    }

    @Test
    public void speechStartedWhileListeningDoesNotStopAssistant() {
        send(new VoiceCallEvent.Start(), 1000);
        send(new VoiceCallEvent.Opened(), 1000);
        VoiceCallReducer.Transition transition = send(new VoiceCallEvent.SpeechStarted(), 1100);
        assertEquals(VoiceCallState.LISTENING, model.state());
        assertFalse(has(transition, VoiceCallEffect.StopAssistant.class));
    }

    @Test
    public void mutedClosesUplinkAndReturnsWhenUnmuted() {
        send(new VoiceCallEvent.Start(), 1000);
        send(new VoiceCallEvent.Opened(), 1000);
        VoiceCallReducer.Transition muted = send(new VoiceCallEvent.SetMicMuted(true), 1100);
        assertEquals(VoiceCallState.LISTENING, model.state());
        assertTrue(model.micMuted());
        assertFalse(model.uplinkEnabled());
        assertTrue(has(muted, VoiceCallEffect.SetUplinkEnabled.class));

        send(new VoiceCallEvent.SetMicMuted(false), 1200);
        assertEquals(VoiceCallState.LISTENING, model.state());
        assertFalse(model.micMuted());
        assertTrue(model.uplinkEnabled());
    }

    @Test
    public void mutedWhileSpeakingDoesNotAffectSpeakingStateAndPlayback() {
        send(new VoiceCallEvent.Start(), 1000);
        send(new VoiceCallEvent.Opened(), 1000);
        send(new VoiceCallEvent.AssistantAudio(new byte[480], 24_000, ITEM, 0), 1100);
        assertEquals(VoiceCallState.SPEAKING, model.state());

        VoiceCallReducer.Transition muted = send(new VoiceCallEvent.SetMicMuted(true), 1200);
        assertEquals("AI说话期间静音麦克风，AI对话状态仍然是SPEAKING", VoiceCallState.SPEAKING, model.state());
        assertTrue(model.micMuted());
        assertFalse(model.uplinkEnabled());

        // AI回答结束
        send(new VoiceCallEvent.ResponseDone(), 1300);
        assertEquals(VoiceCallState.DRAINING, model.state());
        send(new VoiceCallEvent.PlaybackDrained(), 1400);
        assertEquals(VoiceCallState.LISTENING, model.state());
        assertFalse("由于麦克风仍处于静音状态，回到LISTENING后上行门禁依然关闭", model.uplinkEnabled());
    }

    @Test
    public void stopIsTerminalAndIdempotent() {
        send(new VoiceCallEvent.Start(), 1000);
        VoiceCallReducer.Transition stop = send(new VoiceCallEvent.Stop(), 1100);
        assertEquals(VoiceCallState.ENDED, model.state());
        assertTrue(has(stop, VoiceCallEffect.StopCapture.class));
        VoiceCallReducer.Transition again = send(new VoiceCallEvent.Start(), 1200);
        assertEquals(VoiceCallState.ENDED, model.state());
        assertTrue(again.effects().isEmpty());
    }

    @Test
    public void lateEventsAfterTerminalAreIgnored() {
        send(new VoiceCallEvent.Start(), 1000);
        send(new VoiceCallEvent.Stop(), 1100);
        VoiceCallReducer.Transition late = send(new VoiceCallEvent.AssistantAudio(
                new byte[480], 24_000, ITEM, 0), 1200);
        assertTrue("终态后任何事件都不产生副作用", late.effects().isEmpty());
        assertEquals(VoiceCallState.ENDED, model.state());
    }

    @Test
    public void duplexResolvedUpgradesUplinkOnlyWhenFullDuplex() {
        send(new VoiceCallEvent.Start(), 1000);
        send(new VoiceCallEvent.Opened(), 1000);
        send(new VoiceCallEvent.AssistantAudio(new byte[480], 24_000, ITEM, 0), 1200);
        assertFalse("半双工默认关闸", model.uplinkEnabled());

        VoiceCallReducer.Transition upgraded =
                send(new VoiceCallEvent.DuplexResolved(VoiceDuplex.FULL_DUPLEX), 1300);

        assertTrue("升级为全双工后播放期间也应开闸", model.uplinkEnabled());
        assertTrue(has(upgraded, VoiceCallEffect.SetUplinkEnabled.class));
    }

    @Test
    public void setDuplexSwitchesModeAndUpdatesUplinkDynamically() {
        send(new VoiceCallEvent.Start(), 1000);
        send(new VoiceCallEvent.Opened(), 1000);
        send(new VoiceCallEvent.AssistantAudio(new byte[480], 24_000, ITEM, 0), 1200);
        assertFalse("半双工播放中上行关闭", model.uplinkEnabled());

        VoiceCallReducer.Transition toFull =
                send(new VoiceCallEvent.SetDuplex(VoiceDuplex.FULL_DUPLEX), 1300);
        assertEquals(VoiceDuplex.FULL_DUPLEX, model.duplex());
        assertTrue("切换到全双工后上行应开启", model.uplinkEnabled());
        assertTrue(has(toFull, VoiceCallEffect.SetUplinkEnabled.class));
        assertTrue(has(toFull, VoiceCallEffect.Publish.class));

        VoiceCallReducer.Transition toHalf =
                send(new VoiceCallEvent.SetDuplex(VoiceDuplex.HALF_DUPLEX), 1400);
        assertEquals(VoiceDuplex.HALF_DUPLEX, model.duplex());
        assertFalse("切换回半双工且在播放中上行应关闭", model.uplinkEnabled());
        assertTrue(has(toHalf, VoiceCallEffect.SetUplinkEnabled.class));
        assertTrue(has(toHalf, VoiceCallEffect.Publish.class));
    }

    private static boolean stopAssistantCancels(VoiceCallReducer.Transition transition) {
        return transition.effects().stream()
                .filter(VoiceCallEffect.StopAssistant.class::isInstance)
                .map(VoiceCallEffect.StopAssistant.class::cast)
                .anyMatch(VoiceCallEffect.StopAssistant::cancelGeneration);
    }
}
