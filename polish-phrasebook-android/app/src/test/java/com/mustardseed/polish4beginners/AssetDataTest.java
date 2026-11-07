package com.mustardseed.polish4beginners;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates the JSON that ships in assets/. These run on the desktop JVM
 * against the real files, so a malformed or progress-breaking data edit fails
 * the build instead of shipping.
 */
public class AssetDataTest {

    private static JSONArray asset(String name) throws Exception {
        File f = new File("src/main/assets/" + name);
        assertTrue("missing asset: " + name, f.exists());
        return new JSONArray(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }

    // ---- phrases.json -----------------------------------------------------

    @Test
    public void phrasesParseAndHaveRequiredFields() throws Exception {
        JSONArray cards = asset("phrases.json");
        assertTrue("deck looks too small", cards.length() > 3000);
        for (int i = 0; i < cards.length(); i++) {
            JSONObject c = cards.getJSONObject(i);
            assertFalse("empty polish at " + i, c.optString("polish").trim().isEmpty());
            assertFalse("empty english at " + i, c.optString("english").trim().isEmpty());
            assertFalse("empty level at " + i, c.optString("level").trim().isEmpty());
        }
    }

    /**
     * coreIndex values are persistent user-progress identities. Duplicates
     * would make two cards share one progress record.
     */
    @Test
    public void coreIndexValuesAreUnique() throws Exception {
        JSONArray cards = asset("phrases.json");
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < cards.length(); i++) {
            int core = cards.getJSONObject(i).optInt("coreIndex", 0);
            if (core > 0) {
                assertTrue("duplicate coreIndex " + core, seen.add(core));
            }
        }
        assertFalse("expected some coreIndex cards", seen.isEmpty());
    }

