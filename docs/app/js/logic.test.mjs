
import test from 'node:test';
import assert from 'node:assert/strict';
import * as L from './logic.js';

const NOW = 1_700_000_000_000;

// ---- persistent card identity ----------------------------------------

test('coreIndexCardsKeyOnCoreIndex', () => {
    assert.equal(L.cardKey(321, "Work & School", "prezydent"), "core:321");
});

test('nonCoreCardsKeyOnCategoryAndPolish', () => {
    assert.equal(
        L.cardKey(0, "Everyday", "Dzień dobry."),
        "Everyday:Dzień dobry."
    );
});

test('cardKeyIsStableAcrossCallsAndIgnoresOtherFields', () => {
    const a = L.cardKey(7, "Food & Drink", "chleb");
    const b = L.cardKey(7, "Anything Else", "different text");
    // coreIndex wins, so a card can be re-categorized without losing progress.
    assert.equal(a, b);
});

// ---- Leitner scheduling ----------------------------------------------

test('correctAnswerPromotesOneBox', () => {
    assert.equal(L.nextBox(0, true), 1);
    assert.equal(L.nextBox(2, true), 3);
});

test('promotionIsCappedAtMaxBox', () => {
    assert.equal(L.nextBox(L.MAX_BOX, true), L.MAX_BOX);
});

test('missResetsToBoxOne', () => {
    assert.equal(L.nextBox(5, false), 1);
    assert.equal(L.nextBox(0, false), 1);
});

test('intervalsFollowTheDocumentedSchedule', () => {
    const day = L.DAY_MS;
    assert.equal(L.BOX_INTERVALS_MS[1], day);
    assert.equal(L.BOX_INTERVALS_MS[2], 3 * day);
    assert.equal(L.BOX_INTERVALS_MS[3], 7 * day);
    assert.equal(L.BOX_INTERVALS_MS[4], 16 * day);
    assert.equal(L.BOX_INTERVALS_MS[5], 35 * day);
});

test('correctAnswerSchedulesByNewBoxInterval', () => {
    const box = L.nextBox(1, true); // -> 2
    assert.equal(L.nextDueAt(box, true, NOW), NOW + 3 * L.DAY_MS);
});

test('missIsDueImmediately', () => {
    assert.equal(L.nextDueAt(1, false, NOW), NOW);
    assert.ok(L.isDue(1, NOW, NOW));
});

test('newCardsAreNotDueAndReportNewStatus', () => {
    assert.ok(!L.isDue(0, 0, NOW));
    assert.equal(L.statusOf(0, 0, NOW), L.STATUS_NEW);
});

test('scheduledAheadCardIsNotDue', () => {
    const due = NOW + L.DAY_MS;
    assert.ok(!L.isDue(2, due, NOW));
    assert.equal(L.statusOf(2, due, NOW), L.STATUS_SCHEDULED);
    assert.equal(L.statusOf(2, due, due + 1), L.STATUS_DUE);
});

// ---- stored memory round-trip ----------------------------------------

test('memoryRoundTrips', () => {
    const encoded = L.encodeMemory(3, NOW);
    const decoded = L.decodeMemory(encoded, NOW);
    assert.equal(decoded[0], 3);
    assert.equal(decoded[1], NOW);
});

test('legacyStatusStringsMigrateInsteadOfResetting', () => {
    const learnt = L.decodeMemory("learnt", NOW);
    assert.equal(learnt[0], 2);
    assert.equal(learnt[1], NOW + 3 * L.DAY_MS);

    const forgot = L.decodeMemory("forgot", NOW);
    assert.equal(forgot[0], 1);
    assert.equal(forgot[1], NOW);

    assert.equal(L.decodeMemory("new", NOW)[0], 0);
});

test('corruptOrEmptyMemoryFallsBackToNewWithoutThrowing', () => {
    assert.equal(L.decodeMemory(null, NOW)[0], 0);
    assert.equal(L.decodeMemory("", NOW)[0], 0);
    assert.equal(L.decodeMemory("garbage", NOW)[0], 0);
    assert.equal(L.decodeMemory("x|y", NOW)[0], 0);
});

test('decodedBoxIsClampedToValidRange', () => {
    assert.equal(L.decodeMemory("99|" + NOW, NOW)[0], L.MAX_BOX);
    assert.equal(L.decodeMemory("-4|" + NOW, NOW)[0], 0);
});

// ---- data update version comparison ----------------------------------

test('onlyStrictlyNewerDataVersionsTriggerAnUpdate', () => {
    assert.ok(L.isNewerDataVersion(11, 10));
    assert.ok(!L.isNewerDataVersion(10, 10));
    assert.ok(!L.isNewerDataVersion(9, 10));
});

