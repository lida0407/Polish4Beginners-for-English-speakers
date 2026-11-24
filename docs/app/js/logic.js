
/**
 * Pure logic extracted from MainActivity so it can be unit tested without an
 * Android device. Behaviour here is deliberately identical to what shipped
 * before extraction — in particular the Leitner intervals and the card key
 * format, both of which are baked into existing users' saved progress.
 *
 * Changing anything in this class can invalidate user data. Do not alter the
 * card key format or the stored memory encoding without a migration.
 */

export const DAY_MS = 24 * 60 * 60 * 1000;

/** Box 0 = new; boxes 1..5 are reviewed at growing intervals. */
export const BOX_INTERVALS_MS = [
    0, DAY_MS, 3 * DAY_MS, 7 * DAY_MS, 16 * DAY_MS, 35 * DAY_MS
];

export const MAX_BOX = BOX_INTERVALS_MS.length - 1;

export const STATUS_NEW = 0;
export const STATUS_DUE = 1;
export const STATUS_SCHEDULED = 2;

/**
 * Persistent card identity. Cards carrying a coreIndex keep it forever;
 * everything else is identified by category + Polish text.
 *
 * These strings are SharedPreferences keys for user progress — never
 * renumber coreIndex values and never change this format.
 */
export function cardKey(coreIndex, category, polish) {
    if (coreIndex > 0) {
        return "core:" + coreIndex;
    }
    return category + ":" + polish;
}

/** Next box after an answer: correct promotes (capped), a miss resets to 1. */
export function nextBox(currentBox, got) {
    if (!got) {
        return 1;
    }
    const box = currentBox + 1;
    return Math.min(box, MAX_BOX);
}

/** When the card should next appear. A miss is due immediately. */
export function nextDueAt(nextBox, got, now) {
    if (!got) {
        return now;
    }
    return now + BOX_INTERVALS_MS[nextBox];
}

export function statusOf(box, dueAt, now) {
    if (box <= 0) {
        return STATUS_NEW;
    }
    return dueAt <= now ? STATUS_DUE : STATUS_SCHEDULED;
}

export function isDue(box, dueAt, now) {
    return box > 0 && dueAt <= now;
}

/** Serialized form of a card's memory: "box|dueAtMillis". */
export function encodeMemory(box, dueAt) {
    return box + "|" + dueAt;
}

/**
 * Parses stored memory, tolerating the pre-scheduling formats that older
 * installs wrote ("new" / "learnt" / "forgot"). Returns {box, dueAt}.
 */
export function decodeMemory(stored, now) {
    if (stored == null || stored === "" || stored === "new") {
        return [0, 0];
    }
    if (stored === "learnt") {
        return [2, now + BOX_INTERVALS_MS[2]];
    }
    if (stored === "forgot") {
        return [1, now];
    }
    const split = stored.indexOf('|');
    if (split > 0) {
        const boxPart = stored.substring(0, split);
        const dueAtPart = stored.substring(split + 1);
        if (/^[+-]?\d+$/.test(boxPart) && /^[+-]?\d+$/.test(dueAtPart)) {
            const rawBox = parseInt(boxPart, 10);
            const box = Math.max(0, Math.min(MAX_BOX, rawBox));
            const dueAt = Number(dueAtPart);
            return [box, dueAt];
        }
    }
    return [0, 0];
}

/** True when the remote data manifest advertises something newer. */
export function isNewerDataVersion(remoteVersion, localVersion) {
    return remoteVersion > localVersion;
}

/**
 * Escapes one value for a Markdown table cell: a pipe would end the cell
 * and any line break would end the row.
 *
 * Note the character class rather than \R, which needs API 24 while this
 * app still supports API 23.
 */
export function markdownCell(value) {
    if (value == null) {
        return "";
    }
    return value.replace(/\|/g, "\\|").replace(/[\r\n]+/g, " ").replace(/ {2,}/g, " ").trim();
}

// ---- spoken-answer scoring -------------------------------------------

