package com.yovo.lib_ai.models.spec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;

/**
 * 三态账本与出处覆盖：没人说过不等于不支持，低出处改不动高出处。
 */
public final class YoSupportMapTest {

    private static YoSupportMap<YoModality> empty() {
        return YoSupportMap.empty(YoModality.class);
    }

    @Test
    public void nobodyHavingSpokenIsUnknownNotNo() {
        YoSupportMap<YoModality> map = empty();
        assertEquals(YoSupport.UNKNOWN, map.fact(YoModality.IMAGE).support());
        assertFalse(map.fact(YoModality.IMAGE).known());
        assertFalse(map.has(YoModality.IMAGE));
        assertFalse("没说过也不能算明确不支持", map.fact(YoModality.IMAGE).no());
        assertTrue(map.isEmpty());
    }

    @Test
    public void inferredYesIsAHintNotAConfirmation() {
        YoSupportMap<YoModality> map = empty().with(YoModality.IMAGE, YoSupport.YES, YoSpecSource.INFERRED);
        assertTrue(map.has(YoModality.IMAGE));
        assertFalse(map.confirmed(YoModality.IMAGE));
        assertTrue(map.fact(YoModality.IMAGE).unconfirmed());
        assertEquals(Set.of(YoModality.IMAGE), map.yes());
        assertTrue(map.confirmedYes().isEmpty());
    }

    @Test
    public void higherSourcesOverrideLowerOnes() {
        YoSupportMap<YoModality> inferred = empty().with(YoModality.IMAGE, YoSupport.YES, YoSpecSource.INFERRED);

        YoSupportMap<YoModality> reported = inferred.with(YoModality.IMAGE, YoSupport.NO, YoSpecSource.REPORTED);
        assertTrue("平台明确说不支持，压过名字上的推断", reported.fact(YoModality.IMAGE).no());
        assertFalse(reported.has(YoModality.IMAGE));

        YoSupportMap<YoModality> user = reported.with(YoModality.IMAGE, YoSupport.YES, YoSpecSource.USER);
        assertTrue("用户压过平台", user.confirmed(YoModality.IMAGE));
        assertEquals(YoSpecSource.USER, user.fact(YoModality.IMAGE).source());
    }

    @Test
    public void lowerSourcesCannotChangeAHigherConclusion() {
        YoSupportMap<YoModality> reported = empty().with(YoModality.IMAGE, YoSupport.NO, YoSpecSource.REPORTED);
        assertSame(reported, reported.with(YoModality.IMAGE, YoSupport.YES, YoSpecSource.INFERRED));

        YoSupportMap<YoModality> user = reported.with(YoModality.IMAGE, YoSupport.YES, YoSpecSource.USER);
        assertSame(user, user.with(YoModality.IMAGE, YoSupport.NO, YoSpecSource.REPORTED));
    }

    @Test
    public void sameLevelLaterWriteWins() {
        YoSupportMap<YoModality> map = empty()
                .with(YoModality.AUDIO, YoSupport.YES, YoSpecSource.REPORTED)
                .with(YoModality.AUDIO, YoSupport.NO, YoSpecSource.REPORTED);
        assertTrue(map.fact(YoModality.AUDIO).no());
    }

    @Test
    public void unknownStatementsChangeNothing() {
        YoSupportMap<YoModality> map = empty().with(YoModality.IMAGE, YoSupport.YES, YoSpecSource.REPORTED);
        assertSame(map, map.with(YoModality.IMAGE, YoSupport.UNKNOWN, YoSpecSource.USER));
        assertSame(map, map.with(YoModality.AUDIO, YoSupport.UNKNOWN, YoSpecSource.USER));
    }

    @Test
    public void exhaustiveListsMarkEverythingElseAsNo() {
        YoSupportMap<YoModality> map = empty().withExhaustive(
                List.of(YoModality.TEXT, YoModality.IMAGE), YoSpecSource.REPORTED);
        assertEquals(Set.of(YoModality.TEXT, YoModality.IMAGE), map.yes());
        assertTrue(map.fact(YoModality.AUDIO).no());
        assertTrue(map.fact(YoModality.VIDEO).no());
        assertEquals(YoSpecSource.REPORTED, map.fact(YoModality.AUDIO).source());
    }

    @Test
    public void exhaustiveListsStillRespectHigherSources() {
        YoSupportMap<YoModality> map = empty()
                .with(YoModality.AUDIO, YoSupport.YES, YoSpecSource.USER)
                .withExhaustive(List.of(YoModality.TEXT), YoSpecSource.REPORTED);
        assertTrue("用户勾过的不会被平台的穷举清单抹掉", map.confirmed(YoModality.AUDIO));
        assertTrue(map.fact(YoModality.IMAGE).no());
    }

    @Test
    public void writesReturnNewMapsAndExposedSetsAreReadOnly() {
        YoSupportMap<YoModality> base = empty();
        YoSupportMap<YoModality> changed = base.with(YoModality.TEXT, YoSupport.YES, YoSpecSource.USER);
        assertTrue(base.isEmpty());
        assertFalse(changed.isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> changed.yes().add(YoModality.IMAGE));
        assertEquals(changed, empty().with(YoModality.TEXT, YoSupport.YES, YoSpecSource.USER));
        assertEquals(changed.hashCode(),
                empty().with(YoModality.TEXT, YoSupport.YES, YoSpecSource.USER).hashCode());
    }
}
