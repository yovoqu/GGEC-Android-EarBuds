package com.ggec.earbuds.data.ai.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ggec.earbuds.data.ai.json.AiSettingsJson;
import com.ggec.earbuds.data.ai.store.EncryptedSecretStore;
import com.ggec.earbuds.data.ai.store.SecretCipher;
import com.ggec.earbuds.data.store.DocumentFormatException;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelCategory;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.MemoryDocumentStore;
import com.ggec.earbuds.testing.ai.MemorySecretStore;
import com.ggec.earbuds.testing.ai.QueueExecutor;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class AiSettingsRepositoryImplTest {

    private static final String SETTINGS = "settings.json";

    private final MemoryDocumentStore documents = new MemoryDocumentStore();
    private final MemorySecretStore secrets = new MemorySecretStore();

    private AiSettingsRepositoryImpl repository() {
        return new AiSettingsRepositoryImpl(
                documents, secrets, new AiSettingsJson(), Runnable::run);
    }

    private AiSettingsRepositoryImpl queuedRepository(QueueExecutor executor) {
        return new AiSettingsRepositoryImpl(
                documents, secrets, new AiSettingsJson(), executor);
    }

    @Test
    public void startsEmptyWhenNothingWasSaved() {
        assertEquals(AiSettings.EMPTY, repository().load());
        assertEquals(0, documents.writes);
    }

    @Test
    public void updatesPersistAndSurviveARestart() {
        secrets.secrets.put("apikey.deepseek", "sk-one");
        AiSettingsRepositoryImpl first = repository();
        first.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "", "m1", "m2"))
                .withTextSelection(new AiSelection("deepseek", "m2")));

        AiSettings reloaded = repository().load();

        assertTrue(reloaded.platform("deepseek").orElseThrow().configured());
        assertEquals(2, reloaded.platform("deepseek").orElseThrow().models().size());
        assertEquals(new AiSelection("deepseek", "m2"), reloaded.textSelection());
        assertTrue(reloaded.textModel().isPresent());
    }

    @Test
    public void keysLiveOnlyInTheSecretStore() {
        repository().update(s -> s.withPlatform(AiFixtures.platform("deepseek", "", "m1")));
        repository().savePlatform(AiFixtures.platform("deepseek", "", "m1"), "sk-very-secret");

        assertEquals("sk-very-secret", secrets.secrets.get("apikey.deepseek"));
        assertFalse(documents.documents.get(SETTINGS).contains("sk-very-secret"));
    }

    @Test
    public void configuredFlagFollowsTheSecretStore() {
        secrets.secrets.put("apikey.deepseek", "k1");
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "", "m1")));
        assertTrue(repository.load().platform("deepseek").orElseThrow().configured());

        secrets.secrets.remove("apikey.deepseek");
        assertFalse(repository.load().platform("deepseek").orElseThrow().configured());
    }

    @Test
    public void savePlatformWritesTheKeyAndTheDocumentInOneCommit() {
        AiPlatformConfig saved = repository().savePlatform(
                AiFixtures.platform("deepseek", "", "m1"), " sk-one ");

        assertEquals("sk-one", secrets.secrets.get("apikey.deepseek"));
        assertTrue(saved.configured());
        assertTrue(documents.documents.get(SETTINGS).contains("deepseek"));
        assertFalse(documents.documents.get(SETTINGS).contains("sk-one"));
        assertTrue(repository().load().platform("deepseek").orElseThrow().configured());
    }

    @Test
    public void savePlatformWithAnEmptyKeyClearsTheStoredKey() {
        repository().savePlatform(AiFixtures.platform("deepseek", "", "m1"), "sk-one");

        AiPlatformConfig cleared = repository().savePlatform(
                AiFixtures.platform("deepseek", "", "m1"), "");

        assertNull(secrets.secrets.get("apikey.deepseek"));
        assertFalse(cleared.configured());
        assertFalse(repository().load().platform("deepseek").orElseThrow().configured());
    }

    @Test
    public void aFailedSettingsWriteRollsBackTheKeyOfTheSameCommit() {
        repository().savePlatform(AiFixtures.platform("deepseek", "", "m1"), "old-key");
        String before = documents.documents.get(SETTINGS);
        documents.failFor.add(SETTINGS);
        List<AiFailure> failures = new ArrayList<>();
        AiSettingsRepositoryImpl repository = repository();
        repository.observePersistenceFailures(failures::add);

        repository.savePlatform(AiFixtures.platform("deepseek", "", "m2"), "new-key");

        assertEquals("设置文档没落，密钥也回到写前的值", "old-key",
                secrets.secrets.get("apikey.deepseek"));
        assertEquals("设置文档保持原样", before, documents.documents.get(SETTINGS));
        assertEquals(1, failures.size());
        assertEquals(AiFailureKind.STORAGE, failures.get(0).kind());
        assertEquals(SETTINGS, failures.get(0).location().orElseThrow().document());
    }

    @Test
    public void saveVoiceKeyWritesTheKeyAndMarksItConfiguredInOneCommit() {
        repository().update(s -> s.withVoice(s.voice().withModel(AiVoiceModel.QWEN)));

        AiSettings settings = repository().saveVoiceKey(AiVoiceModel.QWEN, "sk-qwen");

        assertEquals("sk-qwen", secrets.secrets.get("voicekey.qwen"));
        assertTrue(settings.voiceReady());
        assertFalse(documents.documents.get(SETTINGS).contains("sk-qwen"));
        assertFalse("文档里连密钥键都不该有", documents.documents.get(SETTINGS).contains("voicekey"));
        assertTrue(repository().load().voiceReady());
    }

    @Test
    public void savingTheSameVoiceKeyAgainDoesNotRewriteTheDocument() {
        repository().saveVoiceKey(AiVoiceModel.CHATGPT, "first");
        int writes = documents.writes;

        repository().saveVoiceKey(AiVoiceModel.CHATGPT, "second");

        assertEquals("configured 不入文档，换密钥不必重写设置文档", writes, documents.writes);
        assertEquals("second", secrets.secrets.get("voicekey.chatgpt"));
        assertTrue(repository().load().voiceReady());
    }

    @Test
    public void removingAPlatformRemovesItsSecretButKeepsTheVoiceKey() {
        secrets.secrets.put("voicekey.chatgpt", "sk-gpt");
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "", "m"))
                .withPlatform(AiFixtures.platform("b", "", "m"))
                .withVoice(AiFixtures.voice(AiVoiceModel.CHATGPT, "")));
        repository.savePlatform(AiFixtures.platform("a", "", "m"), "ka");
        repository.savePlatform(AiFixtures.platform("b", "", "m"), "kb");

        repository.update(s -> s.withoutPlatform("a"));

        assertNull(secrets.secrets.get("apikey.a"));
        assertEquals("kb", secrets.secrets.get("apikey.b"));
        assertEquals("sk-gpt", secrets.secrets.get("voicekey.chatgpt"));
    }

    @Test
    public void voiceDefaultDuplexSurvivesRestartBesideCredentials() {
        repository().saveVoiceKey(AiVoiceModel.CHATGPT, "sk-gpt");
        repository().update(s -> s.withVoice(s.voice().withDefaultDuplex(VoiceDuplex.FULL_DUPLEX)));

        AiVoiceConfig reloaded = repository().load().voice();

        assertEquals(VoiceDuplex.FULL_DUPLEX, reloaded.defaultDuplex());
        assertTrue(reloaded.configured());
    }

    @Test
    public void realtimeModelsSurviveARestart() {
        repository().update(s -> s.withVoice(AiFixtures.voice(AiVoiceModel.CHATGPT, "")
                .withBaseUrl("https://gw.example.com")
                .withRealtimeModels(List.of(new AiModel("gpt-realtime", "gpt-realtime",
                        null, null, Set.of(), false, AiModelCategory.REALTIME)))));

        AiVoiceConfig voice = repository().load().voice();

        assertEquals("https://gw.example.com", voice.baseUrl());
        assertEquals(1, voice.realtimeModels().size());
        assertEquals("gpt-realtime", voice.resolvedRealtimeModel());
    }

    @Test
    public void aLostVoiceKeyLeavesTheModelSelectedButUnready() {
        repository().saveVoiceKey(AiVoiceModel.CHATGPT, "sk-gpt");
        secrets.secrets.clear();

        AiSettings reloaded = repository().load();

        assertEquals(AiVoiceModel.CHATGPT, reloaded.voice().model());
        assertFalse(reloaded.voiceReady());
    }

    @Test
    public void aLostSecretLeavesThePlatformUnconfiguredInsteadOfFailing() {
        repository().update(s -> s.withPlatform(AiFixtures.platform("deepseek", "k", "m1")));
        secrets.secrets.clear();

        AiPlatformConfig reloaded = repository().load().platform("deepseek").orElseThrow();

        assertFalse(reloaded.configured());
        assertEquals(1, reloaded.models().size());
    }

    @Test
    public void aCorruptDocumentIsReportedAsStorageFailureAndLeftOnDisk() {
        documents.documents.put(SETTINGS, "{{{ not json");

        try {
            repository().load();
            fail("a corrupt document must not be read as empty settings");
        } catch (AiFailure failure) {
            assertEquals(AiFailureKind.STORAGE, failure.kind());
            assertNull("载荷里不放英文技术串", failure.getMessage());
            assertEquals(SETTINGS, failure.location().orElseThrow().document());
            assertTrue(failure.getCause() instanceof DocumentFormatException);
        }
        assertEquals("the damaged document stays on disk for recovery",
                "{{{ not json", documents.documents.get(SETTINGS));
    }

    @Test
    public void anUnsupportedVersionIsReportedAsStorageFailure() {
        documents.documents.put(SETTINGS, "{\"v\":5,\"platforms\":[]}");

        try {
            repository().load();
            fail("an old document version must not be migrated on the fly");
        } catch (AiFailure failure) {
            assertEquals(AiFailureKind.STORAGE, failure.kind());
            assertTrue(failure.getCause() instanceof DocumentFormatException);
        }
    }

    @Test
    public void unchangedUpdatesDoNotWriteOrNotify() {
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "k", "m1")));
        int writes = documents.writes;
        List<AiSettings> seen = new ArrayList<>();
        repository.observe(seen::add);

        AiSettings result = repository.update(s -> s);

        assertEquals(writes, documents.writes);
        assertTrue(seen.isEmpty());
        assertSame(repository.load(), result);
    }

    @Test
    public void observersSeeEachChangeUntilTheyUnsubscribe() {
        AiSettingsRepositoryImpl repository = repository();
        List<AiSettings> seen = new ArrayList<>();
        Subscription subscription = repository.observe(seen::add);

        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));
        repository.update(s -> s.withTextSelection(new AiSelection("a", "m")));
        subscription.cancel();
        repository.update(s -> s.withTextSelection(null));

        assertEquals(2, seen.size());
        assertEquals(new AiSelection("a", "m"), seen.get(1).textSelection());
    }

    @Test
    public void aFailingObserverDoesNotUndoTheSave() {
        AiSettingsRepositoryImpl repository = repository();
        repository.observe(s -> {
            throw new IllegalStateException("boom");
        });

        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));

        assertEquals(1, repository().load().platforms().size());
    }

    @Test
    public void aRejectedChangeLeavesStateAndDiskUntouched() {
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));
        int writes = documents.writes;
        String before = documents.documents.get(SETTINGS);

        try {
            repository.update(s -> {
                throw new IllegalArgumentException("rejected");
            });
            fail("the exception from the change must propagate");
        } catch (IllegalArgumentException expected) {
            assertEquals(writes, documents.writes);
            assertEquals(before, documents.documents.get(SETTINGS));
            assertEquals(1, repository.load().platforms().size());
        }
    }

    @Test
    public void aFailedWriteLeavesTheInMemoryStateAndObserversUntouched() {
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));
        List<AiSettings> seen = new ArrayList<>();
        repository.observe(seen::add);
        List<AiFailure> failures = new ArrayList<>();
        repository.observePersistenceFailures(failures::add);
        documents.failFor.add(SETTINGS);

        try {
            repository.update(s -> s.withTextSelection(new AiSelection("a", "m")));
            repository.flush().join();
            fail("a storage failure must reach flush");
        } catch (CompletionException wrapped) {
            assertTrue(wrapped.getCause() instanceof AiFailure);
            AiFailure expected = (AiFailure) wrapped.getCause();
            assertEquals(AiFailureKind.STORAGE, expected.kind());
            assertNull("载荷里不放英文技术串", expected.getMessage());
            assertEquals(SETTINGS, expected.location().orElseThrow().document());
            assertEquals(new AiSelection("a", "m"), repository.load().textSelection());
            assertEquals(1, seen.size());
            assertEquals(1, failures.size());
        }
    }

    @Test
    public void commitsPublishMemoryBeforeTheDiskWriteRuns() {
        QueueExecutor executor = new QueueExecutor();
        AiSettingsRepositoryImpl repository = queuedRepository(executor);
        List<AiSettings> seen = new ArrayList<>();
        repository.observe(seen::add);

        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));

        assertEquals(1, seen.size());
        assertEquals("a", repository.load().platforms().get(0).id());
        assertEquals(0, documents.writes);
        assertEquals(1, executor.pending());
        executor.drain();
        assertEquals(1, documents.writes);
    }

    @Test
    public void queuedWritesCoalesceToTheLatestSnapshot() {
        QueueExecutor executor = new QueueExecutor();
        AiSettingsRepositoryImpl repository = queuedRepository(executor);
        List<AiSettings> seen = new ArrayList<>();
        repository.observe(seen::add);

        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));
        repository.update(s -> s.withTextSelection(new AiSelection("a", "m")));
        repository.saveVoiceKey(AiVoiceModel.CHATGPT, "sk-qwen");

        assertEquals(3, seen.size());
        assertEquals(0, documents.writes);
        assertEquals(1, executor.pending());
        executor.drain();
        assertEquals(1, documents.writes);
        assertTrue(repository().load().voiceReady());
        assertEquals(new AiSelection("a", "m"), repository().load().textSelection());
    }

    @Test
    public void flushWaitsForTheQueuedRevision() {
        QueueExecutor executor = new QueueExecutor();
        AiSettingsRepositoryImpl repository = queuedRepository(executor);
        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));

        CompletableFuture<Void> flushed = repository.flush();

        assertFalse(flushed.isDone());
        executor.drain();
        assertTrue(flushed.isDone());
        assertFalse(flushed.isCompletedExceptionally());
    }

    @Test
    public void loadAsyncReadsOnTheInjectedExecutorAndSharesOneRead() {
        QueueExecutor executor = new QueueExecutor();
        AiSettingsRepositoryImpl repository = queuedRepository(executor);
        assertFalse(repository.ready());

        CompletableFuture<AiSettings> first = repository.loadAsync();
        CompletableFuture<AiSettings> second = repository.loadAsync();

        assertFalse(first.isDone());
        assertFalse(second.isDone());
        assertEquals(1, executor.pending());
        executor.drain();
        assertEquals(AiSettings.EMPTY, first.join());
        assertEquals(AiSettings.EMPTY, second.join());
        assertTrue(repository.ready());
    }

    @Test
    public void persistenceStatusReportsSavingThenSaved() {
        QueueExecutor executor = new QueueExecutor();
        AiSettingsRepositoryImpl repository = queuedRepository(executor);
        List<AiSettingsPersistenceStatus> statuses = new ArrayList<>();
        repository.observePersistenceStatus(statuses::add);

        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));
        assertEquals(List.of(AiSettingsPersistenceStatus.SAVING), statuses);
        executor.drain();
        assertEquals(List.of(AiSettingsPersistenceStatus.SAVING,
                AiSettingsPersistenceStatus.SAVED), statuses);
    }

    @Test
    public void persistenceStatusReportsFailure() {
        QueueExecutor executor = new QueueExecutor();
        AiSettingsRepositoryImpl repository = queuedRepository(executor);
        List<AiSettingsPersistenceStatus> statuses = new ArrayList<>();
        repository.observePersistenceStatus(statuses::add);
        documents.failFor.add(SETTINGS);

        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));
        executor.drain();

        assertEquals(AiSettingsPersistenceStatus.FAILED, statuses.get(statuses.size() - 1));
    }

    @Test
    public void anUnreadableSecretDocumentFailsTheCommitWithItsCoordinates() {
        documents.documents.put(EncryptedSecretStore.DOCUMENT, "corrupt");
        EncryptedSecretStore secrets = new EncryptedSecretStore(documents,
                new PlainCipher());
        AiSettingsRepositoryImpl repository = new AiSettingsRepositoryImpl(
                documents, secrets, new AiSettingsJson(), Runnable::run);

        try {
            repository.savePlatform(AiFixtures.platform("deepseek", "", "m1"), "sk-one");
            fail("an unreadable credentials document must fail the commit");
        } catch (AiFailure failure) {
            assertEquals(AiFailureKind.STORAGE, failure.kind());
            assertEquals(EncryptedSecretStore.DOCUMENT, failure.location().orElseThrow().document());
            assertEquals("deepseek", failure.location().orElseThrow().entityId());
        }
    }

    /** 只为这条用例准备的可逆密码器：不需要真正的密钥库。 */
    private static final class PlainCipher implements SecretCipher {

        @Override
        public String encrypt(String plain) {
            return "enc:" + plain;
        }

        @Override
        public String decrypt(String encoded) {
            return encoded.substring(4);
        }
    }
}