/**
 * Normalizes a spoken or written phrase for comparison: lower case, no
 * punctuation, single spaces. Polish diacritics are kept — a recognizer
 * returns them, and "cześć" vs "czesc" is a real difference.
 */
export function normalizeSpoken(value) {
    if (value == null) {
        return "";
    }
    const out = value.toLowerCase();
    let cleaned = "";
    for (let i = 0; i < out.length; i++) {
        const c = out.charAt(i);
        if (/[\p{L}\p{N}]/u.test(c)) {
            cleaned += c;
        } else {
            cleaned += ' ';
        }
    }
    return cleaned.replace(/ {2,}/g, " ").trim();
}

/** Classic edit distance, used to score a near-miss rather than fail it. */
export function editDistance(a, b) {
    if (a == null) {
        a = "";
    }
    if (b == null) {
        b = "";
    }
    let previous = new Array(b.length + 1);
    let current = new Array(b.length + 1);
    for (let j = 0; j <= b.length; j++) {
        previous[j] = j;
    }
    for (let i = 1; i <= a.length; i++) {
        current[0] = i;
        for (let j = 1; j <= b.length; j++) {
            const cost = a.charAt(i - 1) === b.charAt(j - 1) ? 0 : 1;
            current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
        }
        const swap = previous;
        previous = current;
        current = swap;
    }
    return previous[b.length];
}

/** Raw closeness of two already-normalized strings, 0-100. */
function closeness(a, b) {
    if (a === b) {
        return 100;
    }
    if (a.length === 0) {
        return 0;
    }
    const distance = editDistance(a, b);
    const score = Math.round((100 * (a.length - distance)) / a.length);
    return Math.max(0, Math.min(99, score));
}

/**
 * The run of heard words that best matches the target.
 *
 * A recognizer given a short phrase routinely returns more than was said —
 * a trailing "panie", a leading "no". Scored against the whole string that
 * would fail a perfect reading, and the shorter the target the worse the
 * penalty, so a two-word phrase suffers most. Matching the best window
 * instead scores what the speaker actually attempted.
 */
export function bestMatchWindow(target, heard) {
    const a = normalizeSpoken(target);
    const normalizedHeard = normalizeSpoken(heard);
    if (a === "" || normalizedHeard === "") {
        return normalizedHeard;
    }
    const got = normalizedHeard.split(" ");
    const want = a.split(" ").length;
    let best = normalizedHeard;
    let bestScore = closeness(a, normalizedHeard);
    const from = Math.max(1, want - 1);
    const to = Math.min(got.length, want + 1);
    for (let size = from; size <= to; size++) {
        for (let start = 0; start + size <= got.length; start++) {
            let window = "";
            for (let i = start; i < start + size; i++) {
                if (window.length > 0) {
                    window += ' ';
                }
                window += got[i];
            }
            const score = closeness(a, window);
            if (score > bestScore) {
                bestScore = score;
                best = window;
            }
        }
    }
    return best;
}

/**
 * How close a heard phrase is to the target, 0-100. This scores the words
 * a recognizer decided it heard, so it is a usable "did that come out
 * right?" check, not a phoneme-level assessment.
 */
export function pronunciationScore(target, heard) {
    const a = normalizeSpoken(target);
    if (a === "" || normalizeSpoken(heard) === "") {
        return 0;
    }
    return closeness(a, bestMatchWindow(target, heard));
}

/** True when one word is close enough to count, allowing a slip or two. */
export function wordMatches(target, heard) {
    const a = normalizeSpoken(target);
    const b = normalizeSpoken(heard);
    if (a === b) {
        return true;
    }
    if (a === "" || b === "") {
        return false;
    }
    const allowed = a.length <= 4 ? 1 : 2;
    return editDistance(a, b) <= allowed;
}

/**
 * Marks each word of the target as heard or not. Walks both sides forward
 * so a missing or inserted word shifts the rest instead of failing it.
 */