// ---- spoken-answer scoring --------------------------------------------

test('spokenNormalizationDropsPunctuationButKeepsDiacritics', () => {
    assert.equal(L.normalizeSpoken("  Dzień dobry!  "), "dzień dobry");
    assert.equal(L.normalizeSpoken("Cześć."), "cześć");
});

test('anExactMatchScoresFull', () => {
    assert.equal(L.pronunciationScore("Dzień dobry.", "dzień dobry"), 100);
});

test('aNearMissScoresHighButNotFull', () => {
    const score = L.pronunciationScore("cześć", "czesc");
    assert.ok(score >= 55 && score < 100, "expected a high partial score, got " + score);
});

test('aShortPhraseIsNotPunishedForWordsTheRecognizerAddedAround', () => {
    // The whole point of matching a window: these were all said correctly.
    assert.equal(L.pronunciationScore("dzień dobry", "dzień dobry panie"), 100);
    assert.equal(L.pronunciationScore("dzień dobry", "no dzień dobry"), 100);
    assert.equal(L.pronunciationScore("do widzenia", "do widzenia pani"), 100);
});

test('aWrongWordInsideAShortPhraseStillFails', () => {
    // Leniency about surrounding noise must not excuse the phrase itself.
    assert.ok(L.pronunciationScore("dzień dobry", "dzień samochód") < 70);
});

test('theMarksAgreeWithAWindowedScore', () => {
    const marks = L.markHeardWords("dzień dobry", "dzień dobry panie");
    assert.ok(marks[0]);
    assert.ok(marks[1]);
});

test('anUnrelatedAnswerScoresLow', () => {
    assert.ok(L.pronunciationScore("dziękuję", "samochód") < 40);
});

test('emptyOrMissingSpeechScoresZero', () => {
    assert.equal(L.pronunciationScore("dom", ""), 0);
    assert.equal(L.pronunciationScore("dom", null), 0);
    assert.equal(L.pronunciationScore("", "dom"), 0);
});

test('wordMatchingToleratesASlipButNotADifferentWord', () => {
    assert.ok(L.wordMatches("dobry", "dobre"));
    assert.ok(!L.wordMatches("dobry", "samochód"));
});

test('heardWordsAreMarkedPerWord', () => {
    const marks = L.markHeardWords("dzień dobry panie", "dzień dobry");
    assert.ok(marks[0]);
    assert.ok(marks[1]);
    assert.ok(!marks[2], "the unspoken third word must not be marked");
});

test('aMissingWordDoesNotDerailTheWordsAfterIt', () => {
    // "bardzo" was dropped; "dobry" still has to register.
    const marks = L.markHeardWords("jest bardzo dobry", "jest dobry");
    assert.ok(marks[0]);
    assert.ok(!marks[1]);
    assert.ok(marks[2], "a dropped word must not fail the rest");
});

// ---- explain-with-AI prompt -------------------------------------------

test('promptCarriesTheWordContextTranslationAndLevel', () => {
    const p = L.explainPrompt("dzwoni", "is ringing", "Ktoś dzwoni do drzwi.", "A2");
    assert.ok(p.includes("\"dzwoni\""));
    assert.ok(p.includes("Ktoś dzwoni do drzwi."));
    assert.ok(p.includes("is ringing"));
    assert.ok(p.includes("level A2"));
    assert.ok(
        p.includes("why it takes this form here"),
        "an inflected word needs the 'why this form' question"
    );
});

test('aSingleWordAsksForWordGrammarAndAPhraseForPhraseGrammar', () => {
    assert.ok(L.explainPrompt("dom", "", "", "").includes("Polish word"));
    assert.ok(L.explainPrompt("dom", "", "", "").includes("aspect partner"));
    const phrase = L.explainPrompt("dzień dobry", "", "", "");
    assert.ok(phrase.includes("Polish phrase"));
    assert.ok(phrase.includes("word for word"));
});

test('missingPartsAreLeftOutRatherThanPrintedEmpty', () => {
    const p = L.explainPrompt("dom", null, null, null);
    assert.ok(!p.includes("appeared in this sentence"));
    assert.ok(!p.includes("My app translates"));
    assert.ok(!p.includes("level"));
    assert.ok(!p.includes("why it takes this form"));
});

test('aContextThatIsJustTheWordItselfIsDropped', () => {
    assert.ok(
        !L.explainPrompt("Dzień dobry.", "", "dzień dobry", "")
            .includes("appeared in this sentence")
    );
});

// ---- typed answers ---------------------------------------------------