    /** Every card must produce a distinct persistent key. */
    @Test
    public void cardKeysAreUnique() throws Exception {
        JSONArray cards = asset("phrases.json");
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < cards.length(); i++) {
            JSONObject c = cards.getJSONObject(i);
            String key = LearningLogic.cardKey(
                    c.optInt("coreIndex", 0),
                    c.optString("scenario", c.optString("category", "General Core")),
                    c.optString("polish"));
            assertTrue("duplicate card key: " + key, keys.add(key));
        }
    }

    @Test
    public void levelsAreValidCefrValues() throws Exception {
        JSONArray cards = asset("phrases.json");
        Set<String> allowed = new HashSet<>();
        allowed.add("A1"); allowed.add("A2"); allowed.add("B1");
        allowed.add("B2"); allowed.add("C1");
        for (int i = 0; i < cards.length(); i++) {
            String level = cards.getJSONObject(i).optString("level");
            assertTrue("unexpected level: " + level, allowed.contains(level));
        }
    }

    // ---- dialogs.json -----------------------------------------------------

    @Test
    public void dialogsParseAndAreWellFormed() throws Exception {
        JSONArray dialogs = asset("dialogs.json");
        assertTrue(dialogs.length() > 0);
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < dialogs.length(); i++) {
            JSONObject d = dialogs.getJSONObject(i);
            String id = d.optString("id");
            assertFalse("dialog without id at " + i, id.trim().isEmpty());
            assertTrue("duplicate dialog id: " + id, ids.add(id));

            JSONArray lines = d.optJSONArray("lines");
            assertNotNull("dialog " + id + " has no lines", lines);
            assertTrue("dialog " + id + " is empty", lines.length() > 0);

            JSONObject roles = d.optJSONObject("roles");
            for (int j = 0; j < lines.length(); j++) {
                JSONObject line = lines.getJSONObject(j);
                assertFalse(id + " line " + j + " has no polish",
                        line.optString("polish").trim().isEmpty());
                if (roles != null) {
                    String speaker = line.optString("speaker", "A");
                    assertTrue(id + " line " + j + " speaker '" + speaker + "' not in roles",
                            roles.has(speaker));
                }
            }
        }
    }

    // ---- grammar + alphabet ----------------------------------------------

    @Test
    public void grammarLessonsParse() throws Exception {
        JSONArray lessons = asset("grammar_lessons.json");
        assertTrue(lessons.length() > 0);
        for (int i = 0; i < lessons.length(); i++) {
            JSONObject l = lessons.getJSONObject(i);
            assertFalse(l.optString("unit").trim().isEmpty());
            assertFalse(l.optString("topic").trim().isEmpty());
        }
    }

    @Test
    public void alphabetParsesAndCoversPolishDigraphs() throws Exception {
        JSONArray letters = asset("alphabet.json");
        assertTrue(letters.length() > 30);
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < letters.length(); i++) {
            JSONObject a = letters.getJSONObject(i);
            assertFalse(a.optString("letter").trim().isEmpty());
            all.append(a.optString("letter")).append(' ');
        }
        String joined = all.toString();
        for (String digraph : new String[]{"Sz", "Cz", "Rz", "Dz"}) {
            assertTrue("missing digraph " + digraph, joined.contains(digraph));
        }
    }

    // ---- grammar guide ----------------------------------------------------

    private static JSONObject guide() throws Exception {
        File f = new File("src/main/assets/grammar_guide.json");
        assertTrue("missing grammar_guide.json", f.exists());
        return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void guideParsesIntoChaptersWithKnownBlockTypes() throws Exception {
        JSONArray chapters = guide().getJSONArray("chapters");
        assertTrue("expected the guide's chapters", chapters.length() >= 16);
        Set<String> known = new HashSet<>(java.util.Arrays.asList(
                "h", "p", "list", "table", "dialogue", "exercise", "study"));
        for (int i = 0; i < chapters.length(); i++) {
            JSONObject ch = chapters.getJSONObject(i);
            assertFalse("chapter without title", ch.optString("title").trim().isEmpty());
            JSONArray blocks = ch.getJSONArray("blocks");
            assertTrue("empty chapter " + ch.optString("id"), blocks.length() > 0);
            for (int j = 0; j < blocks.length(); j++) {
                String type = blocks.getJSONObject(j).optString("type");
                assertTrue("unknown block type '" + type + "'", known.contains(type));
            }
        }
    }

    /** A practice item without an answer would mark every reply wrong. */
    @Test
    public void everyGuideExerciseHasAnAnswer() throws Exception {
        JSONArray chapters = guide().getJSONArray("chapters");
        int items = 0;
        for (int i = 0; i < chapters.length(); i++) {
            JSONArray blocks = chapters.getJSONObject(i).getJSONArray("blocks");
            for (int j = 0; j < blocks.length(); j++) {
                JSONObject b = blocks.getJSONObject(j);
                if (!"exercise".equals(b.optString("type"))) {
                    continue;
                }
                JSONArray list = b.getJSONArray("items");
                for (int k = 0; k < list.length(); k++) {
                    JSONObject it = list.getJSONObject(k);
                    boolean has = (it.optJSONArray("a") != null && it.getJSONArray("a").length() > 0)
                            || (it.optJSONArray("alts") != null && it.getJSONArray("alts").length() > 0);
                    assertTrue(b.optString("group") + (k + 1) + " has no answer", has);
                    items++;
                }
            }
        }
        assertTrue("expected the guide's practice items", items >= 30);
    }

    /** "Study these words" must find a card for every word it lists. */
    @Test
    public void everyGuideStudyWordHasACard() throws Exception {
        JSONArray cards = asset("phrases.json");
        Set<String> deck = new HashSet<>();
        for (int i = 0; i < cards.length(); i++) {
            deck.add(LearningLogic.normalizeHeadword(cards.getJSONObject(i).optString("polish")));
        }
        JSONArray chapters = guide().getJSONArray("chapters");
        for (int i = 0; i < chapters.length(); i++) {
            JSONArray blocks = chapters.getJSONObject(i).getJSONArray("blocks");
            for (int j = 0; j < blocks.length(); j++) {
                JSONObject b = blocks.getJSONObject(j);
                if ("study".equals(b.optString("type"))) {
                    JSONArray entries = b.getJSONArray("entries");
                    for (int k = 0; k < entries.length(); k++) {
                        String w = entries.getString(k);
                        assertTrue("no card for guide word '" + w + "'",
                                deck.contains(LearningLogic.normalizeHeadword(w)));
                    }
                }
            }
        }
    }

    /**
     * Every lesson points at real blocks, and every multiple-choice question
     * can be answered: its answer is one of its options, with no duplicates.
     */
    @Test
    public void courseLessonsAndQuestionsAreWellFormed() throws Exception {
        JSONObject g = guide();
        JSONArray chapters = g.getJSONArray("chapters");
        JSONArray units = g.getJSONObject("course").getJSONArray("units");
        Set<String> ids = new HashSet<>();
        int lessons = 0;
        int questions = 0;
        for (int u = 0; u < units.length(); u++) {
            JSONObject unit = units.getJSONObject(u);
            JSONArray blocks = chapters.getJSONObject(unit.getInt("chapter")).getJSONArray("blocks");
            JSONArray ls = unit.getJSONArray("lessons");
            for (int l = 0; l < ls.length(); l++) {
                JSONObject lesson = ls.getJSONObject(l);
                assertTrue("duplicate lesson id " + lesson.optString("id"), ids.add(lesson.optString("id")));
                JSONArray refs = lesson.getJSONArray("refs");
                assertTrue("empty lesson " + lesson.optString("id"), refs.length() > 0);
                for (int r = 0; r < refs.length(); r++) {
                    int b = refs.getJSONObject(r).getInt("b");
                    assertTrue("lesson " + lesson.optString("id") + " points past its chapter",
                            b >= 0 && b < blocks.length());
                }
                JSONArray quiz = lesson.getJSONArray("quiz");
                for (int q = 0; q < quiz.length(); q++) {
                    JSONObject item = quiz.getJSONObject(q);
                    if ("mc".equals(item.optString("kind"))) {
                        JSONArray options = item.getJSONArray("options");
                        Set<String> seen = new HashSet<>();
                        boolean hasAnswer = false;
                        for (int o = 0; o < options.length(); o++) {
                            assertTrue("duplicate option in " + item.optString("prompt"), seen.add(options.getString(o)));
                            hasAnswer |= options.getString(o).equals(item.getString("answer"));
                        }
                        assertTrue("answer not offered: " + item.optString("prompt"), hasAnswer);
                        assertTrue("too few options: " + item.optString("prompt"), options.length() >= 2);
                    } else {
                        assertTrue("typed question without answers: " + item.optString("prompt"),
                                item.getJSONArray("accepted").length() > 0);
                    }
                    questions++;
                }
                lessons++;
            }
        }
        assertTrue("expected the course's lessons", lessons >= 40);
        assertTrue("expected the course's questions", questions >= 200);
    }

    /**
     * The app prefers a downloaded deck only when it is newer than the
     * bundled one, so the bundled version constant must match the manifest
     * that ships beside it, or new bundled cards would be ignored.
     */
    @Test
    public void bundledDataVersionMatchesTheManifest() throws Exception {
        File manifest = new File("../../docs/database.json");
        if (!manifest.exists()) {
            return;
        }
        String source = new String(Files.readAllBytes(new File(
                "src/main/java/com/mustardseed/polish4beginners/MainActivity.java").toPath()),
                StandardCharsets.UTF_8);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("DEFAULT_DATABASE_VERSION = (\\d+);").matcher(source);
        assertTrue("DEFAULT_DATABASE_VERSION not found", m.find());
        int bundled = Integer.parseInt(m.group(1));
        int published = new JSONObject(new String(Files.readAllBytes(manifest.toPath()),
                StandardCharsets.UTF_8)).optInt("dataVersion", -1);
        assertEquals("DEFAULT_DATABASE_VERSION must equal docs/database.json dataVersion",
                published, bundled);
    }

    // ---- data manifest ----------------------------------------------------

    /**
     * docs/phrases.json is what shipped devices download; it must stay
     * byte-identical to the bundled asset, and the manifest must describe it.
     */
    @Test
    public void publishedDataMatchesBundledAssetAndManifest() throws Exception {
        File asset = new File("src/main/assets/phrases.json");
        File published = new File("../../docs/phrases.json");
        File manifest = new File("../../docs/database.json");
        if (!published.exists() || !manifest.exists()) {
            return; // docs/ not present in this checkout; nothing to verify
        }
        byte[] a = Files.readAllBytes(asset.toPath());
        byte[] b = Files.readAllBytes(published.toPath());
        assertTrue("assets/phrases.json and docs/phrases.json have diverged",
                java.util.Arrays.equals(a, b));

        JSONObject db = new JSONObject(new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8));
        int declaredCount = db.optInt("phraseCount", -1);
        int actualCount = new JSONArray(new String(b, StandardCharsets.UTF_8)).length();
        assertEquals("database.json phraseCount is stale", actualCount, declaredCount);
        assertEquals("database.json phrasesSizeBytes is stale", b.length, db.optInt("phrasesSizeBytes", -1));
    }
}