export function markHeardWords(target, heard) {
    const want = normalizeSpoken(target).split(" ");
    const got = bestMatchWindow(target, heard).split(" ");
    const marks = new Array(want.length).fill(false);
    let g = 0;
    for (let i = 0; i < want.length; i++) {
        for (let j = g; j < got.length && j <= g + 1; j++) {
            if (wordMatches(want[i], got[j])) {
                marks[i] = true;
                g = j + 1;
                break;
            }
        }
    }
    return marks;
}

// ---- "explain with AI" prompt ----------------------------------------

/**
 * Builds a prompt the learner can paste into any AI chat to have a word or
 * phrase explained properly. Nothing is sent anywhere by the app; this is
 * just text, so it works with whichever assistant the user already has.
 *
 * The sentence the word came from matters most: Polish words change form
 * with case, gender and aspect, so "what does this mean here, and why this
 * form" is the useful question, and only the context can answer it.
 *
 * @param context the sentence the word appeared in, or empty
 * @param gloss   the app's own translation, or empty
 * @param level   CEFR level such as "A2", or empty
 */
export function explainPrompt(text, gloss, context, level) {
    const item = text == null ? "" : text.trim();
    const meaning = gloss == null ? "" : gloss.trim();
    let sentence = context == null ? "" : context.trim();
    const cefr = level == null ? "" : level.trim();
    const phrase = item.split(/\s+/).length > 1;
    // A context identical to the item itself adds nothing.
    if (normalizeSpoken(sentence) === normalizeSpoken(item)) {
        sentence = "";
    }

    let p = "";
    p += "I'm an English speaker learning Polish";
    if (cefr !== "") {
        p += " (level " + cefr + ")";
    }
    p += ". Please explain the Polish " + (phrase ? "phrase" : "word")
            + " \"" + item + "\" to me.\n\n";
    if (sentence !== "") {
        p += "It appeared in this sentence: \"" + sentence + "\"\n";
    }
    if (meaning !== "") {
        p += "My app translates it as: \"" + meaning + "\"\n";
    }
    if (sentence !== "" || meaning !== "") {
        p += '\n';
    }

    p += "Please cover:\n";
    let n = 1;
    if (phrase) {
        p += (n++) + ". What it means" + (sentence === "" ? "" : " in this sentence")
                + ", both word for word and as a natural English equivalent\n";
        p += (n++) + ". The grammar of each word: case, gender, number, verb aspect and tense where relevant\n";
        p += (n++) + ". When and with whom to use it (formal, informal, spoken, written)\n";
    } else {
        p += (n++) + ". Its meaning" + (sentence === "" ? "" : " in this sentence")
                + ", and its other common meanings\n";
        p += (n++) + ". Part of speech and grammar. For a noun: gender and the most useful case forms. "
                + "For a verb: the infinitive, its aspect and aspect partner, and present and past forms. "
                + "For an adjective: its gender forms\n";
        p += (n++) + ". If this is not the dictionary form, what the dictionary form is"
                + (sentence === "" ? "" : " and why it takes this form here") + '\n';
    }
    p += (n++) + ". Pronunciation tips for an English speaker: stress and any tricky sounds\n";
    p += (n++) + ". Three short example sentences"
            + (cefr === "" ? "" : " at my level") + ", with English translations\n";
    p += (n++) + ". Common phrases or collocations it appears in\n";
    p += n + ". False friends, common learner mistakes, or anything easy to confuse it with\n\n";
    p += "Keep it concise and use simple English.";
    return p;
}

// ---- typed answers (grammar guide practice) ----------------------------

export const ANSWER_WRONG = 0;
/** Exactly right (case and punctuation aside). */
export const ANSWER_EXACT = 1;
/** Right word, missing or wrong Polish letters: "biletu" typed as "biletu" without ł etc. */
export const ANSWER_DIACRITICS = 2;
/** Same words as a model answer in another order, usually fine in Polish. */
export const ANSWER_WORD_ORDER = 3;
/** Close to a model answer, but with a real difference worth looking at. */
export const ANSWER_CLOSE = 4;