test('exactAnswersIgnoreCaseAndPunctuation', () => {
    assert.equal(L.checkAnswer("Biletu", "biletu"), L.ANSWER_EXACT);
    assert.equal(
        L.checkAnswer("uczę się polskiego", "Uczę się polskiego."),
        L.ANSWER_EXACT
    );
});

test('missingPolishLettersAreTheirOwnVerdict', () => {
    assert.equal(L.checkAnswer("pociagiem", "pociągiem"), L.ANSWER_DIACRITICS);
    assert.equal(L.checkAnswer("kolezanka", "koleżanką"), L.ANSWER_DIACRITICS);
    assert.equal(
        L.stripPolishDiacritics("żłobek"),
        "zlobek",
        "ł has no decomposition and must still map"
    );
});

test('reorderedWordsAreAcceptedAsWordOrder', () => {
    assert.equal(
        L.checkAnswer("Nie muszę jutro pracować", "Nie muszę pracować jutro."),
        L.ANSWER_WORD_ORDER
    );
});

test('anyAcceptedAlternativeCounts', () => {
    assert.equal(
        L.checkAnswer("Lubię ten film.", "Ten film mi się podoba.", "Lubię ten film."),
        L.ANSWER_EXACT
    );
});

test('aWrongCaseEndingIsNotExact', () => {
    const verdict = L.checkAnswer("bilet", "biletu");
    assert.ok(verdict !== L.ANSWER_EXACT && verdict !== L.ANSWER_DIACRITICS);
    assert.equal(L.checkAnswer("samochód", "biletu"), L.ANSWER_WRONG);
    assert.equal(L.checkAnswer("", "biletu"), L.ANSWER_WRONG);
});

test('theBestVerdictWinsAcrossAlternatives', () => {
    // "pojdziemy" is pójdziemy minus the ó: a letters-only miss on the
    // second option must beat merely "close" on the first.
    assert.equal(
        L.checkAnswer("Jutro pojdziemy do kina", "Jutro idziemy do kina.", "Jutro pójdziemy do kina."),
        L.ANSWER_DIACRITICS
    );
});

// ---- word notes ---------------------------------------------------------

test('aiMarkdownIsShownAsPlainText', () => {
    const raw = "## Meaning\n**dzwoni** is the 3rd person of *dzwonić*.\n- one\n* two\n`code`";
    const plain = L.plainNote(raw);
    assert.ok(!plain.includes("##"));
    assert.ok(!plain.includes("**"));
    assert.ok(!plain.includes("`"));
    assert.ok(plain.includes("Meaning"));
    assert.ok(plain.includes("• one"));
    assert.ok(plain.includes("• two"));
});

test('plainNoteKeepsPolishAndCollapsesBlankRuns', () => {
    const plain = L.plainNote("Cześć\n\n\n\nżółw\r\n---\nźle");
    assert.ok(plain.includes("Cześć"));
    assert.ok(plain.includes("żółw"));
    assert.ok(!plain.includes("\n\n\n"), "runs of blank lines collapse");
    assert.ok(!plain.includes("---"), "a horizontal rule is dropped");
});

test('previewCutsAtAWordAndMarksTheCut', () => {
    const p = L.notePreview("jeden dwa trzy cztery pięć sześć", 15);
    assert.ok(p.endsWith("…"));
    assert.ok(!p.includes("trz…"), "must not split a word");
    assert.equal(L.notePreview("krótko", 50), "krótko");
    assert.equal(L.notePreview(null, 50), "");
});

// ---- markdown export --------------------------------------------------

test('markdownCellEscapesPipesSoTheTableSurvives', () => {
    assert.equal(L.markdownCell("either | or"), "either \\| or");
});

test('markdownCellFlattensLineBreaksIntoOneRow', () => {
    assert.equal(L.markdownCell("first\nsecond"), "first second");
    assert.equal(L.markdownCell("first\r\nsecond"), "first second");
});

test('markdownCellTrimsAndHandlesNull', () => {
    assert.equal(L.markdownCell("  dom  "), "dom");
    assert.equal(L.markdownCell(null), "");
});

test('markdownCellKeepsPolishDiacritics', () => {
    assert.equal(L.markdownCell("dziękuję"), "dziękuję");
});

// ---- headword normalization ------------------------------------------

test('normalizationLowercasesTrimsAndStripsTrailingPunctuation', () => {
    assert.equal(L.normalizeHeadword("  Dom. "), "dom");
    assert.equal(L.normalizeHeadword("Dzień dobry!"), "dzień dobry");
    assert.equal(L.normalizeHeadword(null), "");
});

test('normalizationPreservesPolishDiacritics', () => {
    assert.equal(L.normalizeHeadword("Gardło"), "gardło");
    assert.ok(L.normalizeHeadword("gardło") !== "gardlo");
});
