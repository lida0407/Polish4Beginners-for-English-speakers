package com.example.polishphrasebook;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final String PREFS = "phrasebook";
    private static final String MEMORY_PREFIX = "memory:";
    private static final String STATUS_NEW = "new";
    private static final String STATUS_FORGOT = "forgot";
    private static final String STATUS_LEARNT = "learnt";

    private static final String SCREEN_HOME = "home";
    private static final String SCREEN_SESSION = "session";
    private static final String SCREEN_BROWSE = "browse";
    private static final String SCREEN_GRAMMAR = "grammar";
    private static final String SCREEN_ALPHABET = "alphabet";
    private static final String SCREEN_SETTINGS = "settings";
    private static final String DEFAULT_THEME = "Klasyczny";
    private static final String LANG_EN = "en";
    private static final String LANG_PL = "pl";
    private static final String SPEED_SLOW = "slow";
    private static final String SPEED_NORMAL = "normal";
    private static final String SPEED_FAST = "fast";
    private static final int SESSION_SIZE = 10;

    private final List<Phrase> phrases = new ArrayList<>();
    private final List<GrammarLesson> grammarLessons = new ArrayList<>();
    private final List<AlphabetItem> alphabet = new ArrayList<>();
    private final Map<String, String> memory = new HashMap<>();
    private final List<Phrase> sessionDeck = new ArrayList<>();
    private final Map<String, Theme> themes = new LinkedHashMap<>();

    private String screen = SCREEN_HOME;
    private String level = "A1";
    private String themeName = DEFAULT_THEME;
    private String interfaceLanguage = LANG_EN;
    private String speechSpeed = SPEED_NORMAL;
    private String browseTopic = "All";
    private String browseQuery = "";
    private String openLessonUnit = null;
    private int browseLimit = 25;
    private int sessionIndex = 0;
    private int sessionGot = 0;
    private boolean sessionRevealed = false;
    private boolean ttsReady = false;
    private TextToSpeech textToSpeech;
    private Typeface sansRegular;
    private Typeface sansMedium;
    private Typeface sansSemiBold;
    private Typeface sansBold;
    private Typeface serifBold;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildThemes();
        themeName = getSharedPreferences(PREFS, MODE_PRIVATE).getString("theme", DEFAULT_THEME);
        if (!themes.containsKey(themeName)) {
            themeName = DEFAULT_THEME;
        }
        interfaceLanguage = getSharedPreferences(PREFS, MODE_PRIVATE).getString("interfaceLanguage", LANG_EN);
        if (!LANG_PL.equals(interfaceLanguage)) {
            interfaceLanguage = LANG_EN;
        }
        speechSpeed = getSharedPreferences(PREFS, MODE_PRIVATE).getString("speechSpeed", SPEED_NORMAL);
        if (!SPEED_SLOW.equals(speechSpeed) && !SPEED_FAST.equals(speechSpeed)) {
            speechSpeed = SPEED_NORMAL;
        }
        loadFonts();
        loadPhrases();
        loadGrammarLessons();
        loadAlphabet();
        loadMemory();
        textToSpeech = new TextToSpeech(this, this);
        render();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            int result = textToSpeech.setLanguage(new Locale("pl", "PL"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Install a Polish TTS voice for Polish reading.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (SCREEN_SESSION.equals(screen)) {
            screen = SCREEN_HOME;
            render();
            return;
        }
        if (!SCREEN_HOME.equals(screen)) {
            screen = SCREEN_HOME;
            render();
            return;
        }
        super.onBackPressed();
    }

    private void render() {
        Theme theme = theme();
        getWindow().setStatusBarColor(theme.bg);
        getWindow().setNavigationBarColor(theme.panel);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(isDarkTheme() ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(theme.bg);

        if (SCREEN_SESSION.equals(screen)) {
            renderSession(root);
        } else {
            ScrollView scrollView = new ScrollView(this);
            scrollView.setFillViewport(false);
            LinearLayout content = vertical();
            content.setPadding(dp(20), dp(16), dp(20), dp(24));
            scrollView.addView(content, new ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT
            ));
            root.addView(scrollView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1
            ));

            if (SCREEN_HOME.equals(screen)) {
                renderHome(content);
            } else if (SCREEN_BROWSE.equals(screen)) {
                renderBrowse(content);
            } else if (SCREEN_GRAMMAR.equals(screen)) {
                renderGrammar(content);
            } else if (SCREEN_ALPHABET.equals(screen)) {
                renderAlphabet(content);
            } else {
                renderSettings(content);
            }
            root.addView(bottomNav());
        }

        setContentView(root);
    }

    private void renderHome(LinearLayout content) {
        Theme th = theme();
        content.setPadding(dp(20), dp(16), dp(20), dp(24));
        content.addView(masthead());
        addGap(content, 16);

        LinearLayout hero = vertical();
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setBackground(rounded(th.panel, th.ink, 4, 1.5f));

        LinearLayout heroMeta = row();
        TextView kicker = label(t("TODAY'S LESSON", "DZISIEJSZA LEKCJA"), th.accent2, 11, 0.14f);
        heroMeta.addView(kicker, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        heroMeta.addView(levelBadge());
        hero.addView(heroMeta);
        addGap(hero, 10);

        TextView headline = serifText(firstVisit() ? t("Your first 10 phrases", "Twoje pierwsze 10 fraz") : t("10 flashcards, level ", "10 fiszek, poziom ") + level, 23, th.ink);
        headline.setLineSpacing(0, 1.05f);
        hero.addView(headline);
        addGap(hero, 10);
        hero.addView(new DashedLine(this, th.dash), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(2)
        ));
        addGap(hero, 10);
        hero.addView(bodyText(t("Flip each card, say it out loud, mark what stuck. ", "Odwróć każdą kartę, powiedz ją na głos i zaznacz, co pamiętasz. ") + dueCountForLevel() + t(" cards still waiting at this level.", " kart czeka na tym poziomie."), 13, th.muted));
        Button start = filledButton(t("Zaczynamy — start session", "Zaczynamy — start"), th.accent, th.onAccent, 16, 52);
        start.setOnClickListener(v -> startSession("All"));
        hero.addView(start, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52), 14));
        content.addView(shadowWrap(hero, 4));
        addGap(content, 16);

        content.addView(levelSelector());
        addGap(content, 16);
        content.addView(statsStrip());
        addGap(content, 18);
        content.addView(topicsSection());
    }

    private View masthead() {
        Theme th = theme();
        LinearLayout box = vertical();
        box.setPadding(0, 0, 0, dp(14));
        box.setBackground(bottomBorder(th.ink, 2));

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(label("TWÓJ DZIENNIK NAUKI", th.accent, 11, 0.18f), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView date = uiText(polishDate(), 11, th.faint, sansSemiBold);
        top.addView(date);
        box.addView(top);
        addGap(box, 6);

        LinearLayout titleRow = row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = serifText("Mój polski", 34, th.ink);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout swatches = row();
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        boolean first = true;
        for (String name : themes.keySet()) {
            ThemeSwatch swatch = new ThemeSwatch(this, themes.get(name), name.equals(themeName));
            swatch.setOnClickListener(v -> {
                themeName = name;
                saveSetting("theme", themeName);
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(24), dp(24));
            if (!first) {
                params.setMargins(dp(8), 0, 0, 0);
            }
            swatches.addView(swatch, params);
            first = false;
        }
        titleRow.addView(swatches);
        box.addView(titleRow);
        return box;
    }

    private View levelBadge() {
        Theme th = theme();
        TextView badge = uiText(level, 11, th.accent, sansBold);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(7), dp(2), dp(7), dp(2));
        badge.setBackground(rounded(Color.TRANSPARENT, th.accent, 3, 1.5f));
        return badge;
    }

    private View levelSelector() {
        Theme th = theme();
        LinearLayout row = row();
        String[] levels = {"A1", "A2", "B1", "B2", "C1"};
        for (String item : levels) {
            boolean selected = item.equals(level);
            Button chip = flatButton(item, selected ? th.ink : th.panel, selected ? th.bg : th.muted, selected ? th.ink : th.dash, 13, 36);
            chip.setOnClickListener(v -> {
                level = item;
                browseTopic = "All";
                browseLimit = 25;
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(36), 1);
            params.setMargins(0, 0, dp(8), 0);
            row.addView(chip, params);
        }
        return row;
    }

    private View statsStrip() {
        Theme th = theme();
        LinearLayout strip = row();
        strip.setBackground(rounded(th.panel, th.ink, 4, 1.5f));
        strip.setBaselineAligned(false);
        int[] counts = memoryCounts();
        strip.addView(statCell(String.valueOf(counts[0]), "New", th.ink, true), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        strip.addView(statCell(String.valueOf(counts[1]), "Learning", th.accent, true), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        strip.addView(statCell(String.valueOf(counts[2]), "Learnt", th.accent2, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return strip;
    }

    private View statCell(String count, String label, int color, boolean divider) {
        Theme th = theme();
        LinearLayout cell = vertical();
        cell.setPadding(dp(14), dp(12), dp(14), dp(12));
        cell.setBackground(divider ? rightBorder(th.softLine, 1.5f) : null);
        cell.addView(serifText(count, 22, color));
        TextView labelView = uiText(label.toUpperCase(Locale.ROOT), 11, th.faint, sansSemiBold);
        cell.addView(labelView);
        return cell;
    }

    private View topicsSection() {
        Theme th = theme();
        LinearLayout section = vertical();
        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(label(t("TOPICS", "ROZDZIAŁY"), th.faint, 11, 0.14f));
        View line = new View(this);
        line.setBackgroundColor(th.dash);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(0, dp(2), 1);
        lineParams.setMargins(dp(10), 0, 0, 0);
        heading.addView(line, lineParams);
        section.addView(heading);
        addGap(section, 10);

        for (TopicCount topic : topTopicsForLevel(6)) {
            Button button = flatButton(topic.name + "                                  " + topic.count + t(" cards →", " kart →"), th.panel, th.ink, th.ink, 14, 48);
            button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            button.setPadding(dp(14), 0, dp(14), 0);
            button.setOnClickListener(v -> startSession(topic.name));
            section.addView(button, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48), 0));
            addGap(section, 8);
        }
        return section;
    }

    private void renderSession(LinearLayout root) {
        Theme th = theme();
        LinearLayout content = vertical();
        content.setPadding(dp(20), dp(12), dp(20), dp(20));
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        if (sessionIndex >= sessionDeck.size()) {
            renderDone(content);
            return;
        }

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button close = flatButton("×", th.panel, th.ink, th.ink, 18, 36);
        close.setOnClickListener(v -> {
            screen = SCREEN_HOME;
            render();
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));
        StripeProgress progress = new StripeProgress(this, th, sessionDeck.isEmpty() ? 0 : (float) sessionIndex / (float) sessionDeck.size());
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(0, dp(10), 1);
        progressParams.setMargins(dp(12), 0, dp(12), 0);
        header.addView(progress, progressParams);
        header.addView(uiText(t("card ", "karta ") + (sessionIndex + 1) + "/" + sessionDeck.size(), 12, th.faint, sansBold));
        content.addView(header);
        addGap(content, 14);

        Phrase card = sessionDeck.get(sessionIndex);
        LinearLayout face = vertical();
        face.setGravity(Gravity.CENTER);
        face.setPadding(dp(24), dp(28), dp(24), dp(28));
        face.setBackground(rounded(th.panel, th.ink, 4, 1.5f));
        face.setOnClickListener(v -> {
            if (!sessionRevealed) {
                sessionRevealed = true;
                render();
            }
        });

        LinearLayout meta = row();
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(label(card.level + " · " + card.category, th.accent2, 10.5f, 0.12f), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        meta.addView(uiText("Nr " + (sessionIndex + 1), 10.5f, th.ghost, sansBold));
        face.addView(meta, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        SpaceView topSpace = new SpaceView(this);
        face.addView(topSpace, new LinearLayout.LayoutParams(1, 0, 1));
        TextView polish = serifText(card.polish, 33, th.ink);
        polish.setGravity(Gravity.CENTER);
        polish.setLineSpacing(0, 1.02f);
        face.addView(polish, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        addGap(face, 14);

        if (sessionRevealed) {
            DashedLine divider = new DashedLine(this, th.ghost);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(52), dp(2));
            face.addView(divider, dividerParams);
            addGap(face, 14);
            TextView english = uiText(card.english, 18, th.body, sansMedium);
            english.setGravity(Gravity.CENTER);
            english.setLineSpacing(0, 1.08f);
            face.addView(english);
            if (!card.phonetic.isEmpty()) {
                TextView phonetic = uiText(card.phonetic, 13.5f, th.faint, sansRegular);
                phonetic.setTypeface(Typeface.create(sansRegular, Typeface.ITALIC));
                phonetic.setGravity(Gravity.CENTER);
                face.addView(phonetic, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));
            }
            LinearLayout tts = row();
            tts.setGravity(Gravity.CENTER);
            Button readPl = flatButton(t("Read PL", "Czytaj PL"), th.accentSoft, th.accent, th.accent, 12.5f, 38);
            readPl.setOnClickListener(v -> speak(card.polish, new Locale("pl", "PL")));
            tts.addView(readPl, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)));
            Button readEn = flatButton("Read EN", th.panel, th.muted, th.dash, 12.5f, 38);
            readEn.setOnClickListener(v -> speak(card.english, Locale.US));
            LinearLayout.LayoutParams readEnParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
            readEnParams.setMargins(dp(10), 0, 0, 0);
            tts.addView(readEn, readEnParams);
            face.addView(tts, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 12));
        } else {
            face.addView(label("ODWRÓĆ KARTĘ · TAP TO FLIP", th.ghost, 12, 0.08f));
        }
        SpaceView bottomSpace = new SpaceView(this);
        face.addView(bottomSpace, new LinearLayout.LayoutParams(1, 0, 1));

        ShadowLayout shadow = shadowWrap(face, 5, true);
        content.addView(shadow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        addGap(content, 14);

        if (sessionRevealed) {
            LinearLayout actions = row();
            Button miss = flatButton("Jeszcze nie", th.panel, th.accent, th.accent, 15, 54);
            miss.setOnClickListener(v -> answer(false));
            actions.addView(miss, new LinearLayout.LayoutParams(0, dp(54), 1));
            Button got = flatButton("Umiem!", th.accent2, th.onAccent2, th.ink, 15, 54);
            got.setOnClickListener(v -> answer(true));
            LinearLayout.LayoutParams gotParams = new LinearLayout.LayoutParams(0, dp(54), 1);
            gotParams.setMargins(dp(12), 0, 0, 0);
            actions.addView(got, gotParams);
            content.addView(actions);
        } else {
            Button reveal = flatButton("Pokaż odpowiedź", th.ink, th.bg, th.ink, 15, 54);
            reveal.setOnClickListener(v -> {
                sessionRevealed = true;
                render();
            });
            content.addView(reveal, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
        }
    }

    private void renderDone(LinearLayout content) {
        Theme th = theme();
        LinearLayout done = vertical();
        done.setGravity(Gravity.CENTER);
        done.setPadding(dp(24), dp(24), dp(24), dp(24));
        content.addView(done, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        done.addView(label(t("WELL DONE!", "BRAWO!"), th.accent, 11, 0.18f));
        done.addView(serifText(t("Lesson finished", "Lekcja skończona"), 30, th.ink), topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 14));
        TextView summary = bodyText(t("You knew ", "Znasz ") + sessionGot + t(" of ", " z ") + sessionDeck.size() + t(" cards.\nThe rest come back next lesson.", " kart.\nReszta wróci w następnej lekcji."), 14, th.muted);
        summary.setGravity(Gravity.CENTER);
        done.addView(summary, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 14));
        Button again = flatButton(t("Again", "Jeszcze raz"), th.accent, th.onAccent, th.ink, 15, 50);
        again.setOnClickListener(v -> startSession("All"));
        done.addView(again, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(50), 18));
        Button home = textButton(t("Back home", "Wróć do domu"), th.faint, 13);
        home.setPaintFlags(home.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        home.setOnClickListener(v -> {
            screen = SCREEN_HOME;
            render();
        });
        done.addView(home, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40), 8));
    }

    private void renderBrowse(LinearLayout content) {
        Theme th = theme();
        content.addView(screenTitle(t("Card Catalog", "Katalog kart")));
        addGap(content, 14);

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setText(browseQuery);
        search.setHint(t("Search: dworzec, to eat…", "Szukaj: dworzec, to eat…"));
        search.setTextSize(15);
        search.setTypeface(sansRegular);
        search.setTextColor(th.ink);
        search.setHintTextColor(th.faint);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(rounded(th.panel, th.ink, 4, 1.5f));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                browseQuery = s.toString();
                browseLimit = 25;
                search.post(() -> {
                    if (SCREEN_BROWSE.equals(screen)) {
                        render();
                    }
                });
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        content.addView(search, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));
        addGap(content, 14);

        content.addView(topicChips());
        addGap(content, 10);

        List<Phrase> filtered = browseCards();
        content.addView(label(filtered.size() + t(" CARDS · LEVEL ", " KART · POZIOM ") + level, th.faint, 11.5f, 0.08f));
        addGap(content, 10);

        int limit = Math.min(browseLimit, filtered.size());
        for (int i = 0; i < limit; i++) {
            content.addView(browseRow(filtered.get(i)));
            addGap(content, 8);
        }

        if (filtered.size() > browseLimit) {
            Button more = flatButton(t("Show more", "Pokaż więcej"), th.panel, th.ink, th.ink, 13, 44);
            more.setOnClickListener(v -> {
                browseLimit += 25;
                render();
            });
            content.addView(more, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44), 4));
        }
    }

    private View topicChips() {
        Theme th = theme();
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = row();
        scroll.addView(row);
        List<String> topics = new ArrayList<>();
        topics.add("All");
        for (TopicCount topic : allTopicsForLevel()) {
            topics.add(topic.name);
        }
        for (String topic : topics) {
            boolean selected = topic.equals(browseTopic);
            Button chip = flatButton(topic, selected ? th.ink : th.panel, selected ? th.bg : th.muted, selected ? th.ink : th.dash, 12, 32);
            chip.setAllCaps(true);
            chip.setOnClickListener(v -> {
                browseTopic = topic;
                browseLimit = 25;
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
            params.setMargins(0, 0, dp(8), 0);
            row.addView(chip, params);
        }
        return scroll;
    }

    private View browseRow(Phrase phrase) {
        Theme th = theme();
        LinearLayout outer = row();
        outer.setGravity(Gravity.CENTER_VERTICAL);
        outer.setBackground(rounded(th.panel, th.softLine, 3, 1.5f));

        View status = new View(this);
        status.setBackgroundColor(statusColor(phrase));
        outer.addView(status, new LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout copy = vertical();
        copy.setPadding(dp(10), dp(11), dp(10), dp(11));
        TextView polish = serifText(phrase.polish, 16.5f, th.ink);
        polish.setSingleLine(true);
        polish.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(polish);
        TextView english = uiText(phrase.english, 12.5f, th.faint, sansRegular);
        english.setSingleLine(true);
        english.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(english);
        outer.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button read = flatButton("PL ▸", Color.TRANSPARENT, th.accent, th.dash, 11, 32);
        read.setOnClickListener(v -> speak(phrase.polish, new Locale("pl", "PL")));
        LinearLayout.LayoutParams readParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
        readParams.setMargins(0, 0, dp(10), 0);
        outer.addView(read, readParams);

        return outer;
    }

    private void renderGrammar(LinearLayout content) {
        Theme th = theme();
        content.addView(screenTitle(t("Grammar", "Gramatyka")));
        addGap(content, 12);
        content.addView(bodyText(t("Short lessons in reading order, each with a self-check.", "Krótkie lekcje w kolejności nauki, każda z auto-sprawdzeniem."), 13, th.muted));
        addGap(content, 12);

        for (GrammarLesson lesson : grammarLessons) {
            content.addView(grammarCard(lesson));
            addGap(content, 12);
        }
    }

    private View grammarCard(GrammarLesson lesson) {
        Theme th = theme();
        boolean open = lesson.unit.equals(openLessonUnit);
        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(th.panel, th.ink, 4, 1.5f));

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView unit = uiText(lesson.unit, 10.5f, th.onAccent2, sansBold);
        unit.setPadding(dp(7), dp(3), dp(7), dp(3));
        unit.setBackground(rounded(th.accent2, th.accent2, 2, 1));
        header.addView(unit);
        TextView scenario = label(lesson.scenario, th.faint, 11, 0.06f);
        scenario.setGravity(Gravity.RIGHT);
        header.addView(scenario, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        card.addView(header);
        addGap(card, 8);
        card.addView(serifText(lesson.topic, 18, th.ink));
        addGap(card, 6);
        card.addView(bodyText(lesson.focus, 13, th.muted));

        if (open) {
            addGap(card, 10);
            TextView rule = bodyText(t("Rule. ", "Zasada. ") + lesson.rule, 13, th.body);
            rule.setPadding(dp(12), dp(10), dp(12), dp(10));
            rule.setBackground(leftBorderBox(th.bg, th.accent, 3));
            card.addView(rule);
            addGap(card, 8);
            card.addView(bodyText(t("Pattern. ", "Wzór. ") + lesson.pattern, 13, th.body));
            addGap(card, 8);
            for (GrammarExample example : lesson.examples) {
                LinearLayout ex = vertical();
                ex.setPadding(dp(10), 0, 0, 0);
                ex.setBackground(leftBorderOnly(th.dash, 2));
                ex.addView(serifText(example.polish, 14.5f, th.ink));
                ex.addView(uiText(example.english, 12, th.faint, sansRegular));
                card.addView(ex);
                addGap(card, 6);
            }
            card.addView(new DashedLine(this, th.dash), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));
            addGap(card, 10);
            card.addView(uiText(t("Check yourself: ", "Sprawdź się: ") + lesson.checkPrompt, 13, th.ink, sansBold));
            addGap(card, 5);
            card.addView(uiText(t("Answer: ", "Odpowiedź: ") + lesson.checkAnswer + (lesson.checkHint.isEmpty() ? "" : " — " + lesson.checkHint), 12.5f, th.accent2Text, sansBold));
            Button read = flatButton(t("Read examples", "Czytaj przykłady"), th.accentSoft, th.accent, th.accent, 12, 38);
            read.setOnClickListener(v -> speak(lesson.polishExamples(), new Locale("pl", "PL")));
            card.addView(read, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38), 10));
        }

        Button toggle = flatButton(open ? t("Close lesson", "Zamknij lekcję") : t("Open lesson", "Otwórz lekcję"), Color.TRANSPARENT, th.muted, th.dash, 12, 38);
        toggle.setOnClickListener(v -> {
            openLessonUnit = open ? null : lesson.unit;
            render();
        });
        card.addView(toggle, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38), 10));
        return card;
    }

    private void renderAlphabet(LinearLayout content) {
        Theme th = theme();
        content.addView(screenTitle(t("Alphabet and Sounds", "Alfabet i dźwięki")));
        addGap(content, 12);
        content.addView(bodyText(t("Tap a tile to hear the letter and example word.", "Dotknij kafelka, aby usłyszeć literę i przykład."), 13, th.muted));
        addGap(content, 14);

        for (int i = 0; i < alphabet.size(); i += 2) {
            LinearLayout row = row();
            row.addView(alphabetTile(alphabet.get(i)), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            if (i + 1 < alphabet.size()) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                params.setMargins(dp(10), 0, 0, 0);
                row.addView(alphabetTile(alphabet.get(i + 1)), params);
            } else {
                SpaceView spacer = new SpaceView(this);
                row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
            }
            content.addView(row);
            addGap(content, 10);
        }
    }

    private View alphabetTile(AlphabetItem item) {
        Theme th = theme();
        LinearLayout tile = vertical();
        tile.setPadding(dp(14), dp(12), dp(14), dp(12));
        tile.setBackground(rounded(th.panel, th.ink, 4, 1.5f));
        tile.setOnClickListener(v -> speakAlphabetItem(item));
        tile.addView(serifText(item.letter, 21, th.ink));
        tile.addView(uiText(item.sound, 11.5f, th.accent, sansBold));
        tile.addView(uiText(item.example + " · " + item.english, 12, th.faint, sansRegular));
        return tile;
    }

    private void renderSettings(LinearLayout content) {
        Theme th = theme();
        content.addView(screenTitle(t("Settings", "Ustawienia")));
        addGap(content, 12);
        content.addView(bodyText(t("Personalize the interface, theme, and read-aloud speed.", "Dostosuj język interfejsu, motyw i szybkość czytania."), 13, th.muted));
        addGap(content, 14);

        LinearLayout language = settingsCard(t("Interface Language", "Język interfejsu"), t("Choose the app labels and instructions language.", "Wybierz język etykiet i instrukcji aplikacji."));
        LinearLayout languageRow = row();
        languageRow.addView(settingChoice("English", LANG_EN.equals(interfaceLanguage), () -> {
            interfaceLanguage = LANG_EN;
            saveSetting("interfaceLanguage", interfaceLanguage);
            render();
        }), new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams plParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        plParams.setMargins(dp(10), 0, 0, 0);
        languageRow.addView(settingChoice("Polski", LANG_PL.equals(interfaceLanguage), () -> {
            interfaceLanguage = LANG_PL;
            saveSetting("interfaceLanguage", interfaceLanguage);
            render();
        }), plParams);
        language.addView(languageRow, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42), 12));
        content.addView(language);
        addGap(content, 12);

        LinearLayout themeCard = settingsCard(t("Color Theme", "Motyw kolorystyczny"), t("The same five themes are available from the home header.", "Te same pięć motywów jest dostępne w nagłówku ekranu głównego."));
        LinearLayout themeRow = row();
        themeRow.setGravity(Gravity.CENTER_VERTICAL);
        boolean firstTheme = true;
        for (String name : themes.keySet()) {
            ThemeSwatch swatch = new ThemeSwatch(this, themes.get(name), name.equals(themeName));
            swatch.setOnClickListener(v -> {
                themeName = name;
                saveSetting("theme", themeName);
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(30), dp(30));
            if (!firstTheme) {
                params.setMargins(dp(12), 0, 0, 0);
            }
            themeRow.addView(swatch, params);
            firstTheme = false;
        }
        themeCard.addView(themeRow, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34), 12));
        themeCard.addView(uiText(themeName, 12.5f, th.faint, sansSemiBold), topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));
        content.addView(themeCard);
        addGap(content, 12);

        LinearLayout speed = settingsCard(t("Reading Speed", "Szybkość czytania"), t("Controls Polish and English TextToSpeech playback.", "Steruje odtwarzaniem TextToSpeech po polsku i angielsku."));
        LinearLayout speedRow = row();
        speedRow.addView(settingChoice(t("Slow", "Wolno"), SPEED_SLOW.equals(speechSpeed), () -> setSpeechSpeed(SPEED_SLOW)), new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams normalParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        normalParams.setMargins(dp(8), 0, 0, 0);
        speedRow.addView(settingChoice(t("Normal", "Normalnie"), SPEED_NORMAL.equals(speechSpeed), () -> setSpeechSpeed(SPEED_NORMAL)), normalParams);
        LinearLayout.LayoutParams fastParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        fastParams.setMargins(dp(8), 0, 0, 0);
        speedRow.addView(settingChoice(t("Fast", "Szybko"), SPEED_FAST.equals(speechSpeed), () -> setSpeechSpeed(SPEED_FAST)), fastParams);
        speed.addView(speedRow, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42), 12));
        content.addView(speed);
    }

    private LinearLayout settingsCard(String title, String description) {
        Theme th = theme();
        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(th.panel, th.ink, 4, 1.5f));
        card.addView(serifText(title, 19, th.ink));
        addGap(card, 5);
        card.addView(bodyText(description, 13, th.muted));
        return card;
    }

    private Button settingChoice(String text, boolean selected, Runnable action) {
        Theme th = theme();
        Button button = flatButton(text, selected ? th.ink : th.panel, selected ? th.bg : th.muted, selected ? th.ink : th.dash, 13, 42);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void setSpeechSpeed(String speed) {
        speechSpeed = speed;
        saveSetting("speechSpeed", speechSpeed);
        render();
    }

    private void saveSetting(String key, String value) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(key, value).apply();
    }

    private View bottomNav() {
        Theme th = theme();
        LinearLayout nav = row();
        nav.setPadding(dp(8), dp(6), dp(8), dp(2));
        nav.setGravity(Gravity.CENTER);
        nav.setBackground(topBorder(th.panel, th.ink, 2));
        nav.addView(navItem("home", t("Home", "Dom"), SCREEN_HOME), new LinearLayout.LayoutParams(0, dp(56), 1));
        nav.addView(navItem("browse", t("Cards", "Karty"), SCREEN_BROWSE), new LinearLayout.LayoutParams(0, dp(56), 1));
        nav.addView(navItem("grammar", t("Grammar", "Gramatyka"), SCREEN_GRAMMAR), new LinearLayout.LayoutParams(0, dp(56), 1));
        nav.addView(navItem("alphabet", t("Alphabet", "Alfabet"), SCREEN_ALPHABET), new LinearLayout.LayoutParams(0, dp(56), 1));
        nav.addView(navItem("settings", t("Settings", "Ustawienia"), SCREEN_SETTINGS), new LinearLayout.LayoutParams(0, dp(56), 1));
        return nav;
    }

    private View navItem(String icon, String text, String target) {
        Theme th = theme();
        boolean active = target.equals(screen);
        int color = active ? th.accent : th.faint;
        LinearLayout item = vertical();
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, dp(4), 0, dp(4));
        item.setOnClickListener(v -> {
            screen = target;
            render();
        });
        item.addView(new NavIcon(this, icon, color), new LinearLayout.LayoutParams(dp(21), dp(21)));
        TextView label = uiText(text, 10.5f, color, sansBold);
        label.setGravity(Gravity.CENTER);
        item.addView(label);
        return item;
    }

    private void startSession(String topic) {
        List<Phrase> pool = new ArrayList<>();
        for (Phrase phrase : phrases) {
            if (!level.equals(phrase.level)) {
                continue;
            }
            if (!"All".equals(topic) && !topic.equals(phrase.category)) {
                continue;
            }
            if (!STATUS_LEARNT.equals(getMemoryStatus(phrase))) {
                pool.add(phrase);
            }
        }
        if (pool.isEmpty()) {
            for (Phrase phrase : phrases) {
                if (level.equals(phrase.level) && ("All".equals(topic) || topic.equals(phrase.category))) {
                    pool.add(phrase);
                }
            }
        }
        if (pool.isEmpty()) {
            Toast.makeText(this, "No cards for this level yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        Collections.shuffle(pool);
        sessionDeck.clear();
        sessionDeck.addAll(pool.subList(0, Math.min(SESSION_SIZE, pool.size())));
        sessionIndex = 0;
        sessionGot = 0;
        sessionRevealed = false;
        screen = SCREEN_SESSION;
        render();
    }

    private void answer(boolean got) {
        if (sessionIndex < sessionDeck.size()) {
            Phrase phrase = sessionDeck.get(sessionIndex);
            setMemoryStatus(phrase, got ? STATUS_LEARNT : STATUS_FORGOT);
            if (got) {
                sessionGot++;
            }
        }
        sessionIndex++;
        sessionRevealed = false;
        render();
    }

    private List<Phrase> browseCards() {
        String query = browseQuery.trim().toLowerCase(Locale.ROOT);
        List<Phrase> list = new ArrayList<>();
        for (Phrase phrase : phrases) {
            if (!level.equals(phrase.level)) {
                continue;
            }
            if (!"All".equals(browseTopic) && !browseTopic.equals(phrase.category)) {
                continue;
            }
            if (!query.isEmpty() && !searchable(phrase).contains(query)) {
                continue;
            }
            list.add(phrase);
        }
        return list;
    }

    private String searchable(Phrase phrase) {
        return (phrase.polish + " " + phrase.english + " " + phrase.phonetic + " " + phrase.category).toLowerCase(Locale.ROOT);
    }

    private boolean firstVisit() {
        for (Phrase phrase : phrases) {
            String status = getMemoryStatus(phrase);
            if (STATUS_LEARNT.equals(status) || STATUS_FORGOT.equals(status)) {
                return false;
            }
        }
        return true;
    }

    private int dueCountForLevel() {
        int count = 0;
        for (Phrase phrase : phrases) {
            if (level.equals(phrase.level) && !STATUS_LEARNT.equals(getMemoryStatus(phrase))) {
                count++;
            }
        }
        return count;
    }

    private int[] memoryCounts() {
        int[] counts = {0, 0, 0};
        for (Phrase phrase : phrases) {
            String status = getMemoryStatus(phrase);
            if (STATUS_LEARNT.equals(status)) {
                counts[2]++;
            } else if (STATUS_FORGOT.equals(status)) {
                counts[1]++;
            } else {
                counts[0]++;
            }
        }
        return counts;
    }

    private List<TopicCount> topTopicsForLevel(int limit) {
        List<TopicCount> all = allTopicsForLevel();
        return all.subList(0, Math.min(limit, all.size()));
    }

    private List<TopicCount> allTopicsForLevel() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Phrase phrase : phrases) {
            if (level.equals(phrase.level)) {
                Integer count = counts.get(phrase.category);
                counts.put(phrase.category, count == null ? 1 : count + 1);
            }
        }
        List<TopicCount> topics = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            topics.add(new TopicCount(entry.getKey(), entry.getValue()));
        }
        Collections.sort(topics, (a, b) -> Integer.compare(b.count, a.count));
        return topics;
    }

    private int statusColor(Phrase phrase) {
        Theme th = theme();
        String status = getMemoryStatus(phrase);
        if (STATUS_LEARNT.equals(status)) {
            return th.accent2;
        }
        if (STATUS_FORGOT.equals(status)) {
            return th.accent;
        }
        return th.dash;
    }

    private String t(String english, String polish) {
        return LANG_PL.equals(interfaceLanguage) ? polish : english;
    }

    private String getMemoryStatus(Phrase phrase) {
        String status = memory.get(phrase.key());
        if (STATUS_FORGOT.equals(status) || STATUS_LEARNT.equals(status) || STATUS_NEW.equals(status)) {
            return status;
        }
        return STATUS_NEW;
    }

    private void setMemoryStatus(Phrase phrase, String status) {
        memory.put(phrase.key(), status);
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(MEMORY_PREFIX + phrase.key(), status)
                .apply();
    }

    private void loadMemory() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        for (Phrase phrase : phrases) {
            memory.put(phrase.key(), prefs.getString(MEMORY_PREFIX + phrase.key(), STATUS_NEW));
        }
    }

    private void speak(String text, Locale locale) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (!ttsReady) {
            Toast.makeText(this, "TextToSpeech is not ready.", Toast.LENGTH_SHORT).show();
            return;
        }
        int result = textToSpeech.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this, "This voice is not installed on this device.", Toast.LENGTH_SHORT).show();
            return;
        }
        textToSpeech.setSpeechRate(speechRate());
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "learning-card");
    }

    private void speakAlphabetItem(AlphabetItem item) {
        if (!ttsReady) {
            Toast.makeText(this, "TextToSpeech is not ready.", Toast.LENGTH_SHORT).show();
            return;
        }
        Locale polish = new Locale("pl", "PL");
        int result = textToSpeech.setLanguage(polish);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Toast.makeText(this, "This voice is not installed on this device.", Toast.LENGTH_SHORT).show();
            return;
        }
        textToSpeech.setSpeechRate(speechRate());
        String letter = item.letter.replace(" ", ", ");
        textToSpeech.speak(letter, TextToSpeech.QUEUE_FLUSH, null, "alphabet-letter");
        textToSpeech.speak(item.example, TextToSpeech.QUEUE_ADD, null, "alphabet-example");
    }

    private float speechRate() {
        if (SPEED_SLOW.equals(speechSpeed)) {
            return 0.75f;
        }
        if (SPEED_FAST.equals(speechSpeed)) {
            return 1.15f;
        }
        return 0.95f;
    }

    private void loadPhrases() {
        try {
            JSONArray array = new JSONArray(readAsset("phrases.json"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                phrases.add(new Phrase(
                        item.optString("scenario", item.optString("category", "General Core")),
                        item.getString("polish"),
                        item.getString("english"),
                        item.optString("phonetic"),
                        item.optString("level", "A1"),
                        item.optInt("coreIndex", 0)
                ));
            }
        } catch (Exception error) {
            Toast.makeText(this, "Could not load phrase data.", Toast.LENGTH_LONG).show();
        }
    }

    private void loadGrammarLessons() {
        try {
            JSONArray array = new JSONArray(readAsset("grammar_lessons.json"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                List<GrammarExample> examples = new ArrayList<>();
                JSONArray exampleArray = item.optJSONArray("examples");
                if (exampleArray != null) {
                    for (int j = 0; j < exampleArray.length(); j++) {
                        JSONObject example = exampleArray.getJSONObject(j);
                        examples.add(new GrammarExample(example.getString("polish"), example.getString("english")));
                    }
                }
                JSONObject check = item.optJSONObject("check");
                grammarLessons.add(new GrammarLesson(
                        item.getString("unit"),
                        item.optString("scenario", "Grammar"),
                        item.getString("topic"),
                        item.getString("focus"),
                        item.getString("rule"),
                        item.getString("pattern"),
                        examples,
                        check == null ? "" : check.optString("prompt"),
                        check == null ? "" : check.optString("answer"),
                        check == null ? "" : check.optString("hint")
                ));
            }
        } catch (Exception error) {
            Toast.makeText(this, "Could not load grammar lessons.", Toast.LENGTH_LONG).show();
        }
    }

    private void loadAlphabet() {
        try {
            JSONArray array = new JSONArray(readAsset("alphabet.json"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                alphabet.add(new AlphabetItem(
                        item.getString("letter"),
                        item.getString("sound"),
                        item.getString("example"),
                        item.getString("english")
                ));
            }
        } catch (Exception error) {
            Toast.makeText(this, "Could not load alphabet.", Toast.LENGTH_LONG).show();
        }
    }

    private String readAsset(String fileName) throws Exception {
        InputStream stream = getAssets().open(fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder json = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            json.append(line);
        }
        return json.toString();
    }

    private void loadFonts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sansRegular = getResources().getFont(R.font.ibm_plex_sans_regular);
            sansMedium = getResources().getFont(R.font.ibm_plex_sans_medium);
            sansSemiBold = getResources().getFont(R.font.ibm_plex_sans_semibold);
            sansBold = getResources().getFont(R.font.ibm_plex_sans_bold);
            serifBold = getResources().getFont(R.font.source_serif_4_bold);
        } else {
            sansRegular = Typeface.create("sans", Typeface.NORMAL);
            sansMedium = Typeface.create("sans", Typeface.NORMAL);
            sansSemiBold = Typeface.create("sans", Typeface.BOLD);
            sansBold = Typeface.create("sans", Typeface.BOLD);
            serifBold = Typeface.create("serif", Typeface.BOLD);
        }
    }

    private Theme theme() {
        Theme theme = themes.get(themeName);
        return theme == null ? themes.get(DEFAULT_THEME) : theme;
    }

    private boolean isDarkTheme() {
        return "Atrament".equals(themeName);
    }

    private void buildThemes() {
        themes.put("Klasyczny", new Theme("#f7f1e6", "#fffdf7", "#23251f", "#4b463b", "#6d6759", "#8c8677", "#c9c0ad", "#e3d9c6", "#d9cfba", "#e3d9c6", "#c2402f", "#d4664f", "#fbe9e4", "#fdf8ee", "#29489c", "#fdf8ee", "#29489c"));
        themes.put("Las", new Theme("#edf1e4", "#fafcf3", "#1e2a1f", "#414b3d", "#5d6653", "#7f8a73", "#bcc4a9", "#d8ddc4", "#cbd2b6", "#d8ddc4", "#2d6a4f", "#4c8a6d", "#e0efe4", "#f2f8ec", "#a5651f", "#fbf5e8", "#8f5518"));
        themes.put("Bałtyk", new Theme("#e9eff1", "#f9fcfd", "#182630", "#3c4e59", "#566a74", "#7d919b", "#b4c5cd", "#d0dde2", "#c2d2d9", "#d0dde2", "#1f6f8b", "#4590aa", "#e0eef3", "#f2f9fb", "#ad4a2f", "#fbf1ec", "#a03f24"));
        themes.put("Wrzos", new Theme("#f2ecf2", "#fcf9fc", "#2a2130", "#4e4356", "#6b5d70", "#90839a", "#c7bacf", "#e0d3e0", "#d4c4d4", "#e0d3e0", "#6d3f7d", "#8e619e", "#f0e2f0", "#f9f3fa", "#ad4a2f", "#fbf1ec", "#a03f24"));
        themes.put("Atrament", new Theme("#201d18", "#2a2620", "#f1e9d8", "#d8cfba", "#b5ab97", "#8c8471", "#5c5546", "#3d3830", "#4a443a", "#14120e", "#d9a441", "#b98a2e", "#3a3222", "#201d18", "#c96f4a", "#201d18", "#c96f4a"));
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private TextView screenTitle(String text) {
        Theme th = theme();
        TextView title = serifText(text, 26, th.ink);
        title.setPadding(0, 0, 0, dp(10));
        title.setBackground(bottomBorder(th.ink, 2));
        return title;
    }

    private TextView serifText(String text, float size, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(serifBold);
        view.setIncludeFontPadding(true);
        return view;
    }

    private TextView bodyText(String text, float size, int color) {
        TextView view = uiText(text, size, color, sansRegular);
        view.setLineSpacing(0, 1.18f);
        return view;
    }

    private TextView label(String text, int color, float size, float letterSpacing) {
        TextView view = uiText(text.toUpperCase(Locale.ROOT), size, color, sansBold);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setLetterSpacing(letterSpacing);
        }
        return view;
    }

    private TextView uiText(String text, float size, int color, Typeface typeface) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(typeface);
        view.setIncludeFontPadding(true);
        return view;
    }

    private Button filledButton(String text, int fill, int textColor, float size, int heightDp) {
        return flatButton(text, fill, textColor, fill, size, heightDp);
    }

    private Button flatButton(String text, int fill, int textColor, int stroke, float size, int heightDp) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(size);
        button.setTypeface(sansBold);
        button.setTextColor(textColor);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(rounded(fill, stroke, 4, 1.5f));
        button.setIncludeFontPadding(false);
        button.setHeight(dp(heightDp));
        return button;
    }

    private Button textButton(String text, int textColor, float size) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(size);
        button.setTypeface(sansBold);
        button.setTextColor(textColor);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setMinHeight(0);
        button.setMinWidth(0);
        return button;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp, float strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(Math.max(1, Math.round(dpFloat(strokeDp))), stroke);
        return drawable;
    }

    private ShadowLayout shadowWrap(View child, int offsetDp) {
        return shadowWrap(child, offsetDp, false);
    }

    private ShadowLayout shadowWrap(View child, int offsetDp, boolean fillHeight) {
        ShadowLayout shadow = new ShadowLayout(this, theme().shadow, dp(offsetDp), dp(4));
        FrameLayout.LayoutParams childParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                fillHeight ? FrameLayout.LayoutParams.MATCH_PARENT : FrameLayout.LayoutParams.WRAP_CONTENT
        );
        childParams.setMargins(0, 0, dp(offsetDp), dp(offsetDp));
        shadow.addView(child, childParams);
        return shadow;
    }

    private LinearLayout.LayoutParams topMarginParams(int width, int height, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(0, dp(topDp), 0, 0);
        return params;
    }

    private void addGap(LinearLayout parent, int heightDp) {
        SpaceView space = new SpaceView(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dpFloat(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private String polishDate() {
        Calendar calendar = Calendar.getInstance();
        String[] days = {"Niedziela", "Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota"};
        String[] months = {"stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia"};
        return days[calendar.get(Calendar.DAY_OF_WEEK) - 1] + ", " + calendar.get(Calendar.DAY_OF_MONTH) + " " + months[calendar.get(Calendar.MONTH)];
    }

    private android.graphics.drawable.Drawable bottomBorder(int color, float strokeDp) {
        return new BorderDrawable(Color.TRANSPARENT, color, 0, 0, 0, dpFloat(strokeDp));
    }

    private android.graphics.drawable.Drawable topBorder(int fill, int color, float strokeDp) {
        return new BorderDrawable(fill, color, 0, dpFloat(strokeDp), 0, 0);
    }

    private android.graphics.drawable.Drawable rightBorder(int color, float strokeDp) {
        return new BorderDrawable(Color.TRANSPARENT, color, 0, 0, dpFloat(strokeDp), 0);
    }

    private android.graphics.drawable.Drawable leftBorderOnly(int color, float strokeDp) {
        return new BorderDrawable(Color.TRANSPARENT, color, dpFloat(strokeDp), 0, 0, 0);
    }

    private android.graphics.drawable.Drawable leftBorderBox(int fill, int color, float strokeDp) {
        return new BorderDrawable(fill, color, dpFloat(strokeDp), 0, 0, 0);
    }

    private static class Phrase {
        final String category;
        final String polish;
        final String english;
        final String phonetic;
        final String level;
        final int coreIndex;

        Phrase(String category, String polish, String english, String phonetic, String level, int coreIndex) {
            this.category = category;
            this.polish = polish;
            this.english = english;
            this.phonetic = phonetic;
            this.level = level;
            this.coreIndex = coreIndex;
        }

        String key() {
            if (coreIndex > 0) {
                return "core:" + coreIndex;
            }
            return category + ":" + polish;
        }
    }

    private static class GrammarLesson {
        final String unit;
        final String scenario;
        final String topic;
        final String focus;
        final String rule;
        final String pattern;
        final List<GrammarExample> examples;
        final String checkPrompt;
        final String checkAnswer;
        final String checkHint;

        GrammarLesson(String unit, String scenario, String topic, String focus, String rule, String pattern, List<GrammarExample> examples, String checkPrompt, String checkAnswer, String checkHint) {
            this.unit = unit;
            this.scenario = scenario;
            this.topic = topic;
            this.focus = focus;
            this.rule = rule;
            this.pattern = pattern;
            this.examples = new ArrayList<>(examples);
            this.checkPrompt = checkPrompt;
            this.checkAnswer = checkAnswer;
            this.checkHint = checkHint;
        }

        String polishExamples() {
            StringBuilder text = new StringBuilder();
            for (GrammarExample example : examples) {
                if (text.length() > 0) {
                    text.append(". ");
                }
                text.append(example.polish);
            }
            return text.toString();
        }
    }

    private static class GrammarExample {
        final String polish;
        final String english;

        GrammarExample(String polish, String english) {
            this.polish = polish;
            this.english = english;
        }
    }

    private static class AlphabetItem {
        final String letter;
        final String sound;
        final String example;
        final String english;

        AlphabetItem(String letter, String sound, String example, String english) {
            this.letter = letter;
            this.sound = sound;
            this.example = example;
            this.english = english;
        }
    }

    private static class TopicCount {
        final String name;
        final int count;

        TopicCount(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    private static class Theme {
        final int bg;
        final int panel;
        final int ink;
        final int body;
        final int muted;
        final int faint;
        final int ghost;
        final int softLine;
        final int dash;
        final int shadow;
        final int accent;
        final int accentAlt;
        final int accentSoft;
        final int onAccent;
        final int accent2;
        final int onAccent2;
        final int accent2Text;

        Theme(String bg, String panel, String ink, String body, String muted, String faint, String ghost, String softLine, String dash, String shadow, String accent, String accentAlt, String accentSoft, String onAccent, String accent2, String onAccent2, String accent2Text) {
            this.bg = Color.parseColor(bg);
            this.panel = Color.parseColor(panel);
            this.ink = Color.parseColor(ink);
            this.body = Color.parseColor(body);
            this.muted = Color.parseColor(muted);
            this.faint = Color.parseColor(faint);
            this.ghost = Color.parseColor(ghost);
            this.softLine = Color.parseColor(softLine);
            this.dash = Color.parseColor(dash);
            this.shadow = Color.parseColor(shadow);
            this.accent = Color.parseColor(accent);
            this.accentAlt = Color.parseColor(accentAlt);
            this.accentSoft = Color.parseColor(accentSoft);
            this.onAccent = Color.parseColor(onAccent);
            this.accent2 = Color.parseColor(accent2);
            this.onAccent2 = Color.parseColor(onAccent2);
            this.accent2Text = Color.parseColor(accent2Text);
        }
    }

    private static class SpaceView extends View {
        SpaceView(Activity activity) {
            super(activity);
        }
    }

    private static class BorderDrawable extends android.graphics.drawable.Drawable {
        private final int fill;
        private final int color;
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BorderDrawable(int fill, int color, float left, float top, float right, float bottom) {
            this.fill = fill;
            this.color = color;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        @Override
        public void draw(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fill);
            canvas.drawRect(getBounds(), paint);
            paint.setColor(color);
            if (left > 0) canvas.drawRect(0, 0, left, getBounds().height(), paint);
            if (top > 0) canvas.drawRect(0, 0, getBounds().width(), top, paint);
            if (right > 0) canvas.drawRect(getBounds().width() - right, 0, getBounds().width(), getBounds().height(), paint);
            if (bottom > 0) canvas.drawRect(0, getBounds().height() - bottom, getBounds().width(), getBounds().height(), paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private static class ShadowLayout extends FrameLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int offset;
        private final int radius;

        ShadowLayout(Activity activity, int color, int offset, int radius) {
            super(activity);
            this.offset = offset;
            this.radius = radius;
            paint.setColor(color);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            RectF rect = new RectF(offset, offset, getWidth(), getHeight());
            canvas.drawRoundRect(rect, radius, radius, paint);
        }
    }

    private static class DashedLine extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DashedLine(Activity activity, int color) {
            super(activity);
            paint.setColor(color);
            paint.setStrokeWidth(2);
            paint.setStyle(Paint.Style.STROKE);
            paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{10, 8}, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawLine(0, getHeight() / 2f, getWidth(), getHeight() / 2f, paint);
        }
    }

    private static class ThemeSwatch extends View {
        private final Theme theme;
        private final boolean active;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ThemeSwatch(Activity activity, Theme theme, boolean active) {
            super(activity);
            this.theme = theme;
            this.active = active;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float radius = Math.min(getWidth(), getHeight()) / 2f - 2;
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(theme.bg);
            canvas.drawCircle(cx, cy, radius, paint);
            Path path = new Path();
            path.moveTo(0, getHeight());
            path.lineTo(0, 0);
            path.lineTo(getWidth(), 0);
            path.close();
            paint.setColor(theme.accent);
            canvas.save();
            canvas.clipPath(path);
            canvas.drawCircle(cx, cy, radius, paint);
            canvas.restore();
            if (active) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4);
                paint.setColor(theme.ink);
                canvas.drawCircle(cx, cy, radius, paint);
            }
        }
    }

    private static class StripeProgress extends View {
        private final Theme theme;
        private final float progress;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        StripeProgress(Activity activity, Theme theme, float progress) {
            super(activity);
            this.theme = theme;
            this.progress = progress;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float stroke = 2.5f;
            RectF box = new RectF(stroke, stroke, getWidth() - stroke, getHeight() - stroke);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(theme.panel);
            canvas.drawRoundRect(box, 3, 3, paint);
            float fillWidth = box.width() * Math.max(0, Math.min(1, progress));
            RectF fill = new RectF(box.left + 2, box.top + 2, box.left + fillWidth - 2, box.bottom - 2);
            paint.setColor(theme.accent);
            canvas.drawRect(fill, paint);
            paint.setColor(theme.accentAlt);
            paint.setStrokeWidth(4);
            for (float x = fill.left - getHeight(); x < fill.right; x += 12) {
                canvas.drawLine(x, fill.bottom, x + getHeight(), fill.top, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setColor(theme.ink);
            canvas.drawRoundRect(box, 3, 3, paint);
        }
    }

    private static class NavIcon extends View {
        private final String type;
        private final int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        NavIcon(Activity activity, String type, int color) {
            super(activity);
            this.type = type;
            this.color = color;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.4f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            paint.setColor(color);
            if ("home".equals(type)) {
                Path roof = new Path();
                roof.moveTo(w * 0.15f, h * 0.48f);
                roof.lineTo(w * 0.5f, h * 0.16f);
                roof.lineTo(w * 0.85f, h * 0.48f);
                canvas.drawPath(roof, paint);
                canvas.drawRect(w * 0.25f, h * 0.43f, w * 0.75f, h * 0.86f, paint);
            } else if ("browse".equals(type)) {
                canvas.drawRoundRect(new RectF(w * 0.18f, h * 0.25f, w * 0.68f, h * 0.9f), 3, 3, paint);
                canvas.drawLine(w * 0.36f, h * 0.14f, w * 0.78f, h * 0.14f, paint);
                canvas.drawLine(w * 0.78f, h * 0.14f, w * 0.86f, h * 0.72f, paint);
            } else if ("grammar".equals(type)) {
                canvas.drawRoundRect(new RectF(w * 0.18f, h * 0.18f, w * 0.5f, h * 0.86f), 3, 3, paint);
                canvas.drawRoundRect(new RectF(w * 0.5f, h * 0.18f, w * 0.82f, h * 0.86f), 3, 3, paint);
                canvas.drawLine(w * 0.5f, h * 0.18f, w * 0.5f, h * 0.86f, paint);
            } else if ("alphabet".equals(type)) {
                canvas.drawLine(w * 0.17f, h * 0.85f, w * 0.42f, h * 0.18f, paint);
                canvas.drawLine(w * 0.42f, h * 0.18f, w * 0.68f, h * 0.85f, paint);
                canvas.drawLine(w * 0.27f, h * 0.62f, w * 0.58f, h * 0.62f, paint);
                canvas.drawLine(w * 0.77f, h * 0.22f, w * 0.77f, h * 0.52f, paint);
                canvas.drawLine(w * 0.65f, h * 0.37f, w * 0.9f, h * 0.37f, paint);
            } else {
                canvas.drawCircle(w * 0.5f, h * 0.5f, w * 0.18f, paint);
                canvas.drawLine(w * 0.5f, h * 0.08f, w * 0.5f, h * 0.23f, paint);
                canvas.drawLine(w * 0.5f, h * 0.77f, w * 0.5f, h * 0.92f, paint);
                canvas.drawLine(w * 0.08f, h * 0.5f, w * 0.23f, h * 0.5f, paint);
                canvas.drawLine(w * 0.77f, h * 0.5f, w * 0.92f, h * 0.5f, paint);
                canvas.drawLine(w * 0.2f, h * 0.2f, w * 0.31f, h * 0.31f, paint);
                canvas.drawLine(w * 0.69f, h * 0.69f, w * 0.8f, h * 0.8f, paint);
                canvas.drawLine(w * 0.8f, h * 0.2f, w * 0.69f, h * 0.31f, paint);
                canvas.drawLine(w * 0.31f, h * 0.69f, w * 0.2f, h * 0.8f, paint);
            }
        }
    }
}