/** ą→a, ł→l and so on. ł has no Unicode decomposition, so it is mapped by hand. */
export function stripPolishDiacritics(value) {
    if (value == null) {
        return "";
    }
    const from = "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ";
    const to = "acelnoszzACELNOSZZ";
    let out = "";
    for (let i = 0; i < value.length; i++) {
        const c = value.charAt(i);
        const k = from.indexOf(c);
        out += k >= 0 ? to.charAt(k) : c;
    }
    return out;
}

function sortedWords(normalized) {
    const words = normalized.split(" ");
    words.sort();
    return words.join(" ");
}

/** Verdict strength for "keep the best one": exact > order > letters > close. */
function rank(verdict) {
    switch (verdict) {
        case ANSWER_EXACT: return 4;
        case ANSWER_WORD_ORDER: return 3;
        case ANSWER_DIACRITICS: return 2;
        case ANSWER_CLOSE: return 1;
        default: return 0;
    }
}

/**
 * Grades a typed answer against one or more accepted answers, most
 * generous verdict first. Kind feedback matters more than strictness
 * here: a missing ł is a different lesson from a wrong case ending.
 */
export function checkAnswer(typed, ...accepted) {
    const got = normalizeSpoken(typed);
    if (got === "" || accepted == null) {
        return ANSWER_WRONG;
    }
    let best = ANSWER_WRONG;
    for (const option of accepted) {
        const want = normalizeSpoken(option);
        if (want === "") {
            continue;
        }
        if (got === want) {
            return ANSWER_EXACT;
        }
        if (stripPolishDiacritics(got) === stripPolishDiacritics(want)) {
            best = rank(best) >= rank(ANSWER_DIACRITICS) ? best : ANSWER_DIACRITICS;
        } else if (want.includes(" ") && sortedWords(got) === sortedWords(want)) {
            best = rank(best) >= rank(ANSWER_WORD_ORDER) ? best : ANSWER_WORD_ORDER;
        } else if (pronunciationScore(want, got) >= 80) {
            best = rank(best) >= rank(ANSWER_CLOSE) ? best : ANSWER_CLOSE;
        }
    }
    return best;
}

// ---- word notes --------------------------------------------------------

/**
 * A note pasted from an AI chat arrives as Markdown. The app shows plain
 * text, so this strips the markup that would otherwise show as stray
 * symbols: **bold**, # headings, `code`, and list bullets become "•".
 * The stored note keeps the original, so a Markdown export stays intact.
 */
export function plainNote(raw) {
    if (raw == null) {
        return "";
    }
    const lines = raw.replace(/\r\n/g, "\n").replace(/\r/g, "\n").split("\n");
    let out = "";
    for (const line of lines) {
        let l = line;
        l = l.replace(/^\s{0,3}#{1,6}\s+/, "");          // headings
        l = l.replace(/^(\s*)[-*+]\s+/, "$1• ");          // bullets
        l = l.replace(/\*\*/g, "").replace(/__/g, "");                // bold
        l = l.replace(/`/g, "");                                   // inline code
        if (/^[-*_]{3,}$/.test(l.trim())) {
            l = "";                                                // horizontal rule
        }
        out += l + "\n";
    }
    // Collapse the blank-line runs AI answers are full of.
    return out.replace(/\n{3,}/g, "\n\n").trim();
}

/** First part of a note for a compact preview, cut at a word boundary. */
export function notePreview(raw, maxChars) {
    const plain = plainNote(raw);
    if (plain.length <= maxChars) {
        return plain;
    }
    let cut = plain.lastIndexOf(' ', maxChars);
    if (cut < Math.floor(maxChars / 2)) {
        cut = maxChars;
    }
    return plain.substring(0, cut).trim() + "…";
}

/** Normalization used for dictionary keys and duplicate detection. */
export function normalizeHeadword(value) {
    if (value == null) {
        return "";
    }
    const s = value.trim().toLowerCase();
    return s.replace(/[.!?,;:]+$/, "");
}
