package com.example.polishphrasebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Environment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.view.WindowManager;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ClickableSpan;
import android.text.method.LinkMovementMethod;
import android.text.Spanned;
import android.text.SpannableString;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {
    private static final String PREFS = "phrasebook";
    private static final String MEMORY_PREFIX = "memory:";
    private static final String STATUS_NEW = "new";
    private static final String STATUS_FORGOT = "forgot";
    private static final String STATUS_LEARNT = "learnt";
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    // Leitner boxes: box 0 = new, boxes 1-5 reviewed at growing intervals.
    private static final long[] BOX_INTERVALS_MS = {0, DAY_MS, 3 * DAY_MS, 7 * DAY_MS, 16 * DAY_MS, 35 * DAY_MS};
    private static final int MAX_BOX = BOX_INTERVALS_MS.length - 1;

    private static final String SCREEN_HOME = "home";
    private static final String SCREEN_SESSION = "session";
    private static final String SCREEN_BROWSE = "browse";
    private static final String SCREEN_GRAMMAR = "grammar";
    private static final String SCREEN_ALPHABET = "alphabet";
    private static final String SCREEN_NEWS = "news";
    private static final String SCREEN_TRANSLATE = "translate";
    private static final String SCREEN_LISTEN = "listen";
    private static final String SCREEN_DIALOGS = "dialogs";
    private static final String CUSTOM_DIALOGS = "customDialogs";
    private static final long LISTEN_GAP_MS = 1000L;
    private static final String SCREEN_READ = "read";
    private static final String SCREEN_MORE = "more";
    private static final String SCREEN_SETTINGS = "settings";
    private static final String DEFAULT_THEME = "Komiks";
    private static final String LANG_EN = "en";
    private static final String LANG_PL = "pl";
    private static final String PREF_TTS_ENGINE = "ttsEngine";
    private static final String PREF_VOICE_PL = "ttsVoicePl";
    private static final String PREF_VOICE_EN = "ttsVoiceEn";
    private static final String SPEED_SLOWEST = "slowest";
    private static final String SPEED_SLOW = "slow";
    private static final String SPEED_NORMAL = "normal";
    private static final String SPEED_FAST = "fast";
    private static final String SPEED_FASTEST = "fastest";
    private static final String DATA_MANIFEST_URL = "https://api.github.com/repos/lida0407/Polish4Beginners-for-English-speakers/contents/docs/database.json?ref=main";
    private static final String REMOTE_PHRASES_FILE = "phrases_remote.json";
    private static final int DEFAULT_DATABASE_VERSION = 10;
    private static final long UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final int SESSION_SIZE = 10;
    private static final int NEWS_PREFETCH_AHEAD = 5;
    private static final String CUSTOM_CARDS = "customCards";
    private static final String FAVOURITES = "favourites";
    private static final String MY_WORDS_CATEGORY = "My Words";
    private static final int REQ_SAVE_TEMPLATE = 2001;
    private static final int REQ_OPEN_LIST = 2002;
    private static final int REQ_SAVE_DIALOG_TEMPLATE = 2003;
    private static final int REQ_OPEN_DIALOG = 2004;
    private static final int REQ_OPEN_DICTIONARY = 2005;
    private static final String UPDATE_MANIFEST_URL = "https://api.github.com/repos/lida0407/Polish4Beginners-for-English-speakers/contents/docs/latest.json?ref=main";
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";
    private static final String DICTIONARY_FILE = "user_dictionary.json";
    // Words for tap-to-translate: letters plus internal apostrophes/hyphens.
    private static final Pattern WORD_PATTERN = Pattern.compile("\\p{L}+(?:['\u2019-]\\p{L}+)*");
    // Longest first: the fallback accepts the first trimmed form that exists.
    // Noun/adjective endings, longest first, plus the vowels a stem may restore.
    private static final String[] INFLECTION_SUFFIXES = {
            "iami", "ach", "ami", "ego", "emu", "imi", "ymi", "owi", "iem",
            "om", "ów", "em", "ie", "iu", "ia", "io", "ce", "ka", "ki", "ku",
            "ą", "ę", "y", "i", "u", "a", "e", "o"
    };
    private static final String[] INFLECTION_RESTORES = {"", "a", "o", "e", "y", "i"};
    private static final String GLOSS_FILE = "gloss_cache.json";
    private static final String DIALOG_TEMPLATE =
            "[\n"
            + "  {\n"
            + "    \"id\": \"my-cafe\",\n"
            + "    \"title\": \"Ordering coffee\",\n"
            + "    \"titlePolish\": \"Zamawianie kawy\",\n"
            + "    \"level\": \"A1\",\n"
            + "    \"scenario\": \"Food & Drink\",\n"
            + "    \"description\": \"Ordering at the counter and paying.\",\n"
            + "    \"roles\": { \"A\": \"Klient · Customer\", \"B\": \"Barista\" },\n"
            + "    \"lines\": [\n"
            + "      { \"speaker\": \"B\", \"polish\": \"Dzień dobry! Co podać?\", \"english\": \"Hello! What can I get you?\" },\n"
            + "      { \"speaker\": \"A\", \"polish\": \"Poproszę dużą kawę z mlekiem.\", \"english\": \"A large coffee with milk, please.\", \"note\": \"poproszę = polite 'I'll have'\" },\n"
            + "      { \"speaker\": \"B\", \"polish\": \"Na miejscu czy na wynos?\", \"english\": \"For here or to go?\" },\n"
            + "      { \"speaker\": \"A\", \"polish\": \"Na wynos, proszę.\", \"english\": \"To go, please.\" }\n"
            + "    ]\n"
            + "  }\n"
            + "]\n";
    private static final String WORDLIST_TEMPLATE =
            "polish,english,level,tag\n"
            + "dziękuję,thank you,A1,My Words\n"
            + "proszę,,A1,My Words\n"
            + ",good morning,A1,ZUS\n";

    private final List<Phrase> phrases = new ArrayList<>();
    private final List<GrammarLesson> grammarLessons = new ArrayList<>();
    private final List<AlphabetItem> alphabet = new ArrayList<>();
    private final List<NewsItem> newsItems = new ArrayList<>();
    private final Map<String, CardMemory> memory = new HashMap<>();
    private final java.util.Set<String> favourites = new java.util.HashSet<>();
    private final List<Phrase> listenDeck = new ArrayList<>();
    private final List<Dialog> dialogs = new ArrayList<>();
    private final Handler listenHandler = new Handler(Looper.getMainLooper());
    // Uploaded dictionary: normalized Polish -> gloss. Loaded once, then O(1).
    private final Map<String, String> userDictionary = new HashMap<>();
    // Prebuilt per-card gloss, so playback never looks anything up.
    private final Map<String, String> glossCache = new HashMap<>();
    private boolean dictionaryLoading = false;
    private boolean glossBuilding = false;
    private String glossStatus = "";
    private final List<Phrase> sessionDeck = new ArrayList<>();
    private final List<Boolean> sessionEnglishFront = new ArrayList<>();
    private final Map<String, Theme> themes = new LinkedHashMap<>();

    private String screen = SCREEN_HOME;
    private String level = "A1";
    private String themeName = DEFAULT_THEME;
    private String interfaceLanguage = LANG_EN;
    private String speechSpeed = SPEED_NORMAL;
    private String browseTopic = "All";
    private String listenTopic = "All";
    private int listenIndex = 0;
    private boolean listenPlaying = false;
    private boolean listenShowEnglish = true;
    private String openDialogId = null;
    private boolean readShowsDialogs = false;
    private long updateDownloadId = -1L;
    private BroadcastReceiver updateDownloadReceiver;
    private boolean dataReady = false;
    private String dataError = "";
    private Bundle pendingState = null;
    private int dialogPlayIndex = -1;
    private boolean dialogShowEnglish = true;
    private String browseQuery = "";
    private String openLessonUnit = null;
    private int browseLimit = 25;
    private int sessionIndex = 0;
    private int sessionGot = 0;
    private int newsIndex = 0;
    private float newsTouchStartX = 0f;
    private float newsTouchStartY = 0f;
    private boolean sessionRevealed = false;
    private boolean newsLoading = false;
    private boolean newsTranslating = false;
    private boolean newsTranslatorReady = false;
    private boolean newsTranslatorPreparing = false;
    private boolean newsTranslationUnavailable = false;
    private boolean newsFetchedOnce = false;
    private boolean ttsReady = false;
    private String newsError = "";
    private String newsLastUpdated = "";
    private String newsTranslationStatus = "";
    private TextToSpeech textToSpeech;
    private Translator polishEnglishTranslator;
    private Translator englishPolishTranslator;
    private boolean translateEnToPl = false;
    private boolean translateBusy = false;
    private String translateInput = "";
    private String translateOutput = "";
    private String translateStatus = "";
    private Typeface sansRegular;
    private Typeface sansMedium;
    private Typeface sansSemiBold;
    private Typeface sansBold;
    private Typeface serifBold;
    private Typeface displaySemi;
    private Typeface displayBold;

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
        if (!SPEED_SLOWEST.equals(speechSpeed)
                && !SPEED_SLOW.equals(speechSpeed)
                && !SPEED_FAST.equals(speechSpeed)
                && !SPEED_FASTEST.equals(speechSpeed)) {
            speechSpeed = SPEED_NORMAL;
        }
        loadFonts();
        // Assets are ~1 MB of JSON; parsing them on the main thread delayed the
        // first frame. Show a loading state and finish initialization off-thread.
        pendingState = savedInstanceState;
        registerUpdateDownloadReceiver();
        render();
        startDataLoad();
    }

    private void startDataLoad() {
        new Thread(() -> {
            dataError = "";
            try {
                loadPhrases();
                loadGrammarLessons();
                loadAlphabet();
                loadDialogs();
                loadMemory();
                loadFavourites();
            } catch (Throwable error) {
                if (dataError.isEmpty()) {
                    dataError = "Could not load learning data.";
                }
            }
            runOnUiThread(() -> {
                dataReady = true;
                restoreInstanceState(pendingState);
                pendingState = null;
                loadDictionaryAsync();
                textToSpeech = createTts();
                render();
                maybeCheckForDataUpdatesOnStart();
                maybeCheckForUpdatesOnStart();
            });
        }, "p4b-data-load").start();
    }

    private void renderLoading(LinearLayout root) {
        Theme th = theme();
        LinearLayout box = vertical();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.addView(box, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        if (dataError.isEmpty()) {
            box.addView(serifText("Mój polski", 30, th.ink));
            TextView msg = bodyText(t("Loading your cards…", "Wczytuję karty…"), 14, th.muted);
            msg.setGravity(Gravity.CENTER);
            box.addView(msg, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 14));
        } else {
            box.addView(serifText(t("Something went wrong", "Coś poszło nie tak"), 24, th.ink));
            TextView msg = bodyText(dataError, 14, th.muted);
            msg.setGravity(Gravity.CENTER);
            box.addView(msg, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 12));
            Button retry = filledButton(t("Try again", "Spróbuj ponownie"), th.accent, th.onAccent, 15, 50);
            retry.setOnClickListener(v -> {
                dataReady = false;
                dataError = "";
                render();
                startDataLoad();
            });
            box.addView(retry, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(50), 18));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("screen", screen);
        outState.putString("level", level);
        outState.putString("browseTopic", browseTopic);
        outState.putString("browseQuery", browseQuery);
        outState.putInt("browseLimit", browseLimit);
        outState.putString("openLessonUnit", openLessonUnit);
        outState.putString("listenTopic", listenTopic);
        outState.putInt("listenIndex", listenIndex);
        outState.putBoolean("listenShowEnglish", listenShowEnglish);
        outState.putString("openDialogId", openDialogId);
        outState.putBoolean("dialogShowEnglish", dialogShowEnglish);
        outState.putInt("newsIndex", newsIndex);
        outState.putBoolean("translateEnToPl", translateEnToPl);
        outState.putString("translateInput", translateInput);
        outState.putString("translateOutput", translateOutput);
        // Study session: keep card identities, not object references.
        outState.putInt("sessionIndex", sessionIndex);
        outState.putInt("sessionGot", sessionGot);
        outState.putBoolean("sessionRevealed", sessionRevealed);
        String[] keys = new String[sessionDeck.size()];
        for (int i = 0; i < sessionDeck.size(); i++) {
            keys[i] = sessionDeck.get(i).key();
        }
        outState.putStringArray("sessionKeys", keys);
        boolean[] fronts = new boolean[sessionEnglishFront.size()];
        for (int i = 0; i < sessionEnglishFront.size(); i++) {
            fronts[i] = sessionEnglishFront.get(i);
        }
        outState.putBooleanArray("sessionFronts", fronts);
    }

    // Applied only once phrases are loaded, so session cards can be resolved.
    private void restoreInstanceState(Bundle in) {
        if (in == null) {
            return;
        }
        screen = in.getString("screen", SCREEN_HOME);
        level = in.getString("level", level);
        browseTopic = in.getString("browseTopic", browseTopic);
        browseQuery = in.getString("browseQuery", browseQuery);
        browseLimit = in.getInt("browseLimit", browseLimit);
        openLessonUnit = in.getString("openLessonUnit");
        listenTopic = in.getString("listenTopic", listenTopic);
        listenIndex = in.getInt("listenIndex", 0);
        listenShowEnglish = in.getBoolean("listenShowEnglish", true);
        openDialogId = in.getString("openDialogId");
        dialogShowEnglish = in.getBoolean("dialogShowEnglish", true);
        newsIndex = in.getInt("newsIndex", 0);
        translateEnToPl = in.getBoolean("translateEnToPl", false);
        translateInput = in.getString("translateInput", "");
        translateOutput = in.getString("translateOutput", "");

        String[] keys = in.getStringArray("sessionKeys");
        sessionDeck.clear();
        if (keys != null && keys.length > 0) {
            Map<String, Phrase> byKey = new HashMap<>();
            for (Phrase phrase : phrases) {
                byKey.put(phrase.key(), phrase);
            }
            for (String key : keys) {
                Phrase phrase = byKey.get(key);
                if (phrase != null) {
                    sessionDeck.add(phrase);
                }
            }
        }
        sessionEnglishFront.clear();
        boolean[] fronts = in.getBooleanArray("sessionFronts");
        if (fronts != null) {
            for (boolean b : fronts) {
                sessionEnglishFront.add(b);
            }
        }
        // Keep the deck and its direction list the same length.
        while (sessionEnglishFront.size() < sessionDeck.size()) {
            sessionEnglishFront.add(false);
        }
        sessionIndex = Math.max(0, in.getInt("sessionIndex", 0));
        sessionGot = in.getInt("sessionGot", 0);
        sessionRevealed = in.getBoolean("sessionRevealed", false);
        // If the deck could not be rebuilt, don't strand the user on a blank card.
        if (SCREEN_SESSION.equals(screen) && sessionDeck.isEmpty()) {
            screen = SCREEN_HOME;
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            int result = textToSpeech.setLanguage(new Locale("pl", "PL"));
            applySpeechRate();
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Install a Polish TTS voice for Polish reading.", Toast.LENGTH_LONG).show();
            }
            // Voice lists only exist once an engine is initialized; refresh Settings.
            if (SCREEN_SETTINGS.equals(screen)) {
                runOnUiThread(this::render);
            }
        }
    }

    // Builds TextToSpeech on the chosen engine ("" = the system default engine,
    // e.g. MultiTTS when the user has set it as their system TTS).
    private TextToSpeech createTts() {
        String engine = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_TTS_ENGINE, "");
        if (engine.isEmpty()) {
            return new TextToSpeech(this, this);
        }
        return new TextToSpeech(this, this, engine);
    }

    private void setTtsEngine(String enginePackage) {
        saveSetting(PREF_TTS_ENGINE, enginePackage);
        // Voice ids are engine-specific, so previous picks no longer apply.
        saveSetting(PREF_VOICE_PL, "");
        saveSetting(PREF_VOICE_EN, "");
        ttsReady = false;
        if (textToSpeech != null) {
            try {
                textToSpeech.stop();
                textToSpeech.shutdown();
            } catch (Exception ignored) {
            }
        }
        textToSpeech = createTts();
        render();
    }

    @Override
    protected void onDestroy() {
        listenHandler.removeCallbacksAndMessages(null);
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (polishEnglishTranslator != null) {
            polishEnglishTranslator.close();
        }
        if (englishPolishTranslator != null) {
            englishPolishTranslator.close();
        }
        if (updateDownloadReceiver != null) {
            try {
                unregisterReceiver(updateDownloadReceiver);
            } catch (IllegalArgumentException ignored) {
            }
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
        if (SCREEN_DIALOGS.equals(screen) && openDialogId != null) {
            stopAudioPlayback();
            openDialogId = null;
            render();
            return;
        }
        if (SCREEN_LISTEN.equals(screen) || SCREEN_DIALOGS.equals(screen)) {
            stopAudioPlayback();
            screen = SCREEN_HOME;
            render();
            return;
        }
        if (SCREEN_GRAMMAR.equals(screen) || SCREEN_ALPHABET.equals(screen)
                || SCREEN_TRANSLATE.equals(screen) || SCREEN_SETTINGS.equals(screen)) {
            screen = SCREEN_MORE;   // these now live under "More"
            render();
            return;
        }
        if (SCREEN_NEWS.equals(screen)) {
            screen = SCREEN_READ;
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
        if (theme.halftone) {
            root.setBackground(new HalftoneDrawable(theme.bg, theme.softLine, dpFloat(1.3f), dpFloat(14)));
        } else {
            root.setBackgroundColor(theme.bg);
        }

        // Nothing is interactive until the learning data is in memory.
        if (!dataReady) {
            renderLoading(root);
            setContentView(root);
            return;
        }

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
            } else if (SCREEN_NEWS.equals(screen)) {
                renderNews(content);
            } else if (SCREEN_TRANSLATE.equals(screen)) {
                renderTranslate(content);
            } else if (SCREEN_LISTEN.equals(screen)) {
                renderListen(content);
            } else if (SCREEN_DIALOGS.equals(screen)) {
                renderDialogs(content);
            } else if (SCREEN_READ.equals(screen)) {
                renderRead(content);
            } else if (SCREEN_MORE.equals(screen)) {
                renderMore(content);
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
        hero.setBackground(rounded(th.panel, th.ink, th.radius, th.border));

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
        hero.addView(bodyText(t("Flip each card, say it out loud, mark what stuck. ", "Odwróć każdą kartę, powiedz ją na głos i zaznacz, co pamiętasz. ") + dueReviewCountForLevel() + t(" to review and ", " do powtórki i ") + newCountForLevel() + t(" new at this level.", " nowych na tym poziomie."), 13, th.muted));
        Button start = filledButton(t("Zaczynamy — start session", "Zaczynamy — start"), th.accent, th.onAccent, 16, 52);
        start.setOnClickListener(v -> startSession("All"));
        hero.addView(start, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52), 14));
        content.addView(shadowWrap(hero, 4));
        addGap(content, 16);

        content.addView(levelSelector());
        addGap(content, 16);
        addGap(content, 12);
        LinearLayout modes = row();
        Button listenBtn = flatButton("🎧  " + t("Listen", "Słuchaj"), th.accent2, th.onAccent2, th.ink, 15, 56);
        listenBtn.setOnClickListener(v -> {
            screen = SCREEN_LISTEN;
            buildListenDeck();
            render();
        });
        modes.addView(shadowWrap(listenBtn, 3), new LinearLayout.LayoutParams(0, dp(56), 1));
        Button talkBtn = flatButton("💬  " + t("Conversations", "Rozmowy"), th.accentAlt, th.ink, th.ink, 15, 56);
        talkBtn.setOnClickListener(v -> {
            screen = SCREEN_DIALOGS;
            openDialogId = null;
            render();
        });
        LinearLayout.LayoutParams talkP = new LinearLayout.LayoutParams(0, dp(56), 1);
        talkP.setMargins(dp(10), 0, 0, 0);
        modes.addView(shadowWrap(talkBtn, 3), talkP);
        content.addView(modes);
        addGap(content, 16);

        content.addView(statsStrip());
        int favs = favouriteCount();
        if (favs > 0) {
            addGap(content, 12);
            Button favBtn = flatButton("★  " + t("Favourites", "Ulubione") + "                                  " + favs + t(" cards →", " kart →"), th.accent, th.onAccent, th.accent, 14, 48);
            favBtn.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
            favBtn.setPadding(dp(14), 0, dp(14), 0);
            favBtn.setOnClickListener(v -> startFavouritesSession());
            content.addView(favBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        }
        Map<String, Integer> tagCounts = customTagCounts();
        if (!tagCounts.isEmpty()) {
            addGap(content, 14);
            content.addView(label(t("MY WORD LISTS", "MOJE LISTY SŁÓW"), th.accent, 11, 0.14f));
            addGap(content, 8);
            for (Map.Entry<String, Integer> entry : tagCounts.entrySet()) {
                final String tag = entry.getKey();
                Button mine = flatButton(tag + "                                  " + entry.getValue() + t(" cards →", " kart →"), th.accent2, th.onAccent2, th.ink, 14, 48);
                mine.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
                mine.setPadding(dp(14), 0, dp(14), 0);
                mine.setOnClickListener(v -> startTagSession(tag));
                content.addView(mine, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
                addGap(content, 8);
            }
        }
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
        badge.setBackground(rounded(Color.TRANSPARENT, th.accent, th.radius, th.border));
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
        strip.setBackground(rounded(th.panel, th.ink, th.radius, th.border));
        strip.setBaselineAligned(false);
        int[] counts = memoryCounts();
        strip.addView(statCell(String.valueOf(counts[0]), t("New", "Nowe"), th.accent, true, 0), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        strip.addView(statCell(String.valueOf(counts[1]), t("To review", "Do powtórki"), th.accent, true, 1), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        strip.addView(statCell(String.valueOf(counts[2]), t("Scheduled", "Zaplanowane"), th.accent3, false, 2), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return strip;
    }

    private View statCell(String count, String label, int color, boolean divider, int statusIndex) {
        Theme th = theme();
        LinearLayout cell = vertical();
        cell.setPadding(dp(14), dp(12), dp(14), dp(12));
        cell.setBackground(divider ? rightBorder(th.softLine, 1.5f) : null);
        cell.addView(serifText(count, 24, color));
        TextView labelView = uiText(label.toUpperCase(Locale.ROOT), 11, th.faint, sansSemiBold);
        cell.addView(labelView);
        cell.setOnClickListener(v -> startStatusSession(statusIndex));
        return cell;
    }

    private View topicsSection() {
        Theme th = theme();
        LinearLayout section = vertical();
        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(label(t("TOPICS", "ROZDZIAŁY"), th.accent, 11, 0.14f));
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
            button.setOnClickListener(v -> startSession(topic.name, 0));
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
        face.setBackground(rounded(th.panel, th.ink, th.radius, th.border));
        face.setOnClickListener(v -> {
            if (!sessionRevealed) {
                sessionRevealed = true;
                render();
            }
        });

        LinearLayout meta = row();
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(label(card.level + " · " + card.category, th.accent2, 10.5f, 0.12f), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        boolean fav = isFavourite(card);
        TextView star = uiText(fav ? "★" : "☆", 20, fav ? th.accent : th.ghost, sansBold);
        star.setPadding(dp(8), 0, dp(8), 0);
        star.setOnClickListener(v -> {
            toggleFavourite(card);
            render();
        });
        meta.addView(star);
        meta.addView(uiText("Nr " + (sessionIndex + 1), 10.5f, th.ghost, sansBold));
        face.addView(meta, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        boolean englishFront = sessionIndex < sessionEnglishFront.size() && sessionEnglishFront.get(sessionIndex);
        String frontText = englishFront ? card.english : card.polish;
        String answerText = englishFront ? card.polish : card.english;

        SpaceView topSpace = new SpaceView(this);
        face.addView(topSpace, new LinearLayout.LayoutParams(1, 0, 1));
        face.addView(label(englishFront ? "EN → PL" : "PL → EN", th.ghost, 10, 0.14f));
        addGap(face, 8);
        TextView front = serifText(frontText, 33, th.ink);
        front.setGravity(Gravity.CENTER);
        front.setLineSpacing(0, 1.02f);
        front.setTextIsSelectable(true);
        face.addView(front, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        addGap(face, 14);

        if (sessionRevealed) {
            DashedLine divider = new DashedLine(this, th.ghost);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(dp(52), dp(2));
            face.addView(divider, dividerParams);
            addGap(face, 14);
            TextView answer = uiText(answerText, 18, th.body, sansMedium);
            answer.setGravity(Gravity.CENTER);
            answer.setLineSpacing(0, 1.08f);
            answer.setTextIsSelectable(true);
            face.addView(answer);
            if (!card.phonetic.isEmpty()) {
                TextView phonetic = uiText(card.phonetic, 13.5f, th.faint, sansRegular);
                phonetic.setTypeface(Typeface.create(sansRegular, Typeface.ITALIC));
                phonetic.setGravity(Gravity.CENTER);
                face.addView(phonetic, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));
            }
            if (!card.examplePolish.isEmpty()) {
                LinearLayout example = vertical();
                example.setPadding(dp(12), dp(8), dp(12), dp(8));
                example.setBackground(leftBorderOnly(th.accent2, 3));
                TextView examplePolish = serifText(card.examplePolish, 15, th.body);
                examplePolish.setLineSpacing(0, 1.06f);
                example.addView(examplePolish);
                if (!card.exampleEnglish.isEmpty()) {
                    TextView exampleEnglish = uiText(card.exampleEnglish, 12.5f, th.faint, sansRegular);
                    exampleEnglish.setLineSpacing(0, 1.06f);
                    example.addView(exampleEnglish, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 2));
                }
                example.setOnClickListener(v -> speak(card.examplePolish, new Locale("pl", "PL")));
                face.addView(example, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 12));
            }
            if (!card.notes.isEmpty()) {
                TextView notes = uiText(card.notes, 12, th.faint, sansRegular);
                notes.setGravity(Gravity.CENTER);
                face.addView(notes, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));
            }
            if (!card.declension.isEmpty()) {
                TextView table = new TextView(this);
                table.setText(card.declension);
                table.setTypeface(Typeface.MONOSPACE);
                table.setTextSize(12.5f);
                table.setTextColor(th.body);
                table.setLineSpacing(0, 1.12f);
                table.setPadding(dp(14), dp(10), dp(14), dp(10));
                table.setBackground(rounded(th.bg, th.dash, th.radius, th.border));
                table.setTextIsSelectable(true);
                HorizontalScrollView scroll = new HorizontalScrollView(this);
                scroll.setHorizontalScrollBarEnabled(false);
                scroll.addView(table);
                face.addView(scroll, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 10));
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

            LinearLayout tools = row();
            tools.setGravity(Gravity.CENTER);
            Button share = flatButton(t("Share", "Udostępnij"), th.panel, th.muted, th.dash, 12.5f, 38);
            share.setOnClickListener(v -> sharePhrase(card));
            tools.addView(share, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)));
            Button translate = flatButton(t("Google Translate", "Google Translate"), th.panel, th.muted, th.dash, 12.5f, 38);
            translate.setOnClickListener(v -> openGoogleTranslate(card.polish, "pl", "en"));
            LinearLayout.LayoutParams translateParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
            translateParams.setMargins(dp(10), 0, 0, 0);
            tools.addView(translate, translateParams);
            face.addView(tools, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 10));
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
        search.setBackground(rounded(th.panel, th.ink, th.radius, th.border));
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
        outer.setBackground(rounded(th.panel, th.softLine, th.radius, th.border));

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
        card.setBackground(rounded(th.panel, th.ink, th.radius, th.border));

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView unit = uiText(lesson.unit, 10.5f, th.onAccent2, sansBold);
        unit.setPadding(dp(7), dp(3), dp(7), dp(3));
        unit.setBackground(rounded(th.accent2, th.accent2, th.radius / 2f, th.border));
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
        tile.setBackground(rounded(th.panel, th.ink, th.radius, th.border));
        tile.setOnClickListener(v -> speakAlphabetItem(item));
        tile.addView(serifText(item.letter, 21, th.ink));
        tile.addView(uiText(item.sound, 11.5f, th.accent, sansBold));
        tile.addView(uiText(item.example + " · " + item.english, 12, th.faint, sansRegular));
        return tile;
    }

    private void renderNews(LinearLayout content) {
        Theme th = theme();
        content.addView(screenTitle(t("News Reading", "Czytanie wiadomości")));
        addGap(content, 12);

        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView status = bodyText(newsStatusText(), 12.5f, th.faint);
        controls.addView(status, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button refresh = flatButton(newsLoading ? t("Loading", "Ładowanie") : t("Refresh", "Odśwież"), th.accentSoft, th.accent, th.accent, 12.5f, 38);
        refresh.setEnabled(!newsLoading);
        refresh.setOnClickListener(v -> fetchTodayNews(true));
        controls.addView(refresh, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)));
        content.addView(controls);
        addGap(content, 12);

        if (!newsFetchedOnce && !newsLoading) {
            fetchTodayNews(false);
        }

        if (!newsError.isEmpty()) {
            TextView error = bodyText(newsError, 13, th.accent);
            error.setPadding(dp(12), dp(10), dp(12), dp(10));
            error.setBackground(leftBorderBox(th.panel, th.accent, 3));
            content.addView(error);
            addGap(content, 12);
        }

        if (newsLoading && newsItems.isEmpty()) {
            content.addView(bodyText(t("Fetching today's headlines from Polish news feeds...", "Pobieram dzisiejsze nagłówki z polskich kanałów RSS..."), 13, th.muted));
            return;
        }

        if (newsFetchedOnce && newsItems.isEmpty()) {
            content.addView(bodyText(t("No dated items from today were found yet. Try Refresh later.", "Nie znaleziono jeszcze dzisiejszych pozycji. Spróbuj odświeżyć później."), 13, th.muted));
            return;
        }

        clampNewsIndex();
        prefetchNewsTranslations();

        content.addView(newsCard(newsItems.get(newsIndex)));
        addGap(content, 12);
        content.addView(newsPageIndicator());
    }

    private List<NewsSource> newsSources() {
        List<NewsSource> sources = new ArrayList<>();
        sources.add(new NewsSource("TVN24 Warszawa", "Warsaw local news and city updates.", "https://tvn24.pl/tvnwarszawa", "https://tvn24.pl/tvnwarszawa/najnowsze.xml"));
        sources.add(new NewsSource("Gazeta Wyborcza", "Warsaw reporting from Gazeta Wyborcza.", "https://wyborcza.pl", "https://warszawa.wyborcza.pl/pub/rss/warszawa.xml"));
        sources.add(new NewsSource("RMF24", "Breaking news, politics, economy, and public affairs.", "https://www.rmf24.pl", "https://www.rmf24.pl/fakty/feed"));
        sources.add(new NewsSource("Onet Wiadomości", "Broad Polish news portal for daily reading.", "https://wiadomosci.onet.pl", "https://wiadomosci.onet.pl/.feed"));
        sources.add(new NewsSource("Polsat News", "Polish news, politics, society, and live coverage.", "https://www.polsatnews.pl", "https://www.polsatnews.pl/rss/wszystkie.xml"));
        return sources;
    }

    private String newsStatusText() {
        if (newsLoading) {
            return t("Loading today's news...", "Ładowanie dzisiejszych wiadomości...");
        }
        if (!newsLastUpdated.isEmpty()) {
            String status = t("Today · updated ", "Dzisiaj · aktualizacja ") + newsLastUpdated;
            if (!newsItems.isEmpty()) {
                status += " · " + (newsIndex + 1) + "/" + newsItems.size();
            }
            if (!newsTranslationStatus.isEmpty()) {
                status += " · " + newsTranslationStatus;
            }
            return status;
        }
        return t("Today · Polish + English", "Dzisiaj · polski + angielski");
    }

    private View newsCard(NewsItem item) {
        Theme th = theme();
        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(th.panel, th.ink, th.radius, th.border));
        attachNewsSwipe(card);

        LinearLayout meta = row();
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(label(item.source, th.accent2, 10.5f, 0.08f), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        meta.addView(uiText(item.timeLabel, 10.5f, th.ghost, sansBold));
        card.addView(meta);
        addGap(card, 8);

        loadNewsImages(item);
        if (!item.images.isEmpty()) {
            card.addView(newsImages(item), topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 2));
            addGap(card, 10);
        }

        TextView title = serifText(item.title, 18.5f, th.ink);
        title.setTextIsSelectable(true);
        card.addView(title);
        if (!item.description.isEmpty()) {
            TextView description = bodyText(item.description, 13, th.muted);
            description.setTextIsSelectable(true);
            card.addView(description, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 7));
        }

        LinearLayout englishPanel = vertical();
        englishPanel.setPadding(dp(12), dp(10), dp(12), dp(10));
        englishPanel.setBackground(rounded(th.accentSoft, th.accent, th.radius, th.border));
        TextView englishLabel = label("ENGLISH", th.accent, 10.5f, 0.08f);
        englishPanel.addView(englishLabel);
        if (!item.englishTitle.isEmpty()) {
            TextView englishTitle = serifText(item.englishTitle, 17.5f, th.accent2Text);
            englishTitle.setTextIsSelectable(true);
            englishPanel.addView(englishTitle, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 5));
            if (!item.englishDescription.isEmpty()) {
                TextView englishDescription = bodyText(item.englishDescription, 13, th.accent2Text);
                englishDescription.setTextIsSelectable(true);
                englishPanel.addView(englishDescription, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 7));
            }
        } else {
            englishPanel.addView(bodyText(item.translationFailed
                    ? t("Translation unavailable.", "Tłumaczenie niedostępne.")
                    : t("Translating in app...", "Tłumaczę w aplikacji..."), 13, th.accent2Text), topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 5));
        }
        card.addView(englishPanel, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 12));

        Button open = flatButton(t("Open news", "Otwórz wiadomość"), th.accentSoft, th.accent, th.accent, 13, 40);
        open.setOnClickListener(v -> openWebUrl(item.link));
        card.addView(open, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40), 12));
        return card;
    }

    private View newsPageIndicator() {
        Theme th = theme();
        LinearLayout pager = row();
        pager.setGravity(Gravity.CENTER_VERTICAL);

        Button left = flatButton("<", th.panel, newsIndex > 0 ? th.ink : th.faint, th.dash, 16, 38);
        left.setEnabled(newsIndex > 0);
        left.setOnClickListener(v -> moveNewsPage(-1));
        pager.addView(left, new LinearLayout.LayoutParams(dp(44), dp(38)));

        TextView count = label((newsIndex + 1) + " / " + newsItems.size(), th.accent2, 11, 0.12f);
        count.setGravity(Gravity.CENTER);
        count.setPadding(0, dp(8), 0, dp(8));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(0, dp(38), 1);
        countParams.setMargins(dp(10), 0, dp(10), 0);
        pager.addView(count, countParams);

        Button right = flatButton(">", th.panel, newsIndex < newsItems.size() - 1 ? th.ink : th.faint, th.dash, 16, 38);
        right.setEnabled(newsIndex < newsItems.size() - 1);
        right.setOnClickListener(v -> moveNewsPage(1));
        pager.addView(right, new LinearLayout.LayoutParams(dp(44), dp(38)));
        return pager;
    }

    private View newsImages(NewsItem item) {
        LinearLayout media = row();
        media.setGravity(Gravity.CENTER);
        int imageCount = Math.min(2, item.images.size());
        for (int i = 0; i < imageCount; i++) {
            ImageView image = new ImageView(this);
            image.setImageBitmap(item.images.get(i));
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackgroundColor(theme().softLine);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(imageCount == 1 ? 178 : 132), 1);
            if (i > 0) {
                params.setMargins(dp(8), 0, 0, 0);
            }
            media.addView(image, params);
        }
        return media;
    }

    private void loadNewsImages(NewsItem item) {
        if (item.imageUrls.isEmpty() || item.imagesLoaded || item.imagesLoading) {
            return;
        }
        item.imagesLoading = true;
        new Thread(() -> {
            List<Bitmap> bitmaps = new ArrayList<>();
            int count = Math.min(2, item.imageUrls.size());
            for (int i = 0; i < count; i++) {
                try {
                    Bitmap bitmap = downloadNewsBitmap(item.imageUrls.get(i));
                    if (bitmap != null) {
                        bitmaps.add(bitmap);
                    }
                } catch (Exception ignored) {
                }
            }
            runOnUiThread(() -> {
                item.images.clear();
                item.images.addAll(bitmaps);
                item.imagesLoading = false;
                item.imagesLoaded = true;
                if (SCREEN_NEWS.equals(screen) && newsIndex < newsItems.size() && newsItems.get(newsIndex) == item) {
                    render();
                }
            });
        }).start();
    }

    private Bitmap downloadNewsBitmap(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("User-Agent", "Polish4Beginners-Android");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            return null;
        }
        try {
            Bitmap raw = BitmapFactory.decodeStream(connection.getInputStream());
            if (raw == null) {
                return null;
            }
            int maxWidth = 1200;
            int maxHeight = 720;
            float scale = Math.min(maxWidth / (float) raw.getWidth(), maxHeight / (float) raw.getHeight());
            if (scale >= 1f) {
                return raw;
            }
            Bitmap scaled = Bitmap.createScaledBitmap(raw, Math.max(1, Math.round(raw.getWidth() * scale)), Math.max(1, Math.round(raw.getHeight() * scale)), true);
            raw.recycle();
            return scaled;
        } finally {
            connection.disconnect();
        }
    }

    private void attachNewsSwipe(View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    newsTouchStartX = event.getX();
                    newsTouchStartY = event.getY();
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = event.getX() - newsTouchStartX;
                    float dy = event.getY() - newsTouchStartY;
                    if (Math.abs(dy) > dp(70) && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                        moveNewsPage(dy < 0 ? 1 : -1);
                        return true;
                    }
                    if (Math.abs(dx) > dp(70) && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        moveNewsPage(dx < 0 ? 1 : -1);
                        return true;
                    }
                    v.performClick();
                    return true;
                default:
                    return true;
            }
        });
    }

    private void moveNewsPage(int delta) {
        if (newsItems.isEmpty()) {
            return;
        }
        int nextIndex = Math.max(0, Math.min(newsItems.size() - 1, newsIndex + delta));
        if (nextIndex == newsIndex) {
            return;
        }
        newsIndex = nextIndex;
        trimNewsImageCache();
        render();
    }

    private void clampNewsIndex() {
        if (newsItems.isEmpty()) {
            newsIndex = 0;
        } else {
            newsIndex = Math.max(0, Math.min(newsItems.size() - 1, newsIndex));
        }
    }

    private void trimNewsImageCache() {
        for (int i = 0; i < newsItems.size(); i++) {
            if (Math.abs(i - newsIndex) > 3) {
                NewsItem item = newsItems.get(i);
                for (Bitmap bitmap : item.images) {
                    if (bitmap != null && !bitmap.isRecycled()) {
                        bitmap.recycle();
                    }
                }
                item.images.clear();
                item.imagesLoaded = false;
            }
        }
    }

    private void fetchTodayNews(boolean userStarted) {
        if (newsLoading) {
            return;
        }
        newsLoading = true;
        newsError = "";
        newsTranslationStatus = "";
        newsTranslating = false;
        if (userStarted && SCREEN_NEWS.equals(screen)) {
            render();
        }

        new Thread(() -> {
            List<NewsItem> fetched = new ArrayList<>();
            int failedSources = 0;
            for (NewsSource source : newsSources()) {
                try {
                    fetched.addAll(fetchNewsForSource(source));
                } catch (Exception e) {
                    failedSources++;
                }
            }
            Collections.sort(fetched, (a, b) -> b.publishedAt.compareTo(a.publishedAt));
            if (fetched.size() > 30) {
                fetched = new ArrayList<>(fetched.subList(0, 30));
            }
            final List<NewsItem> result = fetched;
            final int failures = failedSources;
            runOnUiThread(() -> {
                newsItems.clear();
                newsItems.addAll(result);
                newsIndex = 0;
                newsTranslationUnavailable = false;
                newsLoading = false;
                newsFetchedOnce = true;
                newsLastUpdated = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
                if (failures > 0 && result.isEmpty()) {
                    newsError = t("Could not load today's RSS feeds.", "Nie udało się wczytać dzisiejszych kanałów RSS.");
                } else if (failures > 0) {
                    newsError = t("Some sources did not respond.", "Niektóre źródła nie odpowiedziały.");
                } else {
                    newsError = "";
                }
                if (!newsItems.isEmpty()) {
                    prepareNewsTranslations();
                }
                if (SCREEN_NEWS.equals(screen)) {
                    render();
                }
            });
        }).start();
    }

    private void prepareNewsTranslations() {
        if (newsItems.isEmpty()) {
            return;
        }
        if (newsTranslationUnavailable) {
            return;
        }
        if (newsTranslatorReady) {
            prefetchNewsTranslations();
            return;
        }
        if (newsTranslatorPreparing) {
            return;
        }
        newsTranslating = true;
        newsTranslatorPreparing = true;
        newsTranslationStatus = t("preparing translator", "przygotowuję tłumacza");

        Translator translator = newsTranslator();
        DownloadConditions conditions = new DownloadConditions.Builder().build();
        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(v -> {
                    newsTranslatorReady = true;
                    newsTranslatorPreparing = false;
                    newsTranslationUnavailable = false;
                    prefetchNewsTranslations();
                    if (SCREEN_NEWS.equals(screen)) {
                        render();
                    }
                })
                .addOnFailureListener(e -> {
                    newsTranslating = false;
                    newsTranslatorPreparing = false;
                    newsTranslationUnavailable = true;
                    newsTranslationStatus = "";
                    for (NewsItem item : newsItems) {
                        item.translationFailed = true;
                    }
                    if (SCREEN_NEWS.equals(screen)) {
                        render();
                    }
                });
    }

    private void prefetchNewsTranslations() {
        if (newsItems.isEmpty()) {
            return;
        }
        if (newsTranslationUnavailable) {
            return;
        }
        if (!newsTranslatorReady) {
            prepareNewsTranslations();
            return;
        }
        clampNewsIndex();
        int end = Math.min(newsItems.size() - 1, newsIndex + NEWS_PREFETCH_AHEAD);
        for (int i = newsIndex; i <= end; i++) {
            translateNewsItemAt(i);
        }
        updateNewsTranslationStatus();
    }

    private Translator newsTranslator() {
        if (polishEnglishTranslator == null) {
            TranslatorOptions options = new TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.POLISH)
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build();
            polishEnglishTranslator = Translation.getClient(options);
        }
        return polishEnglishTranslator;
    }

    private void translateNewsItemAt(int index) {
        if (index < 0 || index >= newsItems.size() || !newsTranslatorReady) {
            return;
        }

        NewsItem item = newsItems.get(index);
        if (!needsNewsTranslation(item)) {
            return;
        }
        item.translationQueued = true;
        newsTranslating = true;
        updateNewsTranslationStatus();
        newsTranslator().translate(item.title)
                .addOnSuccessListener(translatedTitle -> {
                    item.englishTitle = translatedTitle.trim();
                    translateNewsDescription(index, item);
                })
                .addOnFailureListener(e -> {
                    item.translationFailed = true;
                    finishNewsItemTranslation(index, item);
                });
    }

    private void translateNewsDescription(int index, NewsItem item) {
        if (item.description.isEmpty()) {
            finishNewsItemTranslation(index, item);
            return;
        }
        newsTranslator().translate(item.description)
                .addOnSuccessListener(translatedDescription -> {
                    item.englishDescription = translatedDescription.trim();
                    finishNewsItemTranslation(index, item);
                })
                .addOnFailureListener(e -> {
                    item.translationFailed = true;
                    finishNewsItemTranslation(index, item);
                });
    }

    private void finishNewsItemTranslation(int index, NewsItem item) {
        item.translationQueued = false;
        item.translationComplete = true;
        int currentIndex = index;
        if (currentIndex < 0 || currentIndex >= newsItems.size() || newsItems.get(currentIndex) != item) {
            currentIndex = newsItems.indexOf(item);
        }
        updateNewsTranslationStatus();
        if (SCREEN_NEWS.equals(screen) && currentIndex >= newsIndex && currentIndex <= newsIndex + NEWS_PREFETCH_AHEAD) {
            render();
        }
    }

    private boolean needsNewsTranslation(NewsItem item) {
        return !item.translationQueued
                && !item.translationComplete
                && !item.translationFailed
                && item.englishTitle.isEmpty();
    }

    private void updateNewsTranslationStatus() {
        if (newsItems.isEmpty()) {
            newsTranslating = false;
            newsTranslationStatus = "";
            return;
        }
        if (newsTranslatorPreparing) {
            newsTranslating = true;
            newsTranslationStatus = t("preparing translator", "przygotowuję tłumacza");
            return;
        }
        int end = Math.min(newsItems.size() - 1, newsIndex + NEWS_PREFETCH_AHEAD);
        int ready = 0;
        int working = 0;
        int total = end - newsIndex + 1;
        for (int i = newsIndex; i <= end; i++) {
            NewsItem item = newsItems.get(i);
            if (item.translationQueued) {
                working++;
            }
            if (item.translationComplete || item.translationFailed || !item.englishTitle.isEmpty()) {
                ready++;
            }
        }
        newsTranslating = working > 0;
        newsTranslationStatus = t("prepared ", "przygotowano ") + ready + "/" + total;
    }

    private List<NewsItem> fetchNewsForSource(NewsSource source) throws Exception {
        List<NewsItem> items = new ArrayList<>();
        HttpURLConnection connection = (HttpURLConnection) new URL(source.feedUrl).openConnection();
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(9000);
        connection.setRequestProperty("User-Agent", "Polish4Beginners-Android");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("RSS returned HTTP " + code);
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) {
            }
            Document document = factory.newDocumentBuilder().parse(connection.getInputStream());
            NodeList itemNodes = document.getElementsByTagName("item");
            int sourceCount = 0;
            for (int i = 0; i < itemNodes.getLength() && sourceCount < 6; i++) {
                Node node = itemNodes.item(i);
                if (!(node instanceof Element)) {
                    continue;
                }
                Element element = (Element) node;
                String rawDescription = childText(element, "description");
                String title = cleanNewsText(childText(element, "title"));
                String link = cleanNewsText(childText(element, "link"));
                String description = shorten(cleanNewsText(rawDescription), 220);
                Date publishedAt = parseNewsDate(childText(element, "pubDate"));
                if (title.isEmpty() || link.isEmpty() || publishedAt == null || !isToday(publishedAt)) {
                    continue;
                }
                items.add(new NewsItem(source.name, title, description, link, formatNewsTime(publishedAt), publishedAt, extractNewsImageUrls(element, rawDescription)));
                sourceCount++;
            }
        } finally {
            connection.disconnect();
        }
        return items;
    }

    private String childText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0) == null) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }

    private List<String> extractNewsImageUrls(Element item, String rawDescription) {
        List<String> urls = new ArrayList<>();
        addNewsImageUrlsFromElements(item, urls);
        addNewsImageUrlsFromHtml(rawDescription, urls);
        addNewsImageUrlsFromHtml(childText(item, "content:encoded"), urls);
        return urls;
    }

    private void addNewsImageUrlsFromElements(Element parent, List<String> urls) {
        NodeList nodes = parent.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength() && urls.size() < 2; i++) {
            Node node = nodes.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element element = (Element) node;
            String name = element.getTagName().toLowerCase(Locale.US);
            boolean likelyImageNode = name.endsWith("thumbnail") || name.endsWith("enclosure") || name.endsWith("content") || name.endsWith("image");
            if (!likelyImageNode) {
                continue;
            }
            String type = element.getAttribute("type").toLowerCase(Locale.US);
            String medium = element.getAttribute("medium").toLowerCase(Locale.US);
            String url = firstNonEmpty(element.getAttribute("url"), element.getAttribute("href"));
            if (url.isEmpty()) {
                continue;
            }
            if (name.endsWith("thumbnail")
                    || medium.contains("image")
                    || type.startsWith("image/")
                    || looksLikeImageUrl(url)) {
                addNewsImageUrl(urls, url);
            }
        }
    }

    private void addNewsImageUrlsFromHtml(String html, List<String> urls) {
        if (html == null || html.isEmpty() || urls.size() >= 2) {
            return;
        }
        Matcher matcher = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html);
        while (matcher.find() && urls.size() < 2) {
            addNewsImageUrl(urls, matcher.group(1));
        }
    }

    private void addNewsImageUrl(List<String> urls, String rawUrl) {
        if (rawUrl == null || urls.size() >= 2) {
            return;
        }
        String url = rawUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return;
        }
        if (!urls.contains(url)) {
            urls.add(url);
        }
    }

    private boolean looksLikeImageUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.contains(".jpg")
                || lower.contains(".jpeg")
                || lower.contains(".png")
                || lower.contains(".webp")
                || lower.contains("/image/")
                || lower.contains("image=");
    }

    private String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private Date parseNewsDate(String rawDate) {
        String[] patterns = {
                "EEE, dd MMM yyyy HH:mm:ss Z",
                "EEE, dd MMM yyyy HH:mm Z",
                "EEE, dd MMM yyyy HH:mm:ss z",
                "dd MMM yyyy HH:mm:ss Z",
                "yyyy-MM-dd'T'HH:mm:ssXXX"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ENGLISH);
                format.setLenient(true);
                return format.parse(rawDate.trim());
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean isToday(Date date) {
        Calendar today = Calendar.getInstance();
        Calendar item = Calendar.getInstance();
        item.setTime(date);
        return today.get(Calendar.YEAR) == item.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == item.get(Calendar.DAY_OF_YEAR);
    }

    private String formatNewsTime(Date date) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
    }

    private String cleanNewsText(String raw) {
        if (raw == null) {
            return "";
        }
        String text;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            text = Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString();
        } else {
            text = Html.fromHtml(raw).toString();
        }
        // Html.fromHtml turns <img> into U+FFFC (object replacement), which RSS
        // descriptions commonly carry; strip it and other stray marks.
        text = text.replace("\ufffc", "").replace("\ufffd", "");
        text = text.replaceAll("[\\u200B-\\u200D\\uFEFF]", "");
        return text.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String shorten(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, Math.max(0, limit - 1)).trim() + "…";
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
        LinearLayout speedRowTop = row();
        addSpeedChoice(speedRowTop, t("Slowest", "Najwolniej"), SPEED_SLOWEST, true);
        addSpeedChoice(speedRowTop, t("Slow", "Wolno"), SPEED_SLOW, false);
        addSpeedChoice(speedRowTop, t("Normal", "Normalnie"), SPEED_NORMAL, false);
        speed.addView(speedRowTop, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42), 12));
        LinearLayout speedRowBottom = row();
        addSpeedChoice(speedRowBottom, t("Fast", "Szybko"), SPEED_FAST, true);
        addSpeedChoice(speedRowBottom, t("Fastest", "Najszybciej"), SPEED_FASTEST, false);
        speed.addView(speedRowBottom, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42), 8));
        content.addView(speed);
        addGap(content, 12);

        LinearLayout voice = settingsCard(t("Reading Voice", "Głos do czytania"), t("Reading aloud uses a text-to-speech engine on your device (including third-party engines like MultiTTS). Pick the engine and a voice per language.", "Czytanie na głos korzysta z silnika mowy na urządzeniu (także zewnętrznych, np. MultiTTS). Wybierz silnik i głos dla każdego języka."));
        final Locale plLocale = new Locale("pl", "PL");
        voice.addView(bodyText(t("Engine: ", "Silnik: ") + currentEngineLabel(), 12.5f, th.faint), topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));
        Button engineBtn = flatButton(t("Choose engine", "Wybierz silnik"), th.panel, th.ink, th.dash, 13, 42);
        engineBtn.setOnClickListener(v -> chooseTtsEngine());
        voice.addView(engineBtn, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42), 10));

        int plCount = voicesFor(plLocale).size();
        int enCount = voicesFor(Locale.US).size();
        String plVoice = selectedVoiceName(plLocale).isEmpty() ? t("engine default", "domyślny silnika") : selectedVoiceName(plLocale);
        String enVoice = selectedVoiceName(Locale.US).isEmpty() ? t("engine default", "domyślny silnika") : selectedVoiceName(Locale.US);
        voice.addView(bodyText(t("Polish voice: ", "Głos polski: ") + plVoice + "  (" + plCount + ")\n"
                + t("English voice: ", "Głos angielski: ") + enVoice + "  (" + enCount + ")", 12.5f, th.faint),
                topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 12));
        LinearLayout pickRow = row();
        Button plBtn = flatButton(t("Polish voice", "Głos polski"), th.accentSoft, th.accent, th.accent, 13, 42);
        plBtn.setOnClickListener(v -> chooseVoice(plLocale));
        pickRow.addView(plBtn, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button enBtn = flatButton(t("English voice", "Głos angielski"), th.accentSoft, th.accent, th.accent, 13, 42);
        enBtn.setOnClickListener(v -> chooseVoice(Locale.US));
        LinearLayout.LayoutParams enParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        enParams.setMargins(dp(10), 0, 0, 0);
        pickRow.addView(enBtn, enParams);
        voice.addView(pickRow, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42), 10));

        LinearLayout voiceRow = row();
        Button installVoice = flatButton(t("Install voice", "Zainstaluj głos"), th.panel, th.ink, th.dash, 13, 42);
        installVoice.setOnClickListener(v -> installVoiceData());
        voiceRow.addView(installVoice, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button ttsSettings = flatButton(t("TTS settings", "Ustawienia mowy"), th.panel, th.ink, th.dash, 13, 42);
        ttsSettings.setOnClickListener(v -> openTtsSettings());
        LinearLayout.LayoutParams tsParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        tsParams.setMargins(dp(10), 0, 0, 0);
        voiceRow.addView(ttsSettings, tsParams);
        voice.addView(voiceRow, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42), 10));
        content.addView(voice);
        addGap(content, 12);

        // App updates are delivered by Google Play; the app never installs APKs itself.
        LinearLayout update = settingsCard(t("App Updates", "Aktualizacje aplikacji"), t("Sideload build: checks GitHub for a newer APK.", "Wersja sideload: sprawdza nowszy APK w GitHub."));
        update.addView(bodyText("v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")", 12.5f, th.faint), topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));
        Button checkUpdates = flatButton(t("Check updates", "Sprawdź aktualizacje"), th.accentSoft, th.accent, th.accent, 13, 42);
        checkUpdates.setOnClickListener(v -> checkForUpdates(true));
        update.addView(checkUpdates, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42), 12));
        content.addView(update);
        addGap(content, 12);

        LinearLayout dataUpdate = settingsCard(t("Word Database", "Baza słów"), t("Update contributed phrases and vocabulary without reinstalling the app.", "Aktualizuj dodane frazy i słownictwo bez ponownej instalacji aplikacji."));
        dataUpdate.addView(bodyText(t("Database v", "Baza v") + currentDatabaseVersion() + " · " + phrases.size() + t(" cards", " kart"), 12.5f, th.faint), topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 8));
        Button checkData = flatButton(t("Update words", "Aktualizuj słowa"), th.accentSoft, th.accent, th.accent, 13, 42);
        checkData.setOnClickListener(v -> checkForDataUpdates(true));
        dataUpdate.addView(checkData, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42), 12));
        content.addView(dataUpdate);
    }

    private LinearLayout settingsCard(String title, String description) {
        Theme th = theme();
        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(th.panel, th.ink, th.radius, th.border));
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

    private void addSpeedChoice(LinearLayout row, String text, String value, boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
        if (!first) {
            params.setMargins(dp(8), 0, 0, 0);
        }
        row.addView(settingChoice(text, value.equals(speechSpeed), () -> setSpeechSpeed(value)), params);
    }

    private void setSpeechSpeed(String speed) {
        speechSpeed = speed;
        saveSetting("speechSpeed", speechSpeed);
        applySpeechRate();
        render();
    }

    private void saveSetting(String key, String value) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(key, value).apply();
    }

    private String fetchGitHubDocumentText(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "Polish4Beginners-Android");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("GitHub returned HTTP " + code);
        }
        try {
            JSONObject payload = new JSONObject(readStream(connection.getInputStream()));
            String encodedContent = payload.optString("content", "");
            if (!encodedContent.trim().isEmpty()) {
                byte[] decoded = Base64.decode(encodedContent, Base64.DEFAULT);
                return new String(decoded, StandardCharsets.UTF_8);
            }
            return payload.toString();
        } finally {
            connection.disconnect();
        }
    }

    private int currentDatabaseVersion() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getInt("dataVersion", DEFAULT_DATABASE_VERSION);
    }

    private void maybeCheckForDataUpdatesOnStart() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long lastCheck = prefs.getLong("lastDataCheckAt", 0L);
        if (now - lastCheck >= UPDATE_CHECK_INTERVAL_MS) {
            prefs.edit().putLong("lastDataCheckAt", now).apply();
            checkForDataUpdates(false);
        }
    }

    private void checkForDataUpdates(boolean userStarted) {
        if (userStarted) {
            Toast.makeText(this, t("Checking word database...", "Sprawdzam bazę słów..."), Toast.LENGTH_SHORT).show();
        }
        new Thread(() -> {
            try {
                JSONObject manifest = new JSONObject(fetchGitHubDocumentText(DATA_MANIFEST_URL));
                int latestVersion = manifest.optInt("dataVersion", DEFAULT_DATABASE_VERSION);
                String phrasesUrl = manifest.optString("phrasesUrl", "");
                if (latestVersion <= currentDatabaseVersion() || phrasesUrl.trim().isEmpty()) {
                    if (userStarted) {
                        runOnUiThread(() -> Toast.makeText(this, t("Word database is already current.", "Baza słów jest aktualna."), Toast.LENGTH_SHORT).show());
                    }
                    return;
                }
                String phraseJson = fetchGitHubDocumentText(phrasesUrl);
                int phraseCount = validatePhraseJson(phraseJson);
                saveRemotePhraseJson(phraseJson, latestVersion);
                runOnUiThread(() -> {
                    try {
                        loadPhrases();
                        loadMemory();
                        browseLimit = 25;
                        sessionDeck.clear();
                        sessionEnglishFront.clear();
                        Toast.makeText(this, t("Word database updated: ", "Baza słów zaktualizowana: ") + phraseCount + t(" cards", " kart"), Toast.LENGTH_LONG).show();
                        render();
                    } catch (Exception e) {
                        Toast.makeText(this, t("Downloaded database could not be loaded.", "Nie można wczytać pobranej bazy."), Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                if (userStarted) {
                    runOnUiThread(() -> Toast.makeText(this, t("Could not update word database.", "Nie udało się zaktualizować bazy słów."), Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    private int validatePhraseJson(String phraseJson) throws Exception {
        JSONArray array = new JSONArray(phraseJson);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            item.getString("polish");
            item.getString("english");
        }
        return array.length();
    }

    private void saveRemotePhraseJson(String phraseJson, int dataVersion) throws Exception {
        try (FileOutputStream output = openFileOutput(REMOTE_PHRASES_FILE, MODE_PRIVATE)) {
            output.write(phraseJson.getBytes(StandardCharsets.UTF_8));
        }
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt("dataVersion", dataVersion)
                .apply();
    }

    private void renderTranslate(LinearLayout content) {
        Theme th = theme();
        content.addView(screenTitle(t("Translate", "Tłumacz")));
        addGap(content, 8);
        content.addView(bodyText(t("On-device Polish–English translation. The first use downloads a small language pack.",
                "Tłumaczenie polsko-angielskie na urządzeniu. Pierwsze użycie pobiera mały pakiet językowy."), 13, th.muted));
        addGap(content, 14);

        String fromLang = translateEnToPl ? t("English", "Angielski") : t("Polish", "Polski");
        String toLang = translateEnToPl ? t("Polish", "Polski") : t("English", "Angielski");

        LinearLayout dir = row();
        dir.setGravity(Gravity.CENTER_VERTICAL);
        dir.addView(label(fromLang.toUpperCase(Locale.ROOT), th.accent2, 12, 0.1f), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button swap = flatButton("⇄", th.panel, th.ink, th.ink, 16, 40);
        swap.setOnClickListener(v -> {
            translateEnToPl = !translateEnToPl;
            String tmp = translateInput;
            translateInput = translateOutput;
            translateOutput = tmp;
            translateStatus = "";
            render();
        });
        dir.addView(swap, new LinearLayout.LayoutParams(dp(48), dp(40)));
        TextView toLabel = label(toLang.toUpperCase(Locale.ROOT), th.accent, 12, 0.1f);
        toLabel.setGravity(Gravity.RIGHT);
        dir.addView(toLabel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        content.addView(dir);
        addGap(content, 10);

        EditText input = new EditText(this);
        input.setText(translateInput);
        input.setHint(translateEnToPl ? t("Type English…", "Wpisz po angielsku…") : t("Type Polish…", "Wpisz po polsku…"));
        input.setTextSize(16);
        input.setTypeface(sansRegular);
        input.setTextColor(th.ink);
        input.setHintTextColor(th.faint);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(2);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(rounded(th.panel, th.ink, th.radius, th.border));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { translateInput = s.toString(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        input.setSelection(input.getText().length());
        content.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        addGap(content, 10);

        Button go = filledButton(translateBusy ? t("Translating…", "Tłumaczę…") : t("Translate", "Przetłumacz"), th.accent, th.onAccent, 15, 50);
        go.setEnabled(!translateBusy);
        go.setOnClickListener(v -> runTranslation());
        content.addView(go, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));

        if (!translateStatus.isEmpty()) {
            addGap(content, 10);
            TextView status = bodyText(translateStatus, 13, th.faint);
            status.setGravity(Gravity.CENTER);
            content.addView(status);
        }

        if (!translateOutput.isEmpty()) {
            addGap(content, 16);
            LinearLayout outCard = vertical();
            outCard.setPadding(dp(16), dp(14), dp(16), dp(14));
            outCard.setBackground(rounded(th.panel, th.accent2, th.radius, th.border));
            outCard.addView(label(toLang.toUpperCase(Locale.ROOT), th.accent2, 11, 0.1f));
            addGap(outCard, 6);
            TextView outText = serifText(translateOutput, 20, th.ink);
            outText.setLineSpacing(0, 1.05f);
            outText.setTextIsSelectable(true);
            outCard.addView(outText);
            addGap(outCard, 12);
            LinearLayout tools = row();
            final Locale outLocale = translateEnToPl ? new Locale("pl", "PL") : Locale.US;
            final String output = translateOutput;
            Button read = flatButton(t("Read", "Czytaj"), th.accentSoft, th.accent, th.accent, 12.5f, 38);
            read.setOnClickListener(v -> speak(output, outLocale));
            tools.addView(read, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)));
            Button copy = flatButton(t("Copy", "Kopiuj"), th.panel, th.muted, th.dash, 12.5f, 38);
            copy.setOnClickListener(v -> {
                ClipboardManager clip = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clip != null) {
                    clip.setPrimaryClip(ClipData.newPlainText("translation", output));
                    Toast.makeText(this, t("Copied.", "Skopiowano."), Toast.LENGTH_SHORT).show();
                }
            });
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
            copyParams.setMargins(dp(10), 0, 0, 0);
            tools.addView(copy, copyParams);
            outCard.addView(tools, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0));

            // Save this translation to the "My Words" study tag, one at a time.
            final String polishSide = translateEnToPl ? translateOutput.trim() : translateInput.trim();
            final String englishSide = translateEnToPl ? translateInput.trim() : translateOutput.trim();
            Button add = flatButton(t("Add to a list", "Dodaj do listy"), th.accent2, th.onAccent2, th.ink, 13, 44);
            add.setOnClickListener(v -> promptForTag(lastTag(), tag -> {
                if (saveCustomCard(polishSide, englishSide, level, tag)) {
                    loadPhrases();
                    loadMemory();
                    Toast.makeText(this, t("Added to ", "Dodano do ") + tag + ".", Toast.LENGTH_SHORT).show();
                    render();
                } else {
                    Toast.makeText(this, t("This word is already in your cards.", "To słowo jest już w Twoich kartach."), Toast.LENGTH_SHORT).show();
                }
            }));
            outCard.addView(add, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44), 10));
            content.addView(outCard);
        }

        addGap(content, 22);
        content.addView(new DashedLine(this, th.dash), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));
        addGap(content, 16);
        content.addView(label(t("YOUR WORD LISTS", "TWOJE LISTY SŁÓW"), th.accent, 11, 0.14f));
        addGap(content, 8);
        Map<String, Integer> tagCounts = customTagCounts();
        content.addView(bodyText(t("Download the template, fill in your words (leave one side blank to auto-translate), then upload. Imported words become study cards you can group into named lists. ",
                "Pobierz szablon, wpisz swoje słowa (zostaw jedną stronę pustą, aby przetłumaczyć automatycznie) i prześlij. Zaimportowane słowa stają się fiszkami w nazwanych listach. ")
                + t("You have ", "Masz ") + customCardCount() + t(" saved words in ", " zapisanych słów w ") + tagCounts.size() + t(" lists.", " listach."), 13, th.muted));
        addGap(content, 12);

        LinearLayout fileRow = row();
        Button template = flatButton(t("Download template", "Pobierz szablon"), th.panel, th.ink, th.ink, 13, 46);
        template.setOnClickListener(v -> downloadTemplate());
        fileRow.addView(template, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button upload = filledButton(t("Upload list", "Prześlij listę"), th.accent, th.onAccent, 13, 46);
        upload.setOnClickListener(v -> pickWordList());
        LinearLayout.LayoutParams upParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        upParams.setMargins(dp(10), 0, 0, 0);
        fileRow.addView(upload, upParams);
        content.addView(fileRow);

        for (Map.Entry<String, Integer> entry : tagCounts.entrySet()) {
            final String tag = entry.getKey();
            addGap(content, 10);
            Button study = flatButton(tag + "  ·  " + entry.getValue() + t(" cards →", " kart →"), th.accent2, th.onAccent2, th.ink, 14, 48);
            study.setOnClickListener(v -> startTagSession(tag));
            content.addView(study, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        }

        addGap(content, 22);
        content.addView(new DashedLine(this, th.dash), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));
        addGap(content, 16);
        content.addView(label(t("OFFLINE DICTIONARY", "SŁOWNIK OFFLINE"), th.accent, 11, 0.14f));
        addGap(content, 8);
        content.addView(bodyText(t("Upload your own dictionary (CSV, TSV or JSON: Polish then English). Then build the translations once — the app resolves every card and saves the result, so listening and study never look anything up while playing.",
                "Prześlij własny słownik (CSV, TSV lub JSON: polski, potem angielski). Następnie raz zbuduj tłumaczenia — aplikacja rozwiąże wszystkie karty i zapisze wynik, więc słuchanie i nauka nie szukają niczego podczas odtwarzania."), 13, th.muted));
        addGap(content, 10);
        content.addView(bodyText(t("Your dictionary: ", "Twój słownik: ") + userDictionary.size()
                + "   ·   " + t("Built translations: ", "Zbudowane tłumaczenia: ") + glossCache.size(), 12.5f, th.faint));
        if (!glossStatus.isEmpty()) {
            addGap(content, 6);
            content.addView(bodyText(glossStatus, 12.5f, th.accent));
        }
        addGap(content, 10);
        LinearLayout dictRow = row();
        Button upDict = flatButton(t("Upload dictionary", "Prześlij słownik"), th.panel, th.ink, th.ink, 13, 46);
        upDict.setOnClickListener(v -> pickDictionaryFile());
        dictRow.addView(upDict, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button build = filledButton(glossBuilding ? t("Building…", "Buduję…") : t("Build translations", "Zbuduj tłumaczenia"), th.accent, th.onAccent, 13, 46);
        build.setEnabled(!glossBuilding);
        build.setOnClickListener(v -> buildGlosses());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(46), 1);
        bp.setMargins(dp(10), 0, 0, 0);
        dictRow.addView(build, bp);
        content.addView(dictRow);
        if (userDictionary.size() > 0 || glossCache.size() > 0) {
            addGap(content, 10);
            Button clear = flatButton(t("Clear dictionary", "Wyczyść słownik"), th.panel, th.muted, th.dash, 12.5f, 42);
            clear.setOnClickListener(v -> clearDictionary());
            content.addView(clear, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)));
        }
    }

    private Translator translatorFor(boolean enToPl) {
        if (enToPl) {
            if (englishPolishTranslator == null) {
                TranslatorOptions options = new TranslatorOptions.Builder()
                        .setSourceLanguage(TranslateLanguage.ENGLISH)
                        .setTargetLanguage(TranslateLanguage.POLISH)
                        .build();
                englishPolishTranslator = Translation.getClient(options);
            }
            return englishPolishTranslator;
        }
        return newsTranslator();
    }

    private void runTranslation() {
        final String text = translateInput.trim();
        if (text.isEmpty()) {
            Toast.makeText(this, t("Type something to translate.", "Wpisz coś do przetłumaczenia."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (translateBusy) {
            return;
        }
        translateBusy = true;
        translateOutput = "";
        translateStatus = t("Preparing…", "Przygotowuję…");
        render();
        final boolean enToPl = translateEnToPl;
        final Translator translator = translatorFor(enToPl);
        DownloadConditions conditions = new DownloadConditions.Builder().build();
        translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener(ignored -> translator.translate(text)
                        .addOnSuccessListener(result -> {
                            translateBusy = false;
                            if (translateEnToPl != enToPl) {
                                return;
                            }
                            translateOutput = result.trim();
                            translateStatus = "";
                            if (SCREEN_TRANSLATE.equals(screen)) {
                                render();
                            }
                        })
                        .addOnFailureListener(e -> {
                            translateBusy = false;
                            translateStatus = t("Translation failed.", "Tłumaczenie nie powiodło się.");
                            if (SCREEN_TRANSLATE.equals(screen)) {
                                render();
                            }
                        }))
                .addOnFailureListener(e -> {
                    translateBusy = false;
                    translateStatus = t("Could not download the language pack. Connect to the internet and try again.",
                            "Nie udało się pobrać pakietu językowego. Połącz się z internetem i spróbuj ponownie.");
                    if (SCREEN_TRANSLATE.equals(screen)) {
                        render();
                    }
                });
    }

    private void downloadTemplate() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "polish_wordlist_template.csv");
        try {
            startActivityForResult(intent, REQ_SAVE_TEMPLATE);
        } catch (Exception e) {
            Toast.makeText(this, t("No app to save files.", "Brak aplikacji do zapisu plików."), Toast.LENGTH_SHORT).show();
        }
    }

    private void pickWordList() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_OPEN_LIST);
        } catch (Exception e) {
            Toast.makeText(this, t("No app to pick files.", "Brak aplikacji do wyboru plików."), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQ_SAVE_TEMPLATE || requestCode == REQ_SAVE_DIALOG_TEMPLATE) {
            String payload = requestCode == REQ_SAVE_TEMPLATE ? WORDLIST_TEMPLATE : DIALOG_TEMPLATE;
            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    out.write(payload.getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, t("Template saved.", "Szablon zapisany."), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, t("Could not save the template.", "Nie udało się zapisać szablonu."), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_OPEN_DIALOG) {
            importDialogFile(uri);
        } else if (requestCode == REQ_OPEN_DICTIONARY) {
            importDictionary(uri);
        } else if (requestCode == REQ_OPEN_LIST) {
            try {
                final List<String[]> rows = parseWordListCsv(readLines(getContentResolver().openInputStream(uri)));
                if (rows.isEmpty()) {
                    Toast.makeText(this, t("No words found in that file.", "Nie znaleziono słów w tym pliku."), Toast.LENGTH_LONG).show();
                    return;
                }
                screen = SCREEN_TRANSLATE;
                promptForTag(lastTag(), tag -> importRows(rows, tag));
            } catch (Exception e) {
                Toast.makeText(this, t("Could not read that file.", "Nie udało się odczytać pliku."), Toast.LENGTH_LONG).show();
            }
        }
    }

    private List<String> readLines(InputStream stream) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    // Each returned row is {polish, english, level, tag}; header row is skipped.
    // tag (4th column) is optional and defaults to "" (filled in at import time).
    private List<String[]> parseWordListCsv(List<String> lines) {
        List<String[]> rows = new ArrayList<>();
        boolean first = true;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            List<String> parts = splitCsvLine(line);
            String pl = parts.size() > 0 ? parts.get(0).trim() : "";
            String en = parts.size() > 1 ? parts.get(1).trim() : "";
            String lvl = parts.size() > 2 ? parts.get(2).trim() : "";
            String tag = parts.size() > 3 ? parts.get(3).trim() : "";
            if (first) {
                first = false;
                if (pl.equalsIgnoreCase("polish") || en.equalsIgnoreCase("english")) {
                    continue; // header
                }
            }
            if (pl.isEmpty() && en.isEmpty()) {
                continue;
            }
            rows.add(new String[]{pl, en, lvl, tag});
        }
        return rows;
    }

    // Minimal RFC-4180 field splitter: fields may be double-quoted and then
    // contain commas; "" inside a quoted field is a literal quote.
    private List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields;
    }

    // defaultTag is used for any row whose CSV tag column is blank.
    private void importRows(List<String[]> rows, String defaultTag) {
        translateBusy = true;
        translateOutput = "";
        translateStatus = t("Importing ", "Importuję ") + rows.size() + t(" words…", " słów…");
        render();
        processImportRow(rows, 0, new ArrayList<>(), defaultTag);
    }

    // Walks the list one row at a time, translating whichever side is blank.
    private void processImportRow(List<String[]> rows, int index, List<String[]> ready, String defaultTag) {
        if (index >= rows.size()) {
            finishImport(ready);
            return;
        }
        String[] r = rows.get(index);
        final String pl = r[0].trim();
        final String en = r[1].trim();
        final String lvl = r[2];
        final String tag = (r.length > 3 && !r[3].trim().isEmpty()) ? r[3].trim() : defaultTag;
        if (!pl.isEmpty() && !en.isEmpty()) {
            ready.add(new String[]{pl, en, lvl, tag});
            processImportRow(rows, index + 1, ready, defaultTag);
            return;
        }
        final boolean enToPl = pl.isEmpty(); // english filled, need polish
        final String source = enToPl ? en : pl;
        translateStatus = t("Translating ", "Tłumaczę ") + (index + 1) + "/" + rows.size() + "…";
        if (SCREEN_TRANSLATE.equals(screen)) {
            render();
        }
        final Translator translator = translatorFor(enToPl);
        translator.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(ignored -> translator.translate(source)
                        .addOnSuccessListener(result -> {
                            if (enToPl) {
                                ready.add(new String[]{result.trim(), en, lvl, tag});
                            } else {
                                ready.add(new String[]{pl, result.trim(), lvl, tag});
                            }
                            processImportRow(rows, index + 1, ready, defaultTag);
                        })
                        .addOnFailureListener(e -> processImportRow(rows, index + 1, ready, defaultTag)))
                .addOnFailureListener(e -> {
                    translateBusy = false;
                    translateStatus = t("Could not download the language pack. Connect to the internet and try again.",
                            "Nie udało się pobrać pakietu językowego. Połącz się z internetem i spróbuj ponownie.");
                    if (SCREEN_TRANSLATE.equals(screen)) {
                        render();
                    }
                });
    }

    private void finishImport(List<String[]> ready) {
        int added = 0;
        for (String[] r : ready) {
            if (saveCustomCard(r[0], r[1], r[2], r.length > 3 ? r[3] : MY_WORDS_CATEGORY)) {
                added++;
            }
        }
        loadPhrases();
        loadMemory();
        translateBusy = false;
        translateStatus = t("Added ", "Dodano ") + added + t(" words to your lists.", " słów do Twoich list.");
        if (SCREEN_TRANSLATE.equals(screen)) {
            render();
        }
    }

    /** Czytaj: News and Conversations behind one top toggle. */
    private void renderRead(LinearLayout content) {
        Theme th = theme();
        LinearLayout toggle = row();
        Button news = flatButton(t("News", "Wiadomości"),
                readShowsDialogs ? th.panel : th.accent,
                readShowsDialogs ? th.muted : th.onAccent, th.ink, 14, 46);
        news.setOnClickListener(v -> {
            stopAudioPlayback();
            readShowsDialogs = false;
            render();
        });
        toggle.addView(news, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button talks = flatButton(t("Conversations", "Rozmowy"),
                readShowsDialogs ? th.accent : th.panel,
                readShowsDialogs ? th.onAccent : th.muted, th.ink, 14, 46);
        talks.setOnClickListener(v -> {
            readShowsDialogs = true;
            render();
        });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(46), 1);
        tp.setMargins(dp(10), 0, 0, 0);
        toggle.addView(talks, tp);
        content.addView(toggle);
        addGap(content, 16);
        if (readShowsDialogs) {
            renderDialogs(content);
        } else {
            renderNews(content);
        }
    }

    /** Więcej: the destinations that no longer have their own tab. */
    private void renderMore(LinearLayout content) {
        Theme th = theme();
        content.addView(screenTitle(t("More", "Więcej")));
        addGap(content, 14);
        addMoreRow(content, t("Grammar", "Gramatyka"), t("23 lessons with self-checks", "23 lekcje z autotestem"), SCREEN_GRAMMAR, th.accent);
        addMoreRow(content, t("Alphabet", "Alfabet"), t("39 letters and sounds", "39 liter i dźwięków"), SCREEN_ALPHABET, th.accent2);
        addMoreRow(content, t("Translate", "Tłumacz"), t("Dictionary, word lists, imports", "Słownik, listy słów, import"), SCREEN_TRANSLATE, th.accent3);
        addMoreRow(content, t("Settings", "Ustawienia"), t("Theme, voice, reading speed", "Motyw, głos, szybkość czytania"), SCREEN_SETTINGS, th.accentAlt);
    }

    private void addMoreRow(LinearLayout content, String title, String subtitle, final String target, int chipColor) {
        Theme th = theme();
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackground(rounded(th.panel, th.ink, th.radius, th.border));
        View chip = new View(this);
        chip.setBackground(rounded(chipColor, th.ink, th.radius / 2f, th.border));
        row.addView(chip, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout copy = vertical();
        copy.setPadding(dp(12), 0, 0, 0);
        copy.addView(serifText(title, 17, th.ink));
        copy.addView(uiText(subtitle, 12, th.muted, sansRegular));
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(uiText("›", 20, th.ghost, sansBold));
        row.setOnClickListener(v -> {
            screen = target;
            render();
        });
        content.addView(shadowWrap(row, 4));
        addGap(content, 10);
    }

    private void maybeCheckForUpdatesOnStart() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long lastCheck = prefs.getLong("lastUpdateCheckAt", 0L);
        if (now - lastCheck >= UPDATE_CHECK_INTERVAL_MS) {
            prefs.edit().putLong("lastUpdateCheckAt", now).apply();
            checkForUpdates(false);
        }
    }

    private void checkForUpdates(boolean userStarted) {
        if (userStarted) {
            Toast.makeText(this, t("Checking GitHub for updates...", "Sprawdzam aktualizacje w GitHub..."), Toast.LENGTH_SHORT).show();
        }
        new Thread(() -> {
            try {
                JSONObject manifest = fetchUpdateManifest();
                int latestCode = manifest.optInt("versionCode", BuildConfig.VERSION_CODE);
                String latestName = manifest.optString("versionName", "");
                String apkUrl = manifest.optString("apkUrl", "");
                String notes = manifest.optString("releaseNotes", "");
                runOnUiThread(() -> {
                    if (latestCode > BuildConfig.VERSION_CODE && !apkUrl.trim().isEmpty()) {
                        showUpdateAvailable(latestName, notes, apkUrl);
                    } else if (userStarted) {
                        Toast.makeText(this, t("You already have the latest version.", "Masz już najnowszą wersję."), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                if (userStarted) {
                    runOnUiThread(() -> Toast.makeText(this, t("Could not check updates.", "Nie udało się sprawdzić aktualizacji."), Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    private JSONObject fetchUpdateManifest() throws Exception {
        return new JSONObject(fetchGitHubDocumentText(UPDATE_MANIFEST_URL));
    }

    private void showUpdateAvailable(String versionName, String notes, String apkUrl) {
        String title = t("Update available", "Dostępna aktualizacja");
        String version = versionName.trim().isEmpty() ? "" : "v" + versionName + "\n\n";
        String message = version + (notes.trim().isEmpty()
                ? t("Download the newest APK from GitHub?", "Pobrać najnowszy APK z GitHub?")
                : notes);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(t("Download", "Pobierz"), (dialog, which) -> downloadUpdate(apkUrl))
                .setNegativeButton(t("Later", "Później"), null)
                .show();
    }

    private void downloadUpdate(String apkUrl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, t("Allow this app to install updates, then check again.", "Zezwól tej aplikacji na instalowanie aktualizacji, potem sprawdź ponownie."), Toast.LENGTH_LONG).show();
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            return;
        }

        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (manager == null) {
            Toast.makeText(this, t("Download service is not available.", "Usługa pobierania jest niedostępna."), Toast.LENGTH_SHORT).show();
            return;
        }
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
        request.setTitle("P4B.apk");
        request.setDescription(t("Downloading Polish4Beginners update", "Pobieranie aktualizacji Polish4Beginners"));
        request.setMimeType(APK_MIME_TYPE);
        request.setAllowedOverMetered(true);
        request.setAllowedOverRoaming(true);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "P4B.apk");
        updateDownloadId = manager.enqueue(request);
        Toast.makeText(this, t("Downloading update...", "Pobieranie aktualizacji..."), Toast.LENGTH_SHORT).show();
    }

    private void registerUpdateDownloadReceiver() {
        updateDownloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (completedId == updateDownloadId) {
                    openDownloadedUpdate(completedId);
                }
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateDownloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateDownloadReceiver, filter);
        }
    }

    private void openDownloadedUpdate(long downloadId) {
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (manager == null) {
            return;
        }
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = manager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return;
            }
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (statusIndex < 0 || cursor.getInt(statusIndex) != DownloadManager.STATUS_SUCCESSFUL) {
                Toast.makeText(this, t("Update download failed.", "Pobieranie aktualizacji nie powiodło się."), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Uri apkUri = manager.getUriForDownloadedFile(downloadId);
        if (apkUri == null) {
            Toast.makeText(this, t("Downloaded APK could not be opened.", "Nie można otworzyć pobranego APK."), Toast.LENGTH_SHORT).show();
            return;
        }
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(apkUri, APK_MIME_TYPE);
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(install);
        } catch (Exception e) {
            Toast.makeText(this, t("Could not open Android installer.", "Nie można otworzyć instalatora Androida."), Toast.LENGTH_SHORT).show();
        }
    }

    private View bottomNav() {
        Theme th = theme();
        LinearLayout nav = row();
        nav.setPadding(dp(8), dp(6), dp(8), dp(2));
        nav.setGravity(Gravity.CENTER);
        nav.setBackground(topBorder(th.panel, th.ink, 2));
        nav.addView(navItem("home", t("Home", "Dom"), SCREEN_HOME), new LinearLayout.LayoutParams(0, dp(56), 1));
        nav.addView(navItem("browse", t("Cards", "Karty"), SCREEN_BROWSE), new LinearLayout.LayoutParams(0, dp(56), 1));
        nav.addView(navItem("listen", t("Listen", "Słuchaj"), SCREEN_LISTEN), new LinearLayout.LayoutParams(0, dp(56), 1));
        nav.addView(navItem("read", t("Read", "Czytaj"), SCREEN_READ), new LinearLayout.LayoutParams(0, dp(56), 1));
        nav.addView(navItem("more", t("More", "Więcej"), SCREEN_MORE), new LinearLayout.LayoutParams(0, dp(56), 1));
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
            stopAudioPlayback();
            screen = target;
            render();
        });
        item.addView(new NavIcon(this, icon, color), new LinearLayout.LayoutParams(dp(21), dp(21)));
        // Seven tabs are tight on a phone: clip each label to its own cell so
        // long words (Translate, Grammar) can't bleed into their neighbours.
        TextView label = uiText(text, 9.5f, color, sansBold);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(dp(2), 0, dp(2), 0);
        item.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return item;
    }

    // Daily quick session from the hero: capped to SESSION_SIZE cards.
    private void startSession(String topic) {
        startSession(topic, SESSION_SIZE);
    }

    // limit <= 0 means "use the whole pool" (topic sessions run until every
    // not-yet-learnt card at this level is finished).
    private void startSession(String topic, int limit) {
        List<Phrase> dueReviews = new ArrayList<>();
        List<Phrase> fresh = new ArrayList<>();
        List<Phrase> scheduled = new ArrayList<>();
        for (Phrase phrase : phrases) {
            if (!level.equals(phrase.level)) {
                continue;
            }
            if (!"All".equals(topic) && !topic.equals(phrase.category)) {
                continue;
            }
            if (isDue(phrase)) {
                dueReviews.add(phrase);
            } else if (cardMemory(phrase).box == 0) {
                fresh.add(phrase);
            } else {
                scheduled.add(phrase);
            }
        }
        Collections.shuffle(dueReviews);
        Collections.shuffle(fresh);
        // Due reviews first, then new cards; learnt (scheduled-ahead) cards are
        // left out unless nothing else remains.
        List<Phrase> pool = new ArrayList<>(dueReviews);
        pool.addAll(fresh);
        if (pool.isEmpty()) {
            Collections.sort(scheduled, (a, b) -> Long.compare(cardMemory(a).dueAt, cardMemory(b).dueAt));
            pool.addAll(scheduled);
        }
        beginSession(pool, limit);
    }

    // Flashcard review of a memory bucket (0 = new, 1 = to review, 2 = learnt),
    // matching the home stat cells. Spans all levels, like the counts do.
    private void startStatusSession(int statusIndex) {
        List<Phrase> pool = new ArrayList<>();
        for (Phrase phrase : phrases) {
            if (statusOf(phrase) == statusIndex) {
                pool.add(phrase);
            }
        }
        Collections.shuffle(pool);
        beginSession(pool, 0);
    }

    private int statusOf(Phrase phrase) {
        CardMemory state = cardMemory(phrase);
        return LearningLogic.statusOf(state.box, state.dueAt, System.currentTimeMillis());
    }

    private void beginSession(List<Phrase> pool, int limit) {
        if (pool.isEmpty()) {
            Toast.makeText(this, t("Nothing to study here yet.", "Nic tu jeszcze do nauki."), Toast.LENGTH_SHORT).show();
            return;
        }
        int size = (limit <= 0) ? pool.size() : Math.min(limit, pool.size());
        sessionDeck.clear();
        sessionDeck.addAll(pool.subList(0, size));
        // Randomize which side is shown first, per card, fresh each session.
        sessionEnglishFront.clear();
        for (int i = 0; i < sessionDeck.size(); i++) {
            sessionEnglishFront.add(Math.random() < 0.5);
        }
        sessionIndex = 0;
        sessionGot = 0;
        sessionRevealed = false;
        screen = SCREEN_SESSION;
        render();
    }

    private void answer(boolean got) {
        if (sessionIndex < sessionDeck.size()) {
            Phrase phrase = sessionDeck.get(sessionIndex);
            recordAnswer(phrase, got);
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
        return (phrase.polish + " " + phrase.english + " " + phrase.phonetic + " "
                + phrase.examplePolish + " " + phrase.exampleEnglish + " "
                + phrase.notes + " " + phrase.category).toLowerCase(Locale.ROOT);
    }

    private boolean firstVisit() {
        for (Phrase phrase : phrases) {
            if (cardMemory(phrase).box > 0) {
                return false;
            }
        }
        return true;
    }

    private int dueReviewCountForLevel() {
        int count = 0;
        for (Phrase phrase : phrases) {
            if (level.equals(phrase.level) && isDue(phrase)) {
                count++;
            }
        }
        return count;
    }

    private int newCountForLevel() {
        int count = 0;
        for (Phrase phrase : phrases) {
            if (level.equals(phrase.level) && cardMemory(phrase).box == 0) {
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

    private CardMemory cardMemory(Phrase phrase) {
        CardMemory state = memory.get(phrase.key());
        return state == null ? new CardMemory(0, 0) : state;
    }

    private String getMemoryStatus(Phrase phrase) {
        CardMemory state = cardMemory(phrase);
        if (state.box == 0) {
            return STATUS_NEW;
        }
        return state.dueAt <= System.currentTimeMillis() ? STATUS_FORGOT : STATUS_LEARNT;
    }

    private boolean isDue(Phrase phrase) {
        CardMemory state = cardMemory(phrase);
        return LearningLogic.isDue(state.box, state.dueAt, System.currentTimeMillis());
    }

    private void recordAnswer(Phrase phrase, boolean got) {
        CardMemory state = cardMemory(phrase);
        long now = System.currentTimeMillis();
        int box = LearningLogic.nextBox(state.box, got);
        long dueAt = LearningLogic.nextDueAt(box, got, now);
        saveCardMemory(phrase, new CardMemory(box, dueAt));
    }

    private void saveCardMemory(Phrase phrase, CardMemory state) {
        memory.put(phrase.key(), state);
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(MEMORY_PREFIX + phrase.key(), LearningLogic.encodeMemory(state.box, state.dueAt))
                .apply();
    }

    private void sharePhrase(Phrase phrase) {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, phrase.polish + "\n" + phrase.english);
        startActivity(Intent.createChooser(share, t("Share phrase", "Udostępnij frazę")));
    }

    private void openGoogleTranslate(String text, String sourceLanguage, String targetLanguage) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("Polish phrase", text));
        }

        Intent translate = new Intent(Intent.ACTION_SEND);
        translate.setType("text/plain");
        translate.putExtra(Intent.EXTRA_TEXT, text);
        translate.setPackage("com.google.android.apps.translate");
        try {
            startActivity(translate);
            Toast.makeText(this, t("Copied and sent to Google Translate.", "Skopiowano i wysłano do Google Translate."), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            translate.setPackage(null);
            startActivity(Intent.createChooser(translate, t("Share to translation app", "Udostępnij do tłumacza")));
        }
    }

    private void openWebUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, t("Could not open news site.", "Nie można otworzyć strony z wiadomościami."), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadMemory() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        SharedPreferences.Editor migration = prefs.edit();
        boolean migrated = false;
        memory.clear();
        for (Phrase phrase : phrases) {
            String stored = prefs.getString(MEMORY_PREFIX + phrase.key(), "");
            CardMemory state = parseCardMemory(stored);
            memory.put(phrase.key(), state);
            if (state.box > 0 && stored.indexOf('|') < 0) {
                migration.putString(MEMORY_PREFIX + phrase.key(), LearningLogic.encodeMemory(state.box, state.dueAt));
                migrated = true;
            }
        }
        if (migrated) {
            migration.apply();
        }
    }

    private CardMemory parseCardMemory(String stored) {
        long[] decoded = LearningLogic.decodeMemory(stored, System.currentTimeMillis());
        return new CardMemory((int) decoded[0], decoded[1]);
    }

    private void speak(String text, Locale locale) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (!ttsReady) {
            Toast.makeText(this, "TextToSpeech is not ready.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!applySavedVoice(locale)) {
            int result = textToSpeech.setLanguage(locale);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                promptInstallVoice(locale);
                return;
            }
        }
        applySpeechRate();
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "learning-card");
    }

    private void speakAlphabetItem(AlphabetItem item) {
        if (!ttsReady) {
            Toast.makeText(this, "TextToSpeech is not ready.", Toast.LENGTH_SHORT).show();
            return;
        }
        Locale polish = new Locale("pl", "PL");
        if (!applySavedVoice(polish)) {
            int result = textToSpeech.setLanguage(polish);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                promptInstallVoice(polish);
                return;
            }
        }
        applySpeechRate();
        String letter = item.letter.replace(" ", ", ");
        textToSpeech.speak(letter, TextToSpeech.QUEUE_FLUSH, null, "alphabet-letter");
        applySpeechRate();
        textToSpeech.speak(item.example, TextToSpeech.QUEUE_ADD, null, "alphabet-example");
    }

    private void applySpeechRate() {
        if (textToSpeech != null) {
            textToSpeech.setSpeechRate(speechRate());
        }
    }

    // ---- User dictionary + prebuilt glosses -------------------------------
    // A dictionary is imported once and kept as an in-memory map; the gloss
    // build resolves every card up front (dictionary first, on-device
    // translation only for the gaps) and caches the result to disk, so
    // listening/study never does a lookup or a translation mid-playback.

    private String dictKey(String polish) {
        return LearningLogic.normalizeHeadword(polish);
    }


    /**
     * Exact dictionary hit only — the user's own dictionary first, then the
     * bundled one. Deliberately strict: an exact hit is trustworthy and is
     * shown as the answer.
     */
    private String lookupWord(String word) {
        String key = dictKey(word);
        if (key.isEmpty()) {
            return null;
        }
        String hit = userDictionary.get(key);
        return (hit == null || hit.isEmpty()) ? null : hit;
    }

    /**
     * Best-effort base form for an inflected word, as "słowo — gloss".
     *
     * Polish stem changes mean suffix trimming alone can land on the wrong
     * lemma (e.g. "dzwoni" trims to the noun "dzwon" when the verb "dzwonić"
     * was meant), so this is never presented as the translation — only as a
     * dictionary hint alongside a real translation.
     */
    private String lookupBaseForm(String word) {
        String key = dictKey(word);
        if (key.isEmpty() || key.length() < 4) {
            return null;
        }
        for (String suffix : INFLECTION_SUFFIXES) {
            if (!key.endsWith(suffix) || key.length() <= suffix.length() + 1) {
                continue;
            }
            String stem = key.substring(0, key.length() - suffix.length());
            for (String restore : INFLECTION_RESTORES) {
                String candidate = stem + restore;
                if (candidate.equals(key)) {
                    continue;
                }
                String gloss = userDictionary.get(candidate);
                if (gloss != null && !gloss.isEmpty()) {
                    return candidate + " — " + gloss;
                }
            }
        }
        return null;
    }

    private void loadDictionaryAsync() {
        if (dictionaryLoading) {
            return;
        }
        dictionaryLoading = true;
        new Thread(() -> {
            final Map<String, String> dict = new HashMap<>();
            final Map<String, String> gloss = new HashMap<>();
            try {
                File f = new File(getFilesDir(), DICTIONARY_FILE);
                if (f.exists()) {
                    JSONObject o = new JSONObject(readStream(openFileInput(DICTIONARY_FILE)));
                    for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                        String k = it.next();
                        dict.put(k, o.optString(k));
                    }
                }
            } catch (Exception ignored) {
            }
            try {
                File g = new File(getFilesDir(), GLOSS_FILE);
                if (g.exists()) {
                    JSONObject o = new JSONObject(readStream(openFileInput(GLOSS_FILE)));
                    for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                        String k = it.next();
                        gloss.put(k, o.optString(k));
                    }
                }
            } catch (Exception ignored) {
            }
            runOnUiThread(() -> {
                userDictionary.clear();
                userDictionary.putAll(dict);
                glossCache.clear();
                glossCache.putAll(gloss);
                dictionaryLoading = false;
                if (SCREEN_TRANSLATE.equals(screen) || SCREEN_LISTEN.equals(screen)) {
                    render();
                }
            });
        }).start();
    }

    // The translation shown for a card: prebuilt gloss, else dictionary, else
    // the card's own English. Pure map access — safe to call during playback.
    private String glossFor(Phrase phrase) {
        String cached = glossCache.get(phrase.key());
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }
        String dict = userDictionary.get(dictKey(phrase.polish));
        if (dict != null && !dict.isEmpty()) {
            return dict;
        }
        return phrase.english;
    }

    private void importDictionary(Uri uri) {
        try {
            List<String> lines = readLines(getContentResolver().openInputStream(uri));
            StringBuilder joined = new StringBuilder();
            for (String l : lines) {
                joined.append(l).append('\n');
            }
            String raw = joined.toString().trim();
            // Editors (notably Excel) prepend a UTF-8 BOM; it would defeat the
            // '{' / '[' format sniff below and silently yield zero entries.
            if (!raw.isEmpty() && raw.charAt(0) == '\uFEFF') {
                raw = raw.substring(1).trim();
                if (!lines.isEmpty()) {
                    lines.set(0, lines.get(0).replace("\uFEFF", ""));
                }
            }
            Map<String, String> parsed = new HashMap<>();

            if (raw.startsWith("{")) {
                JSONObject o = new JSONObject(raw);
                for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                    String k = it.next();
                    parsed.put(dictKey(k), o.optString(k).trim());
                }
            } else if (raw.startsWith("[")) {
                JSONArray a = new JSONArray(raw);
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    String pl = o.optString("polish", o.optString("word", "")).trim();
                    String en = o.optString("english", o.optString("translation", "")).trim();
                    if (!pl.isEmpty() && !en.isEmpty()) {
                        parsed.put(dictKey(pl), en);
                    }
                }
            } else {
                boolean first = true;
                for (String line : lines) {
                    String s = line.trim();
                    if (s.isEmpty()) {
                        continue;
                    }
                    List<String> parts = s.contains("\t")
                            ? java.util.Arrays.asList(s.split("\t", -1))
                            : splitCsvLine(s);
                    if (parts.size() < 2) {
                        continue;
                    }
                    String pl = parts.get(0).trim();
                    String en = parts.get(1).trim();
                    if (first) {
                        first = false;
                        if (pl.equalsIgnoreCase("polish") || en.equalsIgnoreCase("english")) {
                            continue;
                        }
                    }
                    if (!pl.isEmpty() && !en.isEmpty()) {
                        parsed.put(dictKey(pl), en);
                    }
                }
            }

            if (parsed.isEmpty()) {
                Toast.makeText(this, t("No entries found in that dictionary.", "Nie znaleziono haseł w tym słowniku."), Toast.LENGTH_LONG).show();
                return;
            }
            JSONObject out = new JSONObject();
            for (Map.Entry<String, String> e : parsed.entrySet()) {
                out.put(e.getKey(), e.getValue());
            }
            try (FileOutputStream fos = openFileOutput(DICTIONARY_FILE, MODE_PRIVATE)) {
                fos.write(out.toString().getBytes(StandardCharsets.UTF_8));
            }
            userDictionary.clear();
            for (Map.Entry<String, String> e : parsed.entrySet()) {
                userDictionary.put(e.getKey(), e.getValue());
            }
            screen = SCREEN_TRANSLATE;
            glossStatus = t("Dictionary loaded: ", "Wczytano słownik: ") + parsed.size() + t(" entries. Build translations to apply them.", " haseł. Zbuduj tłumaczenia, aby je zastosować.");
            render();
        } catch (Exception e) {
            Toast.makeText(this, t("Could not read that dictionary file.", "Nie udało się odczytać pliku słownika."), Toast.LENGTH_LONG).show();
        }
    }

    private void clearDictionary() {
        deleteFile(DICTIONARY_FILE);
        deleteFile(GLOSS_FILE);
        userDictionary.clear();
        glossCache.clear();
        glossStatus = t("Dictionary and built translations cleared.", "Słownik i zbudowane tłumaczenia usunięte.");
        render();
    }

    // One-time pass over the deck: dictionary hits are instant; only cards with
    // no gloss and no English are sent to the on-device translator, one at a
    // time, and everything is written to the cache file at the end.
    private void buildGlosses() {
        if (glossBuilding) {
            return;
        }
        glossBuilding = true;
        glossCache.clear();
        final List<Phrase> needTranslation = new ArrayList<>();
        int fromDict = 0;
        for (Phrase p : phrases) {
            String dict = userDictionary.get(dictKey(p.polish));
            if (dict != null && !dict.isEmpty()) {
                glossCache.put(p.key(), dict);
                fromDict++;
            } else if (p.english == null || p.english.trim().isEmpty()) {
                needTranslation.add(p);
            } else {
                glossCache.put(p.key(), p.english);
            }
        }
        glossStatus = t("Matched ", "Dopasowano ") + fromDict + t(" from dictionary. Translating ", " ze słownika. Tłumaczę ")
                + needTranslation.size() + "…";
        render();
        translateGlossAt(needTranslation, 0);
    }

    private void translateGlossAt(final List<Phrase> todo, final int index) {
        if (index >= todo.size()) {
            finishGlossBuild();
            return;
        }
        final Phrase p = todo.get(index);
        final Translator tr = translatorFor(false); // Polish -> English
        tr.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(ignored -> tr.translate(p.polish)
                        .addOnSuccessListener(result -> {
                            glossCache.put(p.key(), result.trim());
                            if (index % 10 == 0) {
                                glossStatus = t("Translating ", "Tłumaczę ") + (index + 1) + "/" + todo.size() + "…";
                                if (SCREEN_TRANSLATE.equals(screen)) {
                                    render();
                                }
                            }
                            translateGlossAt(todo, index + 1);
                        })
                        .addOnFailureListener(e -> translateGlossAt(todo, index + 1)))
                .addOnFailureListener(e -> {
                    glossBuilding = false;
                    glossStatus = t("Could not download the language pack. Connect to the internet and try again.",
                            "Nie udało się pobrać pakietu językowego. Połącz się z internetem i spróbuj ponownie.");
                    if (SCREEN_TRANSLATE.equals(screen)) {
                        render();
                    }
                });
    }

    private void finishGlossBuild() {
        try {
            JSONObject out = new JSONObject();
            for (Map.Entry<String, String> e : glossCache.entrySet()) {
                out.put(e.getKey(), e.getValue());
            }
            try (FileOutputStream fos = openFileOutput(GLOSS_FILE, MODE_PRIVATE)) {
                fos.write(out.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
        glossBuilding = false;
        glossStatus = t("Ready: ", "Gotowe: ") + glossCache.size() + t(" translations built and saved offline.", " tłumaczeń zbudowanych i zapisanych offline.");
        render();
    }

    private void pickDictionaryFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_OPEN_DICTIONARY);
        } catch (Exception e) {
            Toast.makeText(this, t("No app to pick files.", "Brak aplikacji do wyboru plików."), Toast.LENGTH_SHORT).show();
        }
    }

    // ---- Immersive listening: Polish ×2, English ×1, 1s gap, then next card ----

    private void buildListenDeck() {
        listenDeck.clear();
        for (Phrase phrase : phrases) {
            if (!level.equals(phrase.level)) {
                continue;
            }
            if (!"All".equals(listenTopic) && !listenTopic.equals(phrase.category)) {
                continue;
            }
            listenDeck.add(phrase);
        }
        Collections.shuffle(listenDeck);
        listenIndex = 0;
    }

    private void startListening() {
        if (listenDeck.isEmpty()) {
            buildListenDeck();
        }
        if (listenDeck.isEmpty()) {
            Toast.makeText(this, t("No cards for this topic yet.", "Brak kart dla tego tematu."), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ttsReady) {
            Toast.makeText(this, t("Speech engine is still starting.", "Silnik mowy jeszcze się uruchamia."), Toast.LENGTH_SHORT).show();
            return;
        }
        listenPlaying = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        attachListenProgressListener();
        render();
        playListenStep(0);
    }

    // Silences whichever auto-playback is running (listening loop or dialog).
    private void stopAudioPlayback() {
        listenPlaying = false;
        dialogPlayIndex = -1;
        listenHandler.removeCallbacksAndMessages(null);
        if (textToSpeech != null) {
            try {
                textToSpeech.stop();
            } catch (Exception ignored) {
            }
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void stopListening() {
        listenPlaying = false;
        listenHandler.removeCallbacksAndMessages(null);
        if (textToSpeech != null) {
            try {
                textToSpeech.stop();
            } catch (Exception ignored) {
            }
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        render();
    }

    private void attachListenProgressListener() {
        if (textToSpeech == null) {
            return;
        }
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }

            @Override
            public void onDone(String utteranceId) {
                if (utteranceId == null || !utteranceId.startsWith("listen:")) {
                    return;
                }
                final String[] parts = utteranceId.split(":");
                if (parts.length < 3) {
                    return;
                }
                final int card = Integer.parseInt(parts[1]);
                final int step = Integer.parseInt(parts[2]);
                listenHandler.post(() -> advanceListening(card, step));
            }

            @Override public void onError(String utteranceId) {
                listenHandler.post(() -> {
                    if (listenPlaying) {
                        stopListening();
                    }
                });
            }
        });
    }

    private void advanceListening(int card, int step) {
        if (!listenPlaying || card != listenIndex) {
            return;
        }
        if (step < 2) {
            playListenStep(step + 1);
            return;
        }
        // Finished English: pause, then move to the next card.
        listenHandler.postDelayed(() -> {
            if (!listenPlaying) {
                return;
            }
            listenIndex++;
            if (listenIndex >= listenDeck.size()) {
                listenIndex = 0; // loop the topic
            }
            render();
            playListenStep(0);
        }, LISTEN_GAP_MS);
    }

    // step 0,1 = Polish; step 2 = English
    private void playListenStep(int step) {
        if (!listenPlaying || listenIndex >= listenDeck.size() || textToSpeech == null) {
            return;
        }
        Phrase card = listenDeck.get(listenIndex);
        boolean polishStep = step < 2;
        Locale locale = polishStep ? new Locale("pl", "PL") : Locale.US;
        String text = polishStep ? card.polish : glossFor(card);
        if (text == null || text.trim().isEmpty()) {
            advanceListening(listenIndex, step);
            return;
        }
        if (!applySavedVoice(locale)) {
            textToSpeech.setLanguage(locale);
        }
        applySpeechRate();
        String id = "listen:" + listenIndex + ":" + step;
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
    }

    private void renderListen(LinearLayout content) {
        Theme th = theme();
        content.addView(screenTitle(t("Immersive Listening", "Słuchanie")));
        addGap(content, 8);
        content.addView(bodyText(t("Each word is read twice in Polish, then once in English, with a short pause. It keeps going hands-free. Tap any Polish word to look it up — playback pauses while you do.",
                "Każde słowo czytane jest dwa razy po polsku, potem raz po angielsku, z krótką przerwą. Działa bez dotykania telefonu. Dotknij dowolnego polskiego słowa, aby je sprawdzić — odtwarzanie wtedy się zatrzyma."), 13, th.muted));
        addGap(content, 14);

        // Topic chips
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = row();
        scroll.addView(chipRow);
        List<String> topics = new ArrayList<>();
        topics.add("All");
        for (TopicCount tc : allTopicsForLevel()) {
            topics.add(tc.name);
        }
        for (String topic : topics) {
            boolean selected = topic.equals(listenTopic);
            Button chip = flatButton(topic, selected ? th.ink : th.panel, selected ? th.bg : th.muted, selected ? th.ink : th.dash, 12, 32);
            chip.setAllCaps(true);
            chip.setOnClickListener(v -> {
                boolean wasPlaying = listenPlaying;
                stopListening();
                listenTopic = topic;
                buildListenDeck();
                if (wasPlaying) {
                    startListening();
                } else {
                    render();
                }
            });
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
            cp.setMargins(0, 0, dp(8), 0);
            chipRow.addView(chip, cp);
        }
        content.addView(scroll);
        addGap(content, 16);

        if (listenDeck.isEmpty()) {
            buildListenDeck();
        }

        LinearLayout card = vertical();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(22), dp(28), dp(22), dp(28));
        card.setBackground(rounded(th.panel, th.ink, th.radius, th.border));
        if (!listenDeck.isEmpty()) {
            Phrase now = listenDeck.get(Math.min(listenIndex, listenDeck.size() - 1));
            card.addView(label(now.level + " · " + now.category, th.accent2, 10.5f, 0.12f));
            addGap(card, 12);
            TextView pl = serifText(now.polish, 30, th.ink);
            pl.setGravity(Gravity.CENTER);
            pl.setLineSpacing(0, 1.03f);
            makeWordsTappable(pl, now.polish);
            card.addView(pl, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            if (listenShowEnglish) {
                addGap(card, 12);
                TextView en = uiText(glossFor(now), 17, th.body, sansMedium);
                en.setGravity(Gravity.CENTER);
                en.setLineSpacing(0, 1.08f);
                card.addView(en, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                if (!now.examplePolish.isEmpty()) {
                    addGap(card, 12);
                    TextView ex = uiText(now.examplePolish, 13, th.faint, sansRegular);
                    ex.setGravity(Gravity.CENTER);
                    makeWordsTappable(ex, now.examplePolish);
                    card.addView(ex, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    if (!now.exampleEnglish.isEmpty()) {
                        TextView exEn = uiText(now.exampleEnglish, 13, th.faint, sansRegular);
                        exEn.setGravity(Gravity.CENTER);
                        card.addView(exEn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    }
                }
            }
            addGap(card, 14);
            card.addView(uiText((listenIndex + 1) + " / " + listenDeck.size(), 11.5f, th.ghost, sansBold));
        }
        content.addView(card);
        addGap(content, 14);

        LinearLayout controls = row();
        Button prev = flatButton("‹", th.panel, th.ink, th.dash, 18, 52);
        prev.setOnClickListener(v -> {
            listenIndex = listenIndex > 0 ? listenIndex - 1 : Math.max(0, listenDeck.size() - 1);
            render();
            if (listenPlaying) {
                playListenStep(0);
            }
        });
        controls.addView(prev, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button toggle = filledButton(listenPlaying ? t("Pause", "Pauza") : t("Play", "Odtwórz"), th.accent, th.onAccent, 15, 52);
        toggle.setOnClickListener(v -> {
            if (listenPlaying) {
                stopListening();
            } else {
                startListening();
            }
        });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(52), 2);
        tp.setMargins(dp(10), 0, dp(10), 0);
        controls.addView(toggle, tp);
        Button next = flatButton("›", th.panel, th.ink, th.dash, 18, 52);
        next.setOnClickListener(v -> {
            listenIndex = (listenIndex + 1) % Math.max(1, listenDeck.size());
            render();
            if (listenPlaying) {
                playListenStep(0);
            }
        });
        controls.addView(next, new LinearLayout.LayoutParams(0, dp(52), 1));
        content.addView(controls);
        addGap(content, 10);

        Button eng = flatButton(listenShowEnglish ? t("Hide English", "Ukryj angielski") : t("Show English", "Pokaż angielski"), th.panel, th.muted, th.dash, 13, 44);
        eng.setOnClickListener(v -> {
            listenShowEnglish = !listenShowEnglish;
            render();
        });
        content.addView(eng, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
    }

    // ---- Scenario conversations ----

    private void renderDialogs(LinearLayout content) {
        Theme th = theme();
        Dialog open = openDialogId == null ? null : dialogById(openDialogId);
        if (open != null) {
            renderDialogDetail(content, open);
            return;
        }
        content.addView(screenTitle(t("Conversations", "Rozmowy")));
        addGap(content, 8);
        content.addView(bodyText(t("Real-life scenarios, line by line. Tap any line to hear it, or play the whole conversation.",
                "Scenariusze z życia, linijka po linijce. Dotknij linii, aby ją usłyszeć, lub odtwórz całą rozmowę."), 13, th.muted));
        addGap(content, 14);

        for (Dialog d : dialogs) {
            LinearLayout item = vertical();
            item.setPadding(dp(14), dp(12), dp(14), dp(12));
            item.setBackground(rounded(th.panel, d.custom ? th.accent2 : th.ink, th.radius, th.border));
            LinearLayout head = row();
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(label(d.level + (d.scenario.isEmpty() ? "" : " · " + d.scenario), th.accent2, 10.5f, 0.1f),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            head.addView(uiText(d.lines.size() + t(" lines", " linii"), 10.5f, th.ghost, sansBold));
            item.addView(head);
            addGap(item, 6);
            item.addView(serifText(d.title, 19, th.ink));
            if (!d.titlePolish.isEmpty()) {
                item.addView(uiText(d.titlePolish, 13, th.faint, sansRegular));
            }
            if (!d.description.isEmpty()) {
                addGap(item, 4);
                item.addView(uiText(d.description, 12.5f, th.muted, sansRegular));
            }
            item.setOnClickListener(v -> {
                openDialogId = d.id;
                dialogPlayIndex = -1;
                render();
            });
            content.addView(item);
            addGap(content, 10);
        }

        addGap(content, 8);
        content.addView(new DashedLine(this, th.dash), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));
        addGap(content, 14);
        content.addView(label(t("YOUR OWN CONVERSATIONS", "TWOJE ROZMOWY"), th.accent, 11, 0.14f));
        addGap(content, 8);
        content.addView(bodyText(t("Write a dialog as a JSON file and upload it. Download the template to see the format.",
                "Zapisz rozmowę jako plik JSON i prześlij. Pobierz szablon, aby zobaczyć format."), 13, th.muted));
        addGap(content, 10);
        LinearLayout fileRow = row();
        Button tmpl = flatButton(t("Dialog template", "Szablon rozmowy"), th.panel, th.ink, th.ink, 13, 46);
        tmpl.setOnClickListener(v -> downloadDialogTemplate());
        fileRow.addView(tmpl, new LinearLayout.LayoutParams(0, dp(46), 1));
        Button up = filledButton(t("Upload dialog", "Prześlij rozmowę"), th.accent, th.onAccent, 13, 46);
        up.setOnClickListener(v -> pickDialogFile());
        LinearLayout.LayoutParams upP = new LinearLayout.LayoutParams(0, dp(46), 1);
        upP.setMargins(dp(10), 0, 0, 0);
        fileRow.addView(up, upP);
        content.addView(fileRow);
    }

    private void renderDialogDetail(LinearLayout content, Dialog d) {
        Theme th = theme();
        LinearLayout head = row();
        head.setGravity(Gravity.CENTER_VERTICAL);
        Button back = flatButton("‹ " + t("All", "Wszystkie"), th.panel, th.ink, th.dash, 12.5f, 38);
        back.setOnClickListener(v -> {
            stopDialogPlayback();
            openDialogId = null;
            render();
        });
        head.addView(back, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)));
        content.addView(head);
        addGap(content, 10);
        content.addView(serifText(d.title, 24, th.ink));
        if (!d.titlePolish.isEmpty()) {
            content.addView(uiText(d.titlePolish, 14, th.faint, sansRegular));
        }
        addGap(content, 12);

        LinearLayout controls = row();
        Button play = filledButton(dialogPlayIndex >= 0 ? t("Stop", "Stop") : t("Play conversation", "Odtwórz rozmowę"), th.accent, th.onAccent, 14, 48);
        play.setOnClickListener(v -> {
            if (dialogPlayIndex >= 0) {
                stopDialogPlayback();
            } else {
                startDialogPlayback(d, 0);
            }
        });
        controls.addView(play, new LinearLayout.LayoutParams(0, dp(48), 2));
        Button toggleEn = flatButton(dialogShowEnglish ? t("Hide EN", "Ukryj EN") : t("Show EN", "Pokaż EN"), th.panel, th.muted, th.dash, 13, 48);
        toggleEn.setOnClickListener(v -> {
            dialogShowEnglish = !dialogShowEnglish;
            render();
        });
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(48), 1);
        tp.setMargins(dp(10), 0, 0, 0);
        controls.addView(toggleEn, tp);
        content.addView(controls);
        addGap(content, 14);

        for (int i = 0; i < d.lines.size(); i++) {
            final DialogLine line = d.lines.get(i);
            final int index = i;
            boolean left = "A".equalsIgnoreCase(line.speaker);
            boolean active = index == dialogPlayIndex;
            LinearLayout bubble = vertical();
            bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
            bubble.setBackground(rounded(active ? th.accentSoft : th.panel, active ? th.accent : (left ? th.ink : th.dash), th.radius, th.border));
            bubble.addView(label(d.roleLabel(line.speaker), left ? th.accent : th.accent2, 10, 0.08f));
            addGap(bubble, 4);
            TextView pl = serifText(line.polish, 17, th.ink);
            pl.setLineSpacing(0, 1.05f);
            makeWordsTappable(pl, line.polish);
            bubble.addView(pl);
            if (dialogShowEnglish && !line.english.isEmpty()) {
                TextView en = uiText(line.english, 12.5f, th.faint, sansRegular);
                en.setLineSpacing(0, 1.05f);
                bubble.addView(en, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 3));
            }
            if (!line.note.isEmpty()) {
                TextView note = uiText("• " + line.note, 11.5f, th.muted, sansRegular);
                bubble.addView(note, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 4));
            }
            bubble.setOnClickListener(v -> speak(line.polish, new Locale("pl", "PL")));
            LinearLayout wrap = row();
            if (!left) {
                SpaceView spacer = new SpaceView(this);
                wrap.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
            }
            wrap.addView(bubble, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 6));
            if (left) {
                SpaceView spacer = new SpaceView(this);
                wrap.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
            }
            content.addView(wrap);
            addGap(content, 8);
        }

        if (d.custom) {
            addGap(content, 8);
            Button del = flatButton(t("Delete this conversation", "Usuń tę rozmowę"), th.panel, th.accent, th.accent, 13, 44);
            del.setOnClickListener(v -> deleteCustomDialog(d));
            content.addView(del, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
        }
    }

    /** Makes every word in a Polish sentence individually tappable. */
    private void makeWordsTappable(TextView view, String sentence) {
        final Theme th = theme();
        SpannableString span = new SpannableString(sentence);
        Matcher m = WORD_PATTERN.matcher(sentence);
        boolean any = false;
        while (m.find()) {
            final String word = m.group();
            span.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    showWordLookup(word);
                }

                @Override
                public void updateDrawState(android.text.TextPaint ds) {
                    ds.setColor(th.ink);      // keep the editorial look, no blue links
                    ds.setUnderlineText(false);
                }
            }, m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            any = true;
        }
        if (any) {
            view.setText(span);
            view.setMovementMethod(LinkMovementMethod.getInstance());
            view.setHighlightColor(th.accentSoft);
        }
    }

    /** Word sheet: translation, audio, and one tap into a list or favourites. */
    private void showWordLookup(final String rawWord) {
        final String word = rawWord.trim();
        if (word.isEmpty()) {
            return;
        }
        // The listening loop and the sheet's audio would otherwise talk over
        // each other, and flushing the loop's utterance can stall its chain.
        if (listenPlaying) {
            stopListening();
        }
        String gloss = lookupWord(word);
        if (gloss != null) {
            presentWordSheet(word, gloss, t("built-in dictionary", "słownik wbudowany"));
            return;
        }
        // Inflected form: translate it properly, and offer the dictionary's
        // base form as a hint rather than guessing at the lemma.
        final String baseHint = lookupBaseForm(word);
        final Translator tr = translatorFor(false);
        tr.downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(ignored -> tr.translate(word)
                        .addOnSuccessListener(result -> {
                            String text = result.trim();
                            if (baseHint != null) {
                                text = text + "\n\n" + t("Dictionary form: ", "Forma słownikowa: ") + baseHint;
                            }
                            presentWordSheet(word, text, t("on-device translation", "tłumaczenie na urządzeniu"));
                        })
                        .addOnFailureListener(e -> presentWordSheet(word,
                                baseHint == null ? "" : t("Dictionary form: ", "Forma słownikowa: ") + baseHint,
                                t("dictionary only", "tylko słownik"))))
                .addOnFailureListener(e -> presentWordSheet(word,
                        baseHint == null ? "" : t("Dictionary form: ", "Forma słownikowa: ") + baseHint,
                        t("offline dictionary", "słownik offline")));
    }

    private void presentWordSheet(final String word, final String gloss, String sourceLabel) {
        Theme th = theme();
        LinearLayout box = vertical();
        box.setPadding(dp(22), dp(18), dp(22), dp(8));

        TextView head = serifText(word, 26, th.ink);
        head.setTextIsSelectable(true);
        box.addView(head);
        box.addView(label(sourceLabel.toUpperCase(Locale.ROOT), th.ghost, 10, 0.1f),
                topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 4));
        TextView body = bodyText(gloss.isEmpty() ? t("No translation available.", "Brak tłumaczenia.") : gloss, 15, th.body);
        box.addView(body, topMarginParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 10));

        final AlertDialog dialog = new AlertDialog.Builder(this).setView(box).create();

        LinearLayout actions = row();
        Button speak = flatButton("🔊 " + t("Play", "Odtwórz"), th.accentSoft, th.accent, th.accent, 13, 44);
        speak.setOnClickListener(v -> speak(word, new Locale("pl", "PL")));
        actions.addView(speak, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button fav = flatButton("★ " + t("Favourite", "Ulubione"), th.panel, th.ink, th.dash, 13, 44);
        fav.setOnClickListener(v -> {
            addWordToCollection(word, gloss, null, true);
            dialog.dismiss();
        });
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, dp(44), 1);
        fp.setMargins(dp(8), 0, 0, 0);
        actions.addView(fav, fp);
        box.addView(actions, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44), 16));

        Button addList = filledButton(t("Add to a list", "Dodaj do listy"), th.accent, th.onAccent, 14, 46);
        addList.setOnClickListener(v -> {
            dialog.dismiss();
            promptForTag(lastTag(), tag -> addWordToCollection(word, gloss, tag, false));
        });
        box.addView(addList, topMarginParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46), 10));

        dialog.show();
    }

    /**
     * Saves a looked-up word as a study card, optionally starring it. Words
     * already in the deck are reused rather than duplicated.
     */
    private void addWordToCollection(String word, String gloss, String tag, boolean favourite) {
        String english = gloss == null ? "" : gloss.trim();
        if (english.isEmpty()) {
            english = word; // keep the card valid; the user can edit the list later
        }
        String key = dictKey(word);
        Phrase existing = null;
        for (Phrase phrase : phrases) {
            if (dictKey(phrase.polish).equals(key)) {
                existing = phrase;
                break;
            }
        }
        if (existing == null) {
            boolean saved = saveCustomCard(word, english, level, tag == null ? lastTag() : tag);
            loadPhrases();
            loadMemory();
            if (saved) {
                for (Phrase phrase : phrases) {
                    if (dictKey(phrase.polish).equals(key)) {
                        existing = phrase;
                        break;
                    }
                }
            }
        }
        if (favourite && existing != null && !isFavourite(existing)) {
            toggleFavourite(existing);
        }
        String where = favourite ? t("Favourites", "Ulubione") : (tag == null ? lastTag() : tag);
        Toast.makeText(this, "„" + word + "” → " + where, Toast.LENGTH_SHORT).show();
        render();
    }

    private void startDialogPlayback(Dialog d, int from) {
        if (!ttsReady) {
            Toast.makeText(this, t("Speech engine is still starting.", "Silnik mowy jeszcze się uruchamia."), Toast.LENGTH_SHORT).show();
            return;
        }
        openDialogId = d.id;
        dialogPlayIndex = from;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        attachDialogProgressListener(d);
        render();
        speakDialogLine(d, from);
    }

    private void stopDialogPlayback() {
        dialogPlayIndex = -1;
        listenHandler.removeCallbacksAndMessages(null);
        if (textToSpeech != null) {
            try {
                textToSpeech.stop();
            } catch (Exception ignored) {
            }
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        render();
    }

    private void speakDialogLine(Dialog d, int index) {
        if (index < 0 || index >= d.lines.size() || textToSpeech == null) {
            return;
        }
        DialogLine line = d.lines.get(index);
        Locale pl = new Locale("pl", "PL");
        if (!applySavedVoice(pl)) {
            textToSpeech.setLanguage(pl);
        }
        applySpeechRate();
        textToSpeech.speak(line.polish, TextToSpeech.QUEUE_FLUSH, null, "dialog:" + index);
    }

    private void attachDialogProgressListener(final Dialog d) {
        if (textToSpeech == null) {
            return;
        }
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { }

            @Override
            public void onDone(String utteranceId) {
                if (utteranceId == null || !utteranceId.startsWith("dialog:")) {
                    return;
                }
                final int done = Integer.parseInt(utteranceId.substring(7));
                listenHandler.post(() -> {
                    if (dialogPlayIndex != done) {
                        return;
                    }
                    listenHandler.postDelayed(() -> {
                        if (dialogPlayIndex != done) {
                            return;
                        }
                        int next = done + 1;
                        if (next >= d.lines.size()) {
                            stopDialogPlayback();
                            return;
                        }
                        dialogPlayIndex = next;
                        render();
                        speakDialogLine(d, next);
                    }, LISTEN_GAP_MS);
                });
            }

            @Override public void onError(String utteranceId) {
                listenHandler.post(() -> stopDialogPlayback());
            }
        });
    }

    private void deleteCustomDialog(Dialog d) {
        String rawId = d.id.startsWith("my:") ? d.id.substring(3) : d.id;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(prefs.getString(CUSTOM_DIALOGS, "[]"));
            JSONArray keep = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (!rawId.equals(o.optString("id"))) {
                    keep.put(o);
                }
            }
            prefs.edit().putString(CUSTOM_DIALOGS, keep.toString()).apply();
        } catch (Exception ignored) {
        }
        openDialogId = null;
        loadDialogs();
        Toast.makeText(this, t("Conversation deleted.", "Rozmowa usunięta."), Toast.LENGTH_SHORT).show();
        render();
    }

    private void downloadDialogTemplate() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "dialog_template.json");
        try {
            startActivityForResult(intent, REQ_SAVE_DIALOG_TEMPLATE);
        } catch (Exception e) {
            Toast.makeText(this, t("No app to save files.", "Brak aplikacji do zapisu plików."), Toast.LENGTH_SHORT).show();
        }
    }

    private void pickDialogFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQ_OPEN_DIALOG);
        } catch (Exception e) {
            Toast.makeText(this, t("No app to pick files.", "Brak aplikacji do wyboru plików."), Toast.LENGTH_SHORT).show();
        }
    }

    private void importDialogFile(Uri uri) {
        try {
            StringBuilder sb = new StringBuilder();
            for (String line : readLines(getContentResolver().openInputStream(uri))) {
                sb.append(line).append('\n');
            }
            String json = sb.toString().trim();
            // Validate before storing.
            int before = dialogs.size();
            parseDialogsInto(json, true);
            int parsed = dialogs.size() - before;
            if (parsed <= 0) {
                Toast.makeText(this, t("No conversations found in that file.", "Nie znaleziono rozmów w tym pliku."), Toast.LENGTH_LONG).show();
                loadDialogs();
                return;
            }
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            JSONArray stored = new JSONArray(prefs.getString(CUSTOM_DIALOGS, "[]"));
            String trimmed = json.startsWith("{") ? "[" + json + "]" : json;
            JSONArray incoming = new JSONArray(trimmed);
            for (int i = 0; i < incoming.length(); i++) {
                stored.put(incoming.getJSONObject(i));
            }
            prefs.edit().putString(CUSTOM_DIALOGS, stored.toString()).apply();
            loadDialogs();
            screen = SCREEN_DIALOGS;
            openDialogId = null;
            Toast.makeText(this, t("Added ", "Dodano ") + parsed + t(" conversation(s).", " rozmów."), Toast.LENGTH_LONG).show();
            render();
        } catch (Exception e) {
            Toast.makeText(this, t("Could not read that dialog file. Check the JSON format.",
                    "Nie udało się odczytać pliku. Sprawdź format JSON."), Toast.LENGTH_LONG).show();
        }
    }

    private String currentEngineLabel() {
        String engine = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_TTS_ENGINE, "");
        if (!ttsReady) {
            return t("starting…", "uruchamianie…");
        }
        try {
            if (engine.isEmpty()) {
                String def = textToSpeech.getDefaultEngine();
                for (TextToSpeech.EngineInfo info : textToSpeech.getEngines()) {
                    if (info.name.equals(def)) {
                        return info.label + " " + t("(system default)", "(domyślny systemu)");
                    }
                }
                return t("System default", "Domyślny systemu");
            }
            for (TextToSpeech.EngineInfo info : textToSpeech.getEngines()) {
                if (info.name.equals(engine)) {
                    return info.label;
                }
            }
        } catch (Exception ignored) {
        }
        return engine.isEmpty() ? t("System default", "Domyślny systemu") : engine;
    }

    private String voicePrefKey(Locale locale) {
        return "pl".equals(locale.getLanguage()) ? PREF_VOICE_PL : PREF_VOICE_EN;
    }

    // All voices the current engine offers for this language.
    private List<Voice> voicesFor(Locale locale) {
        List<Voice> matches = new ArrayList<>();
        if (textToSpeech == null || !ttsReady) {
            return matches;
        }
        try {
            java.util.Set<Voice> all = textToSpeech.getVoices();
            if (all == null) {
                return matches;
            }
            for (Voice v : all) {
                if (v == null || v.getLocale() == null) {
                    continue;
                }
                if (locale.getLanguage().equalsIgnoreCase(v.getLocale().getLanguage())) {
                    matches.add(v);
                }
            }
        } catch (Exception ignored) {
        }
        Collections.sort(matches, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return matches;
    }

    private String voiceLabel(Voice v) {
        String label = v.getName();
        if (v.isNetworkConnectionRequired()) {
            label += "  · " + t("online", "online");
        }
        return label;
    }

    private String selectedVoiceName(Locale locale) {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString(voicePrefKey(locale), "");
    }

    // Returns true when a specific saved voice was applied.
    private boolean applySavedVoice(Locale locale) {
        String saved = selectedVoiceName(locale);
        if (saved.isEmpty() || textToSpeech == null) {
            return false;
        }
        try {
            for (Voice v : voicesFor(locale)) {
                if (saved.equals(v.getName())) {
                    return textToSpeech.setVoice(v) == TextToSpeech.SUCCESS;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void chooseTtsEngine() {
        if (textToSpeech == null) {
            return;
        }
        final List<String> packages = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        packages.add("");
        labels.add(t("System default", "Domyślny systemu"));
        try {
            for (TextToSpeech.EngineInfo info : textToSpeech.getEngines()) {
                packages.add(info.name);
                labels.add(info.label);
            }
        } catch (Exception ignored) {
        }
        String current = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_TTS_ENGINE, "");
        int checked = Math.max(0, packages.indexOf(current));
        new AlertDialog.Builder(this)
                .setTitle(t("Speech engine", "Silnik mowy"))
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (d, which) -> {
                    d.dismiss();
                    setTtsEngine(packages.get(which));
                })
                .setNegativeButton(t("Cancel", "Anuluj"), null)
                .show();
    }

    private void chooseVoice(Locale locale) {
        final List<Voice> voices = voicesFor(locale);
        if (voices.isEmpty()) {
            Toast.makeText(this, t("This engine offers no voices for that language yet.",
                    "Ten silnik nie ma jeszcze głosów dla tego języka."), Toast.LENGTH_LONG).show();
            return;
        }
        final List<String> labels = new ArrayList<>();
        labels.add(t("Engine default", "Domyślny silnika"));
        for (Voice v : voices) {
            labels.add(voiceLabel(v));
        }
        String saved = selectedVoiceName(locale);
        int checked = 0;
        for (int i = 0; i < voices.size(); i++) {
            if (voices.get(i).getName().equals(saved)) {
                checked = i + 1;
                break;
            }
        }
        final boolean polish = "pl".equals(locale.getLanguage());
        new AlertDialog.Builder(this)
                .setTitle(polish ? t("Polish voice", "Głos polski") : t("English voice", "Głos angielski"))
                .setSingleChoiceItems(labels.toArray(new String[0]), checked, (d, which) -> {
                    d.dismiss();
                    saveSetting(voicePrefKey(locale), which == 0 ? "" : voices.get(which - 1).getName());
                    render();
                    // Play a sample so the choice is audible immediately.
                    speak(polish ? "Dzień dobry. Uczę się polskiego." : "Good morning. I am learning Polish.", locale);
                })
                .setNegativeButton(t("Cancel", "Anuluj"), null)
                .show();
    }

    private boolean voiceAvailable(Locale locale) {
        if (textToSpeech == null || !ttsReady) {
            return false;
        }
        try {
            return textToSpeech.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE;
        } catch (Exception e) {
            return false;
        }
    }

    private void promptInstallVoice(Locale locale) {
        String lang = "pl".equals(locale.getLanguage()) ? t("Polish", "polski") : t("English", "angielski");
        new AlertDialog.Builder(this)
                .setTitle(t("Voice not installed", "Głos nie jest zainstalowany"))
                .setMessage(t("The ", "Głos ") + lang
                        + t(" voice for offline reading isn't installed on this device. You can add it for free from your device's text-to-speech settings, then it works without internet.",
                            " do czytania offline nie jest zainstalowany na tym urządzeniu. Możesz go dodać za darmo w ustawieniach syntezatora mowy — potem działa bez internetu."))
                .setPositiveButton(t("Install voice", "Zainstaluj głos"), (d, w) -> installVoiceData())
                .setNeutralButton(t("TTS settings", "Ustawienia mowy"), (d, w) -> openTtsSettings())
                .setNegativeButton(t("Close", "Zamknij"), null)
                .show();
    }

    private void installVoiceData() {
        try {
            startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
        } catch (Exception e) {
            openTtsSettings();
        }
    }

    private void openTtsSettings() {
        try {
            Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
                Toast.makeText(this, t("Open your device's text-to-speech settings to install a voice.",
                        "Otwórz ustawienia syntezatora mowy, aby zainstalować głos."), Toast.LENGTH_LONG).show();
            }
        }
    }

    private float speechRate() {
        if (SPEED_SLOWEST.equals(speechSpeed)) {
            return 0.5f;
        }
        if (SPEED_SLOW.equals(speechSpeed)) {
            return 0.6f;
        }
        if (SPEED_FAST.equals(speechSpeed)) {
            return 1.45f;
        }
        if (SPEED_FASTEST.equals(speechSpeed)) {
            return 1.5f;
        }
        return 1.0f;
    }

    private void loadPhrases() {
        try {
            parsePhrases(readPhraseJson());
            appendCustomCards();
        } catch (Exception error) {
            dataError = "Could not load phrase data.";
        }
    }

    // User-uploaded words, stored locally, added to the deck as "My Words" cards.
    private void appendCustomCards() {
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(CUSTOM_CARDS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Phrase p = new Phrase(
                        MY_WORDS_CATEGORY,
                        o.optString("polish"),
                        o.optString("english"),
                        "", "", "",
                        o.optString("notes", ""),
                        o.optString("level", "A1"),
                        0);
                p.tag = o.optString("tag", MY_WORDS_CATEGORY);
                phrases.add(p);
            }
        } catch (Exception ignored) {
        }
    }

    private String lastTag() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString("lastTag", MY_WORDS_CATEGORY);
    }

    private void loadFavourites() {
        favourites.clear();
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(FAVOURITES, "[]");
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                favourites.add(a.getString(i));
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isFavourite(Phrase phrase) {
        return favourites.contains(phrase.key());
    }

    private void toggleFavourite(Phrase phrase) {
        String k = phrase.key();
        if (!favourites.remove(k)) {
            favourites.add(k);
        }
        JSONArray a = new JSONArray();
        for (String s : favourites) {
            a.put(s);
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(FAVOURITES, a.toString()).apply();
    }

    private int favouriteCount() {
        int count = 0;
        for (Phrase phrase : phrases) {
            if (favourites.contains(phrase.key())) {
                count++;
            }
        }
        return count;
    }

    private void startFavouritesSession() {
        List<Phrase> pool = new ArrayList<>();
        for (Phrase phrase : phrases) {
            if (favourites.contains(phrase.key())) {
                pool.add(phrase);
            }
        }
        Collections.shuffle(pool);
        beginSession(pool, 0);
    }

    // Ordered map of list name -> card count, over user-added cards.
    private Map<String, Integer> customTagCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Phrase phrase : phrases) {
            if (MY_WORDS_CATEGORY.equals(phrase.category)) {
                String tag = phrase.tag.isEmpty() ? MY_WORDS_CATEGORY : phrase.tag;
                Integer c = counts.get(tag);
                counts.put(tag, c == null ? 1 : c + 1);
            }
        }
        return counts;
    }

    private void startTagSession(String tag) {
        List<Phrase> pool = new ArrayList<>();
        for (Phrase phrase : phrases) {
            if (MY_WORDS_CATEGORY.equals(phrase.category)
                    && (phrase.tag.isEmpty() ? MY_WORDS_CATEGORY : phrase.tag).equals(tag)) {
                pool.add(phrase);
            }
        }
        Collections.shuffle(pool);
        beginSession(pool, 0);
    }

    interface TagCallback {
        void onTag(String tag);
    }

    private void promptForTag(String defaultTag, TagCallback callback) {
        final EditText field = new EditText(this);
        field.setText(defaultTag);
        field.setHint(t("List name, e.g. ZUS", "Nazwa listy, np. ZUS"));
        field.setSingleLine(true);
        field.setSelection(field.getText().length());
        int pad = dp(20);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(pad, dp(8), pad, 0);
        wrap.addView(field);
        new AlertDialog.Builder(this)
                .setTitle(t("Add to which list?", "Do której listy dodać?"))
                .setView(wrap)
                .setPositiveButton(t("Add", "Dodaj"), (d, w) -> {
                    String tag = field.getText().toString().trim();
                    if (tag.isEmpty()) {
                        tag = MY_WORDS_CATEGORY;
                    }
                    saveSetting("lastTag", tag);
                    callback.onTag(tag);
                })
                .setNegativeButton(t("Cancel", "Anuluj"), null)
                .show();
    }

    private String normPolish(String value) {
        return LearningLogic.normalizeHeadword(value);
    }

    private int customCardCount() {
        int count = 0;
        for (Phrase phrase : phrases) {
            if (MY_WORDS_CATEGORY.equals(phrase.category)) {
                count++;
            }
        }
        return count;
    }

    // Returns true if newly stored (skips duplicates of any existing card).
    private boolean saveCustomCard(String polish, String english, String lvl, String tag) {
        polish = polish.trim();
        english = english.trim();
        if (polish.isEmpty() || english.isEmpty()) {
            return false;
        }
        String key = normPolish(polish);
        for (Phrase phrase : phrases) {
            if (normPolish(phrase.polish).equals(key)) {
                return false;
            }
        }
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            JSONArray arr = new JSONArray(prefs.getString(CUSTOM_CARDS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                if (normPolish(arr.getJSONObject(i).optString("polish")).equals(key)) {
                    return false;
                }
            }
            JSONObject o = new JSONObject();
            o.put("polish", polish);
            o.put("english", english);
            o.put("level", (lvl == null || lvl.trim().isEmpty()) ? level : lvl.trim());
            o.put("tag", (tag == null || tag.trim().isEmpty()) ? MY_WORDS_CATEGORY : tag.trim());
            arr.put(o);
            prefs.edit().putString(CUSTOM_CARDS, arr.toString()).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String readPhraseJson() throws Exception {
        File remotePhrases = new File(getFilesDir(), REMOTE_PHRASES_FILE);
        if (remotePhrases.exists()) {
            return readStream(openFileInput(REMOTE_PHRASES_FILE));
        }
        return readAsset("phrases.json");
    }

    private void parsePhrases(String phraseJson) throws Exception {
        JSONArray array = new JSONArray(phraseJson);
        phrases.clear();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String phonetic = item.optString("phonetic");
            String examplePolish = item.optString("examplePolish");
            String exampleEnglish = item.optString("exampleEnglish");
            if (examplePolish.isEmpty() && phonetic.contains(" / ")) {
                // Pre-v3 data stored the example sentence in the phonetic field.
                int split = phonetic.indexOf(" / ");
                examplePolish = phonetic.substring(0, split).trim();
                exampleEnglish = phonetic.substring(split + 3).trim();
                phonetic = "";
            }
            Phrase p = new Phrase(
                    item.optString("scenario", item.optString("category", "General Core")),
                    item.getString("polish"),
                    item.getString("english"),
                    phonetic,
                    examplePolish,
                    exampleEnglish,
                    item.optString("notes"),
                    item.optString("level", "A1"),
                    item.optInt("coreIndex", 0)
            );
            p.declension = item.optString("declension");
            phrases.add(p);
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
            dataError = "Could not load grammar lessons.";
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
            dataError = "Could not load alphabet.";
        }
    }

    private void loadDialogs() {
        dialogs.clear();
        try {
            parseDialogsInto(readAsset("dialogs.json"), false);
        } catch (Exception ignored) {
        }
        String custom = getSharedPreferences(PREFS, MODE_PRIVATE).getString(CUSTOM_DIALOGS, "[]");
        try {
            parseDialogsInto(custom, true);
        } catch (Exception ignored) {
        }
    }

    // Accepts either a single dialog object or an array of them.
    private int parseDialogsInto(String json, boolean custom) throws Exception {
        String trimmed = json.trim();
        JSONArray arr;
        if (trimmed.startsWith("{")) {
            arr = new JSONArray();
            arr.put(new JSONObject(trimmed));
        } else {
            arr = new JSONArray(trimmed);
        }
        int added = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            JSONArray lineArr = o.optJSONArray("lines");
            if (lineArr == null || lineArr.length() == 0) {
                continue;
            }
            List<DialogLine> lines = new ArrayList<>();
            for (int j = 0; j < lineArr.length(); j++) {
                JSONObject l = lineArr.getJSONObject(j);
                String pl = l.optString("polish").trim();
                if (pl.isEmpty()) {
                    continue;
                }
                lines.add(new DialogLine(
                        l.optString("speaker", "A").trim(),
                        pl,
                        l.optString("english").trim(),
                        l.optString("note").trim()));
            }
            if (lines.isEmpty()) {
                continue;
            }
            Map<String, String> roles = new LinkedHashMap<>();
            JSONObject r = o.optJSONObject("roles");
            if (r != null) {
                for (java.util.Iterator<String> it = r.keys(); it.hasNext(); ) {
                    String k = it.next();
                    roles.put(k, r.optString(k, k));
                }
            }
            String id = o.optString("id", "").trim();
            if (id.isEmpty()) {
                id = "dialog-" + (dialogs.size() + 1);
            }
            if (custom) {
                id = "my:" + id;
            }
            dialogs.add(new Dialog(
                    id,
                    o.optString("title", id),
                    o.optString("titlePolish", ""),
                    o.optString("level", "A1"),
                    o.optString("scenario", ""),
                    o.optString("description", ""),
                    roles,
                    lines,
                    custom));
            added++;
        }
        return added;
    }

    private Dialog dialogById(String id) {
        for (Dialog d : dialogs) {
            if (d.id.equals(id)) {
                return d;
            }
        }
        return null;
    }

    private String readAsset(String fileName) throws Exception {
        return readStream(getAssets().open(fileName));
    }

    private String readStream(InputStream stream) throws Exception {
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
        }
        return json.toString();
    }

    private void loadFonts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            sansRegular = getResources().getFont(R.font.nunito_regular);
            sansMedium = getResources().getFont(R.font.nunito_bold);
            sansSemiBold = getResources().getFont(R.font.nunito_extrabold);
            sansBold = getResources().getFont(R.font.nunito_black);
            serifBold = getResources().getFont(R.font.baloo2_extrabold);
            displaySemi = getResources().getFont(R.font.baloo2_semibold);
            displayBold = getResources().getFont(R.font.baloo2_bold);
        } else {
            sansRegular = Typeface.create("sans-serif", Typeface.NORMAL);
            sansMedium = Typeface.create("sans-serif", Typeface.BOLD);
            sansSemiBold = Typeface.create("sans-serif", Typeface.BOLD);
            sansBold = Typeface.create("sans-serif", Typeface.BOLD);
            serifBold = Typeface.create("sans-serif", Typeface.BOLD);
            displaySemi = serifBold;
            displayBold = serifBold;
        }
    }

    private Theme theme() {
        Theme theme = themes.get(themeName);
        return theme == null ? themes.get(DEFAULT_THEME) : theme;
    }

    private boolean isDarkTheme() {
        // Derived from the background, so status-bar icon contrast stays
        // correct for any theme added later.
        int bg = theme().bg;
        double luminance = (0.299 * Color.red(bg) + 0.587 * Color.green(bg) + 0.114 * Color.blue(bg)) / 255.0;
        return luminance < 0.5;
    }

    private void buildThemes() {
        // Cartoon theme set. Order here is the order of the masthead swatches.
        // Args: bg, panel, ink, body, muted, faint, ghost, softLine, dash, shadow,
        //       accent, accentAlt, accentSoft, onAccent, accent2, onAccent2,
        //       accent2Text, accent3, radiusDp, borderDp, halftone
        themes.put("Komiks", new Theme(
                "#f4f1ff", "#ffffff", "#17141f", "#3a3547", "#6c5fd4", "#8a86a0", "#b9b5cc",
                "#d9d2f5", "#d9d2f5", "#17141f",
                "#6c4dff", "#ff5c7a", "#e6dfff", "#ffffff",
                "#c8f542", "#17141f", "#5a7a0e", "#25c9d0", 12, 2.5f, true));
        themes.put("Borówka", new Theme(
                "#e4eaff", "#ffffff", "#232d63", "#3d4780", "#7a86c9", "#939ed4", "#a9b2dd",
                "#c3cdfa", "#c3cdfa", "#232d63",
                "#4d6bff", "#ffd94d", "#e4eaff", "#ffffff",
                "#ff7a59", "#ffffff", "#c94f2e", "#22c4a8", 18, 2.5f, false));
        themes.put("Mięta", new Theme(
                "#ddf5e7", "#ffffff", "#1d3f31", "#33574a", "#5f8a75", "#7aa48e", "#93bda8",
                "#b6e3c9", "#b6e3c9", "#1d3f31",
                "#ff5c6c", "#ffab2e", "#ffe1e4", "#ffffff",
                "#17b877", "#ffffff", "#0e7a4e", "#ffd23f", 18, 2.5f, false));
        themes.put("Zachód", new Theme(
                "#ffe6d7", "#ffffff", "#4d2545", "#6b4260", "#a8788f", "#bb92a5", "#cfa8b8",
                "#ffc9a8", "#ffc9a8", "#4d2545",
                "#ff6b35", "#2fbfde", "#ffe6d7", "#ffffff",
                "#9b5cff", "#ffffff", "#7a3fd4", "#2fbfde", 18, 2.5f, false));
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

    /** Card/panel background using the active theme's radius and border. */
    private android.graphics.drawable.Drawable cardBg() {
        Theme th = theme();
        return rounded(th.panel, th.ink, th.radius, th.border);
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
        button.setTypeface(displayBold);
        button.setTextColor(textColor);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(14), 0, dp(14), 0);
        // Cartoon language: pill shape, thick ink outline.
        button.setBackground(rounded(fill, stroke, Math.max(theme().radius, heightDp / 2), theme().border));
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

    private GradientDrawable rounded(int fill, int stroke, float radiusDp, float strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dpFloat(radiusDp));
        drawable.setStroke(Math.max(1, Math.round(dpFloat(strokeDp))), stroke);
        return drawable;
    }

    private ShadowLayout shadowWrap(View child, int offsetDp) {
        return shadowWrap(child, offsetDp, false);
    }

    private ShadowLayout shadowWrap(View child, int offsetDp, boolean fillHeight) {
        ShadowLayout shadow = new ShadowLayout(this, theme().shadow, dp(offsetDp), dp(theme().radius));
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

    private static class DialogLine {
        final String speaker;
        final String polish;
        final String english;
        final String note;

        DialogLine(String speaker, String polish, String english, String note) {
            this.speaker = speaker;
            this.polish = polish;
            this.english = english;
            this.note = note;
        }
    }

    private static class Dialog {
        final String id;
        final String title;
        final String titlePolish;
        final String level;
        final String scenario;
        final String description;
        final Map<String, String> roles;
        final List<DialogLine> lines;
        final boolean custom;

        Dialog(String id, String title, String titlePolish, String level, String scenario,
               String description, Map<String, String> roles, List<DialogLine> lines, boolean custom) {
            this.id = id;
            this.title = title;
            this.titlePolish = titlePolish;
            this.level = level;
            this.scenario = scenario;
            this.description = description;
            this.roles = roles;
            this.lines = lines;
            this.custom = custom;
        }

        String roleLabel(String speaker) {
            String r = roles.get(speaker);
            return r == null ? speaker : r;
        }
    }

    private static class CardMemory {
        final int box;
        final long dueAt;

        CardMemory(int box, long dueAt) {
            this.box = box;
            this.dueAt = dueAt;
        }
    }

    private static class Phrase {
        final String category;
        final String polish;
        final String english;
        final String phonetic;
        final String examplePolish;
        final String exampleEnglish;
        final String notes;
        final String level;
        final int coreIndex;
        String tag = ""; // sub-list name for user-added "My Words" cards
        String declension = ""; // preformatted full case table (nouns)

        Phrase(String category, String polish, String english, String phonetic,
               String examplePolish, String exampleEnglish, String notes,
               String level, int coreIndex) {
            this.category = category;
            this.polish = polish;
            this.english = english;
            this.phonetic = phonetic;
            this.examplePolish = examplePolish;
            this.exampleEnglish = exampleEnglish;
            this.notes = notes;
            this.level = level;
            this.coreIndex = coreIndex;
        }

        String key() {
            return LearningLogic.cardKey(coreIndex, category, polish);
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

    private static class NewsSource {
        final String name;
        final String description;
        final String url;
        final String feedUrl;

        NewsSource(String name, String description, String url, String feedUrl) {
            this.name = name;
            this.description = description;
            this.url = url;
            this.feedUrl = feedUrl;
        }
    }

    private static class NewsItem {
        final String source;
        final String title;
        final String description;
        final String link;
        final String timeLabel;
        final Date publishedAt;
        final List<String> imageUrls;
        final List<Bitmap> images = new ArrayList<>();
        String englishTitle = "";
        String englishDescription = "";
        boolean imagesLoading = false;
        boolean imagesLoaded = false;
        boolean translationQueued = false;
        boolean translationComplete = false;
        boolean translationFailed = false;

        NewsItem(String source, String title, String description, String link, String timeLabel, Date publishedAt, List<String> imageUrls) {
            this.source = source;
            this.title = title;
            this.description = description;
            this.link = link;
            this.timeLabel = timeLabel;
            this.publishedAt = publishedAt;
            this.imageUrls = new ArrayList<>(imageUrls);
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
        final int accent3;      // third accent, used for the 3rd stat / tile
        final int radius;       // card corner radius in dp
        final float border;     // card / button border width in dp
        final boolean halftone; // draw the dotted background texture

        Theme(String bg, String panel, String ink, String body, String muted, String faint, String ghost, String softLine, String dash, String shadow, String accent, String accentAlt, String accentSoft, String onAccent, String accent2, String onAccent2, String accent2Text, String accent3, int radius, float border, boolean halftone) {
            this.accent3 = Color.parseColor(accent3);
            this.radius = radius;
            this.border = border;
            this.halftone = halftone;
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

    /** Halftone dot field used as the Komiks background texture. */
    private static class HalftoneDrawable extends android.graphics.drawable.Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int bg;
        private final float dot;
        private final float step;

        HalftoneDrawable(int bg, int dotColor, float dotRadiusPx, float stepPx) {
            this.bg = bg;
            this.dot = dotRadiusPx;
            this.step = stepPx;
            paint.setColor(dotColor);
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.drawColor(bg);
            android.graphics.Rect b = getBounds();
            int row = 0;
            for (float y = 0; y < b.height() + step; y += step, row++) {
                float offset = (row % 2 == 0) ? 0f : step / 2f;
                for (float x = -step; x < b.width() + step; x += step) {
                    canvas.drawCircle(x + offset, y, dot, paint);
                }
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.OPAQUE; }
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
            } else if ("news".equals(type)) {
                canvas.drawRoundRect(new RectF(w * 0.14f, h * 0.18f, w * 0.86f, h * 0.84f), 3, 3, paint);
                canvas.drawLine(w * 0.26f, h * 0.3f, w * 0.72f, h * 0.3f, paint);
                canvas.drawLine(w * 0.26f, h * 0.43f, w * 0.72f, h * 0.43f, paint);
                canvas.drawLine(w * 0.26f, h * 0.56f, w * 0.48f, h * 0.56f, paint);
                canvas.drawLine(w * 0.58f, h * 0.56f, w * 0.72f, h * 0.56f, paint);
                canvas.drawLine(w * 0.26f, h * 0.69f, w * 0.72f, h * 0.69f, paint);
            } else if ("listen".equals(type)) {
                // headphones: band + two ear cups
                RectF band = new RectF(w * 0.17f, h * 0.18f, w * 0.83f, h * 0.72f);
                canvas.drawArc(band, 180, 180, false, paint);
                canvas.drawRoundRect(new RectF(w * 0.13f, h * 0.5f, w * 0.32f, h * 0.84f), w * 0.08f, w * 0.08f, paint);
                canvas.drawRoundRect(new RectF(w * 0.68f, h * 0.5f, w * 0.87f, h * 0.84f), w * 0.08f, w * 0.08f, paint);
            } else if ("read".equals(type)) {
                // open book: spine plus two facing pages
                canvas.drawLine(w * 0.5f, h * 0.26f, w * 0.5f, h * 0.84f, paint);
                canvas.drawLine(w * 0.5f, h * 0.26f, w * 0.14f, h * 0.2f, paint);
                canvas.drawLine(w * 0.14f, h * 0.2f, w * 0.14f, h * 0.76f, paint);
                canvas.drawLine(w * 0.14f, h * 0.76f, w * 0.5f, h * 0.84f, paint);
                canvas.drawLine(w * 0.5f, h * 0.26f, w * 0.86f, h * 0.2f, paint);
                canvas.drawLine(w * 0.86f, h * 0.2f, w * 0.86f, h * 0.76f, paint);
                canvas.drawLine(w * 0.86f, h * 0.76f, w * 0.5f, h * 0.84f, paint);
            } else if ("more".equals(type)) {
                canvas.drawCircle(w * 0.22f, h * 0.5f, w * 0.085f, paint);
                canvas.drawCircle(w * 0.5f, h * 0.5f, w * 0.085f, paint);
                canvas.drawCircle(w * 0.78f, h * 0.5f, w * 0.085f, paint);
            } else if ("translate".equals(type)) {
                // two stacked arrows pointing opposite ways (translate both directions)
                canvas.drawLine(w * 0.2f, h * 0.35f, w * 0.8f, h * 0.35f, paint);
                canvas.drawLine(w * 0.8f, h * 0.35f, w * 0.66f, h * 0.24f, paint);
                canvas.drawLine(w * 0.8f, h * 0.35f, w * 0.66f, h * 0.46f, paint);
                canvas.drawLine(w * 0.2f, h * 0.65f, w * 0.8f, h * 0.65f, paint);
                canvas.drawLine(w * 0.2f, h * 0.65f, w * 0.34f, h * 0.54f, paint);
                canvas.drawLine(w * 0.2f, h * 0.65f, w * 0.34f, h * 0.76f, paint);
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
