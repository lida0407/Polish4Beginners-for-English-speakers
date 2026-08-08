package com.mustardseed.polish4beginners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Guards the logic that existing users' saved progress depends on.
 * A failure here means an update would corrupt or orphan real study data.
 */
public class LearningLogicTest {

    private static final long NOW = 1_700_000_000_000L;

    // ---- persistent card identity ----------------------------------------

    @Test
    public void coreIndexCardsKeyOnCoreIndex() {
        assertEquals("core:321", LearningLogic.cardKey(321, "Work & School", "prezydent"));
    }

    @Test
    public void nonCoreCardsKeyOnCategoryAndPolish() {
        assertEquals("Everyday:Dzień dobry.",
                LearningLogic.cardKey(0, "Everyday", "Dzień dobry."));
    }

    @Test
    public void cardKeyIsStableAcrossCallsAndIgnoresOtherFields() {
        String a = LearningLogic.cardKey(7, "Food & Drink", "chleb");
        String b = LearningLogic.cardKey(7, "Anything Else", "different text");
        // coreIndex wins, so a card can be re-categorized without losing progress.
        assertEquals(a, b);
    }

    // ---- Leitner scheduling ----------------------------------------------

    @Test
    public void correctAnswerPromotesOneBox() {
        assertEquals(1, LearningLogic.nextBox(0, true));
        assertEquals(3, LearningLogic.nextBox(2, true));
    }

    @Test
    public void promotionIsCappedAtMaxBox() {
        assertEquals(LearningLogic.MAX_BOX, LearningLogic.nextBox(LearningLogic.MAX_BOX, true));
    }

    @Test
    public void missResetsToBoxOne() {
        assertEquals(1, LearningLogic.nextBox(5, false));
        assertEquals(1, LearningLogic.nextBox(0, false));
    }

    @Test
    public void intervalsFollowTheDocumentedSchedule() {
        long day = LearningLogic.DAY_MS;
        assertEquals(day, LearningLogic.BOX_INTERVALS_MS[1]);
        assertEquals(3 * day, LearningLogic.BOX_INTERVALS_MS[2]);
        assertEquals(7 * day, LearningLogic.BOX_INTERVALS_MS[3]);
        assertEquals(16 * day, LearningLogic.BOX_INTERVALS_MS[4]);
        assertEquals(35 * day, LearningLogic.BOX_INTERVALS_MS[5]);
    }

    @Test
    public void correctAnswerSchedulesByNewBoxInterval() {
        int box = LearningLogic.nextBox(1, true); // -> 2
        assertEquals(NOW + 3 * LearningLogic.DAY_MS, LearningLogic.nextDueAt(box, true, NOW));
    }

    @Test
    public void missIsDueImmediately() {
        assertEquals(NOW, LearningLogic.nextDueAt(1, false, NOW));
        assertTrue(LearningLogic.isDue(1, NOW, NOW));
    }

    @Test
    public void newCardsAreNotDueAndReportNewStatus() {
        assertFalse(LearningLogic.isDue(0, 0, NOW));
        assertEquals(LearningLogic.STATUS_NEW, LearningLogic.statusOf(0, 0, NOW));
    }

    @Test
    public void scheduledAheadCardIsNotDue() {
        long due = NOW + LearningLogic.DAY_MS;
        assertFalse(LearningLogic.isDue(2, due, NOW));
        assertEquals(LearningLogic.STATUS_SCHEDULED, LearningLogic.statusOf(2, due, NOW));
        assertEquals(LearningLogic.STATUS_DUE, LearningLogic.statusOf(2, due, due + 1));
    }

    // ---- stored memory round-trip ----------------------------------------

    @Test
    public void memoryRoundTrips() {
        String encoded = LearningLogic.encodeMemory(3, NOW);
        long[] decoded = LearningLogic.decodeMemory(encoded, NOW);
        assertEquals(3, decoded[0]);
        assertEquals(NOW, decoded[1]);
    }

    @Test
    public void legacyStatusStringsMigrateInsteadOfResetting() {
        long[] learnt = LearningLogic.decodeMemory("learnt", NOW);
        assertEquals(2, learnt[0]);
        assertEquals(NOW + 3 * LearningLogic.DAY_MS, learnt[1]);

        long[] forgot = LearningLogic.decodeMemory("forgot", NOW);
        assertEquals(1, forgot[0]);
        assertEquals(NOW, forgot[1]);

        assertEquals(0, LearningLogic.decodeMemory("new", NOW)[0]);
    }

    @Test
    public void corruptOrEmptyMemoryFallsBackToNewWithoutThrowing() {
        assertEquals(0, LearningLogic.decodeMemory(null, NOW)[0]);
        assertEquals(0, LearningLogic.decodeMemory("", NOW)[0]);
        assertEquals(0, LearningLogic.decodeMemory("garbage", NOW)[0]);
        assertEquals(0, LearningLogic.decodeMemory("x|y", NOW)[0]);
    }

    @Test
    public void decodedBoxIsClampedToValidRange() {
        assertEquals(LearningLogic.MAX_BOX, LearningLogic.decodeMemory("99|" + NOW, NOW)[0]);
        assertEquals(0, LearningLogic.decodeMemory("-4|" + NOW, NOW)[0]);
    }

    // ---- data update version comparison ----------------------------------

    @Test
    public void onlyStrictlyNewerDataVersionsTriggerAnUpdate() {
        assertTrue(LearningLogic.isNewerDataVersion(11, 10));
        assertFalse(LearningLogic.isNewerDataVersion(10, 10));
        assertFalse(LearningLogic.isNewerDataVersion(9, 10));
    }

    // ---- headword normalization ------------------------------------------

    @Test
    public void normalizationLowercasesTrimsAndStripsTrailingPunctuation() {
        assertEquals("dom", LearningLogic.normalizeHeadword("  Dom. "));
        assertEquals("dzień dobry", LearningLogic.normalizeHeadword("Dzień dobry!"));
        assertEquals("", LearningLogic.normalizeHeadword(null));
    }

    @Test
    public void normalizationPreservesPolishDiacritics() {
        assertEquals("gardło", LearningLogic.normalizeHeadword("Gardło"));
        assertFalse(LearningLogic.normalizeHeadword("gardło").equals("gardlo"));
    }
}
