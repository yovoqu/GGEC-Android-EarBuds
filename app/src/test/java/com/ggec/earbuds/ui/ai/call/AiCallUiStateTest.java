package com.ggec.earbuds.ui.ai.call;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.ui.ai.common.AiMessages;

import org.junit.Test;

import java.util.List;

/**
 * {@link AiCallUiState} 直接映射单测（替代已消除的冗余 AiCallUiMapper）。
 */
public class AiCallUiStateTest {

    @Test
    public void connectingAndPermissionDeniedProvideSafeInitialStates() {
        AiCallUiState connecting = AiCallUiState.connecting(VoiceDuplex.FULL_DUPLEX);
        assertEquals(AiCallUiState.Phase.CONNECTING, connecting.phase());
        assertEquals(R.string.ai_call_phase_connecting, connecting.title());
        assertEquals(VoiceDuplex.FULL_DUPLEX, connecting.duplex());
        assertTrue(connecting.speakerOn());
        assertTrue(connecting.captionsVisible());
        assertNull(connecting.activeSource());
        assertTrue(connecting.availableSources().isEmpty());

        AiCallUiState denied = AiCallUiState.permissionDenied();
        assertEquals(AiCallUiState.Phase.OFFLINE, denied.phase());
        assertEquals(R.string.ai_call_no_permission, denied.title());
        assertFalse(denied.live());
    }

    @Test
    public void mapsListeningWithAudioSourcesAndMicMute() {
        AiAudioSource.Descriptor mic = new AiAudioSource.Descriptor(
                "builtin:1", AiAudioSource.Kind.BUILTIN_MIC, "手机麦克风", 16_000);
        AiAudioSource.Descriptor ble = new AiAudioSource.Descriptor(
                "ble:1", AiAudioSource.Kind.BLUETOOTH_BLE, "GGEC Earbuds", 48_000);
        AiVoiceSnapshot snapshot = new AiVoiceSnapshot(
                AiVoiceSnapshot.State.LISTENING,
                List.of(),
                0.4f,
                null,
                VoiceDuplex.FULL_DUPLEX,
                true,
                ble,
                List.of(ble, mic)
        );

        AiCallUiState state = AiCallUiState.fromSnapshot(snapshot);
        assertEquals(AiCallUiState.Phase.LISTENING, state.phase());
        assertEquals(R.string.ai_call_phase_listening, state.title());
        assertTrue(state.live());
        assertFalse(state.interruptible());
        assertTrue(state.micMuted());
        assertFalse(state.ended());
        assertEquals(VoiceDuplex.FULL_DUPLEX, state.duplex());
        assertEquals(ble, state.activeSource());
        assertEquals(List.of(ble, mic), state.availableSources());
    }

    @Test
    public void mapsSpeakingAndThinkingAsInterruptible() {
        AiCallUiState thinking = AiCallUiState.fromSnapshot(new AiVoiceSnapshot(
                AiVoiceSnapshot.State.THINKING, List.of(), 0f, null,
                VoiceDuplex.HALF_DUPLEX, false, null, List.of()));
        assertEquals(AiCallUiState.Phase.THINKING, thinking.phase());
        assertTrue(thinking.live());
        assertTrue(thinking.interruptible());

        AiCallUiState speaking = AiCallUiState.fromSnapshot(new AiVoiceSnapshot(
                AiVoiceSnapshot.State.SPEAKING, List.of(), 0f, null,
                VoiceDuplex.HALF_DUPLEX, false, null, List.of()));
        assertEquals(AiCallUiState.Phase.SPEAKING, speaking.phase());
        assertTrue(speaking.live());
        assertTrue(speaking.interruptible());
    }

    @Test
    public void mapsFailedRetryableAndAuthActionsAndEnded() {
        AiCallUiState retryable = AiCallUiState.fromSnapshot(new AiVoiceSnapshot(
                AiVoiceSnapshot.State.FAILED, List.of(), 0f,
                new AiFailure(AiFailureKind.NETWORK, "dropped"),
                VoiceDuplex.HALF_DUPLEX, false, null, List.of()));
        assertEquals(AiCallUiState.Phase.OFFLINE, retryable.phase());
        assertEquals(AiCallUiState.Action.RETRY, retryable.action());
        assertEquals(Integer.valueOf(AiMessages.failure(AiFailureKind.NETWORK)), retryable.reason());
        assertTrue(retryable.ended());
        assertFalse(retryable.live());

        AiCallUiState auth = AiCallUiState.fromSnapshot(new AiVoiceSnapshot(
                AiVoiceSnapshot.State.FAILED, List.of(), 0f,
                new AiFailure(AiFailureKind.AUTHENTICATION, "bad key"),
                VoiceDuplex.HALF_DUPLEX, false, null, List.of()));
        assertEquals(AiCallUiState.Action.SETTINGS, auth.action());
        assertEquals(Integer.valueOf(AiMessages.failure(AiFailureKind.AUTHENTICATION)), auth.reason());

        AiCallUiState ended = AiCallUiState.fromSnapshot(new AiVoiceSnapshot(
                AiVoiceSnapshot.State.ENDED, List.of(), 0f, null,
                VoiceDuplex.HALF_DUPLEX, false, null, List.of()));
        assertEquals(AiCallUiState.Phase.OFFLINE, ended.phase());
        assertEquals(AiCallUiState.Action.NONE, ended.action());
        assertTrue(ended.ended());
        assertFalse(ended.live());
    }

    @Test
    public void formatsDurationAndAppliesWithersImmutably() {
        AiAudioSource.Descriptor mic = new AiAudioSource.Descriptor(
                "builtin:1", AiAudioSource.Kind.BUILTIN_MIC, "手机麦", 16_000);
        AiCallUiState base = AiCallUiState.connecting()
                .withDurationSeconds(125)
                .withSpeakerOn(false)
                .withCaptionsVisible(false)
                .withMicMuted(true)
                .withDuplex(VoiceDuplex.FULL_DUPLEX)
                .withSources(mic, List.of(mic));

        assertEquals("02:05", base.formattedDuration());
        assertFalse(base.speakerOn());
        assertFalse(base.captionsVisible());
        assertTrue(base.micMuted());
        assertEquals(VoiceDuplex.FULL_DUPLEX, base.duplex());
        assertEquals(mic, base.activeSource());
        assertEquals(1, base.availableSources().size());
    }
}
