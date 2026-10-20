package com.ggec.earbuds.data.ai.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ggec.earbuds.data.ai.json.AiSettingsJson;
import com.ggec.earbuds.data.ai.store.AiStorageException;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.MemoryDocumentStore;
import com.ggec.earbuds.testing.ai.MemorySecretStore;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class AiSettingsRepositoryImplTest {

    private final MemoryDocumentStore documents = new MemoryDocumentStore();
    private final MemorySecretStore secrets = new MemorySecretStore();

    private AiSettingsRepositoryImpl repository() {
        return new AiSettingsRepositoryImpl(documents, secrets, new AiSettingsJson());
    }

    @Test
    public void startsEmptyWhenNothingWasSaved() {
        assertEquals(AiSettings.EMPTY, repository().load());
        assertEquals(0, documents.writes);
    }

    @Test
    public void updatesPersistAndSurviveARestart() {
        AiSettingsRepositoryImpl first = repository();
        first.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "sk-one", "m1", "m2"))
                .withSelection(new AiSelection("deepseek", "m2")));

        AiSettings reloaded = repository().load();

        assertEquals("sk-one", reloaded.platform("deepseek").orElseThrow().apiKey());
        assertEquals(2, reloaded.platform("deepseek").orElseThrow().models().size());
        assertEquals(new AiSelection("deepseek", "m2"), reloaded.selection());
        assertTrue(reloaded.activeModel().isPresent());
    }

    @Test
    public void keysLiveOnlyInTheSecretStore() {
        repository().update(s -> s.withPlatform(AiFixtures.platform("deepseek", "sk-very-secret", "m1")));

        assertEquals("sk-very-secret", secrets.secrets.get("apikey.deepseek"));
        assertFalse(documents.documents.get("settings.json").contains("sk-very-secret"));
    }

    @Test
    public void changingAKeyUpdatesItAndBlankingItRemovesIt() {
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "k1", "m1")));

        repository.update(s -> s.withPlatform(s.platform("deepseek").orElseThrow()
                .withCredentials("deepseek", "k2", null, com.ggec.earbuds.domain.ai.model.AiProtocol.CHAT_COMPLETIONS)));
        assertEquals("k2", secrets.secrets.get("apikey.deepseek"));

        repository.update(s -> s.withPlatform(s.platform("deepseek").orElseThrow().withoutKey()));
        assertNull(secrets.secrets.get("apikey.deepseek"));
        assertFalse(repository.load().platform("deepseek").orElseThrow().configured());
    }

    @Test
    public void removingAPlatformRemovesItsSecret() {
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "ka", "m"))
                .withPlatform(AiFixtures.platform("b", "kb", "m")));

        repository.update(s -> s.withoutPlatform("a"));

        assertNull(secrets.secrets.get("apikey.a"));
        assertEquals("kb", secrets.secrets.get("apikey.b"));
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
    public void aCorruptDocumentFallsBackToEmptySettings() {
        documents.documents.put("settings.json", "{{{ not json");

        assertEquals(AiSettings.EMPTY, repository().load());
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
        repository.update(s -> s.withSelection(new AiSelection("a", "m")));
        subscription.cancel();
        repository.update(s -> s.withSelection(null));

        assertEquals(2, seen.size());
        assertEquals(new AiSelection("a", "m"), seen.get(1).selection());
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

        try {
            repository.update(s -> {
                throw new IllegalArgumentException("rejected");
            });
            fail("the exception from the change must propagate");
        } catch (IllegalArgumentException expected) {
            assertEquals(writes, documents.writes);
            assertEquals(1, repository.load().platforms().size());
        }
    }

    @Test
    public void aFailedWriteLeavesTheInMemoryStateAndObserversUntouched() {
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));
        List<AiSettings> seen = new ArrayList<>();
        repository.observe(seen::add);
        documents.failWrites = true;

        try {
            repository.update(s -> s.withSelection(new AiSelection("a", "m")));
            fail("a storage failure must reach the caller");
        } catch (AiFailure expected) {
            assertEquals(AiFailureKind.STORAGE, expected.kind());
            assertTrue(expected.getCause() instanceof AiStorageException);
            assertNull(repository.load().selection());
            assertTrue(seen.isEmpty());
        }
    }
}
