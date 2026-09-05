package com.mustardseed.polish4beginners;

/**
 * Pure logic extracted from MainActivity so it can be unit tested without an
 * Android device. Behaviour here is deliberately identical to what shipped
 * before extraction — in particular the Leitner intervals and the card key
 * format, both of which are baked into existing users' saved progress.
 *
 * Changing anything in this class can invalidate user data. Do not alter the
 * card key format or the stored memory encoding without a migration.
 */
public final class LearningLogic {

    private LearningLogic() {
    }

    public static final long DAY_MS = 24L * 60L * 60L * 1000L;

    /** Box 0 = new; boxes 1..5 are reviewed at growing intervals. */
    public static final long[] BOX_INTERVALS_MS = {
            0, DAY_MS, 3 * DAY_MS, 7 * DAY_MS, 16 * DAY_MS, 35 * DAY_MS
    };

    public static final int MAX_BOX = BOX_INTERVALS_MS.length - 1;

    public static final int STATUS_NEW = 0;
    public static final int STATUS_DUE = 1;
    public static final int STATUS_SCHEDULED = 2;

    /**
     * Persistent card identity. Cards carrying a coreIndex keep it forever;
     * everything else is identified by category + Polish text.
     *
     * These strings are SharedPreferences keys for user progress — never
     * renumber coreIndex values and never change this format.
     */
    public static String cardKey(int coreIndex, String category, String polish) {
        if (coreIndex > 0) {
            return "core:" + coreIndex;
        }
        return category + ":" + polish;
    }

    /** Next box after an answer: correct promotes (capped), a miss resets to 1. */
    public static int nextBox(int currentBox, boolean got) {
        if (!got) {
            return 1;
        }
        int box = currentBox + 1;
        return Math.min(box, MAX_BOX);
    }

    /** When the card should next appear. A miss is due immediately. */
    public static long nextDueAt(int nextBox, boolean got, long now) {
        if (!got) {
            return now;
        }
        return now + BOX_INTERVALS_MS[nextBox];
    }

    public static int statusOf(int box, long dueAt, long now) {
        if (box <= 0) {
            return STATUS_NEW;
        }
        return dueAt <= now ? STATUS_DUE : STATUS_SCHEDULED;
    }

    public static boolean isDue(int box, long dueAt, long now) {
        return box > 0 && dueAt <= now;
    }

    /** Serialized form of a card's memory: "box|dueAtMillis". */
    public static String encodeMemory(int box, long dueAt) {
        return box + "|" + dueAt;
    }

    /**
     * Parses stored memory, tolerating the pre-scheduling formats that older
     * installs wrote ("new" / "learnt" / "forgot"). Returns {box, dueAt}.
     */
    public static long[] decodeMemory(String stored, long now) {
        if (stored == null || stored.isEmpty() || "new".equals(stored)) {
            return new long[]{0, 0};
        }
        if ("learnt".equals(stored)) {
            return new long[]{2, now + BOX_INTERVALS_MS[2]};
        }
        if ("forgot".equals(stored)) {
            return new long[]{1, now};
        }
        int split = stored.indexOf('|');
        if (split > 0) {
            try {
                int box = Math.max(0, Math.min(MAX_BOX, Integer.parseInt(stored.substring(0, split))));
                long dueAt = Long.parseLong(stored.substring(split + 1));
                return new long[]{box, dueAt};
            } catch (NumberFormatException ignored) {
                // fall through to "new"
            }
        }
        return new long[]{0, 0};
    }

    /** True when the remote data manifest advertises something newer. */
    public static boolean isNewerDataVersion(int remoteVersion, int localVersion) {
        return remoteVersion > localVersion;
    }

    /**
     * Escapes one value for a Markdown table cell: a pipe would end the cell
     * and any line break would end the row.
     *
     * Note the character class rather than \R, which needs API 24 while this
     * app still supports API 23.
     */
    public static String markdownCell(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("|", "\\|").replaceAll("[\\r\\n]+", " ").replaceAll(" {2,}", " ").trim();
    }

    /** Normalization used for dictionary keys and duplicate detection. */
    public static String normalizeHeadword(String value) {
        if (value == null) {
            return "";
        }
        String s = value.trim().toLowerCase(java.util.Locale.ROOT);
        return s.replaceAll("[.!?,;:]+$", "");
    }
}
