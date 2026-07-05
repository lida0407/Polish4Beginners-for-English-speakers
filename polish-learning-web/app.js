const DATA_ROOT = "/polish-phrasebook-android/app/src/main/assets";
const SOURCE_LIBRARY = "/polish-learning-web/resource-library.json";
const GRAMMAR_LIBRARY = `${DATA_ROOT}/grammar_lessons.json`;
const memoryKey = "polish-learning-web:memory-status";
const CARD_BATCH_SIZE = 72;

const statusLabels = {
  all: "All",
  new: "New",
  forgot: "Forget",
  learnt: "Learnt",
};

const state = {
  cards: [],
  grammar: [],
  sources: [],
  view: "cards",
  categoryFilter: "All",
  levelFilter: "All",
  statusFilter: "all",
  query: "",
  memory: JSON.parse(localStorage.getItem(memoryKey) || "{}"),
  visibleCards: [],
  renderedCount: 0,
  testMode: false,
  tts: {
    checked: false,
    ready: false,
    engine: "browser",
  },
};

const elements = {
  search: document.querySelector("#searchInput"),
  statusFilters: document.querySelector("#statusFilters"),
  levelFilters: document.querySelector("#levelFilters"),
  categoryFilters: document.querySelector("#categoryFilters"),
  cards: document.querySelector("#cards"),
  empty: document.querySelector("#emptyState"),
  activeTitle: document.querySelector("#activeTitle"),
  visibleCount: document.querySelector("#visibleCount"),
  newCount: document.querySelector("#newCount"),
  forgotCount: document.querySelector("#forgotCount"),
  learntCount: document.querySelector("#learntCount"),
  sourceLibrary: document.querySelector("#sourceLibrary"),
  shuffle: document.querySelector("#shuffleButton"),
  cardsMode: document.querySelector("#cardsModeButton"),
  grammarMode: document.querySelector("#grammarModeButton"),
  sessionMode: document.querySelector("#sessionMode"),
  ttsStatus: document.querySelector("#ttsStatus"),
  cardTemplate: document.querySelector("#cardTemplate"),
  sourceTemplate: document.querySelector("#sourceTemplate"),
};

async function loadJson(path) {
  const response = await fetch(path);
  if (!response.ok) {
    throw new Error(`Could not load ${path}`);
  }
  return response.json();
}

async function boot() {
  try {
    const [cards, grammar, sources] = await Promise.all([
      loadJson(`${DATA_ROOT}/phrases.json`),
      loadJson(GRAMMAR_LIBRARY),
      loadJson(SOURCE_LIBRARY),
    ]);
    state.cards = cards;
    state.grammar = grammar;
    state.sources = sources;
    wireEvents();
    render();
    checkTtsStatus();
  } catch (error) {
    elements.empty.hidden = false;
    elements.empty.textContent = "Start the local server from the workspace root to load the learning data.";
    console.error(error);
  }
}

function wireEvents() {
  elements.search.addEventListener("input", (event) => {
    state.query = event.target.value.trim().toLowerCase();
    renderLearningArea();
  });

  elements.shuffle.addEventListener("click", () => {
    state.view = "cards";
    state.testMode = !state.testMode;
    if (state.testMode) {
      state.cards = shuffle(state.cards);
    }
    render();
  });

  elements.cardsMode.addEventListener("click", () => {
    state.view = "cards";
    state.categoryFilter = "All";
    render();
  });

  elements.grammarMode.addEventListener("click", () => {
    state.view = "grammar";
    state.testMode = false;
    state.categoryFilter = "All";
    render();
  });

  window.addEventListener("scroll", maybeRenderMoreCards, { passive: true });
}

function render() {
  renderModeButtons();
  renderStatusFilters();
  renderLevelFilters();
  renderCategoryFilters();
  renderSourceLibrary();
  renderLearningArea();
}

function renderModeButtons() {
  elements.cardsMode.classList.toggle("is-active", state.view === "cards");
  elements.grammarMode.classList.toggle("is-active", state.view === "grammar");
  elements.shuffle.hidden = state.view === "grammar";
}

function renderLearningArea() {
  if (state.view === "grammar") {
    renderGrammar();
  } else {
    renderCards();
  }
}

function renderStatusFilters() {
  elements.statusFilters.replaceChildren();
  for (const status of ["all", "new", "forgot", "learnt"]) {
    const button = document.createElement("button");
    button.className = `filter-button${state.statusFilter === status ? " is-active" : ""}`;
    button.type = "button";
    button.textContent = statusLabels[status];
    button.addEventListener("click", () => {
      state.statusFilter = status;
      renderStatusFilters();
      renderLearningArea();
    });
    elements.statusFilters.append(button);
  }
}

function renderLevelFilters() {
  elements.levelFilters.replaceChildren();
  for (const level of ["All", "A1", "A2", "B1", "B2", "C1"]) {
    const button = document.createElement("button");
    button.className = `filter-button${state.levelFilter === level ? " is-active" : ""}`;
    button.type = "button";
    button.textContent = level;
    button.addEventListener("click", () => {
      state.levelFilter = level;
      state.categoryFilter = "All";
      renderLevelFilters();
      renderCategoryFilters();
      renderLearningArea();
    });
    elements.levelFilters.append(button);
  }
}

function renderCategoryFilters() {
  const levelItems = state.view === "grammar"
    ? state.grammar.filter((lesson) => state.levelFilter === "All" || (lesson.level || "A1") === state.levelFilter)
    : state.cards.filter((card) => state.levelFilter === "All" || (card.level || "A1") === state.levelFilter);
  const categories = ["All", ...new Set(levelItems.map((item) => state.view === "grammar" ? item.scenario : item.category))];
  elements.categoryFilters.replaceChildren();

  for (const category of categories) {
    const button = document.createElement("button");
    button.className = `filter-button${state.categoryFilter === category ? " is-active" : ""}`;
    button.type = "button";
    button.textContent = category;
    button.addEventListener("click", () => {
      state.categoryFilter = category;
      renderCategoryFilters();
      renderLearningArea();
    });
    elements.categoryFilters.append(button);
  }
}

function getVisibleCards() {
  return state.cards.filter((card) => {
    const status = getMemoryStatus(card);
    if (state.testMode && state.statusFilter === "all" && status === "learnt") {
      return false;
    }
    if (state.statusFilter !== "all" && status !== state.statusFilter) {
      return false;
    }
    if (state.levelFilter !== "All" && (card.level || "A1") !== state.levelFilter) {
      return false;
    }
    if (state.categoryFilter !== "All" && card.category !== state.categoryFilter) {
      return false;
    }
    if (!state.query) {
      return true;
    }
    return `${card.level || "A1"} ${card.category} ${card.polish} ${card.english} ${card.phonetic || ""}`
      .toLowerCase()
      .includes(state.query);
  });
}

function renderCards() {
  state.visibleCards = getVisibleCards();
  state.renderedCount = 0;
  const counts = countStatuses();

  elements.cards.replaceChildren();
  elements.activeTitle.textContent = activeTitleText();
  elements.newCount.textContent = String(counts.new);
  elements.forgotCount.textContent = String(counts.forgot);
  elements.learntCount.textContent = String(counts.learnt);
  elements.empty.hidden = state.visibleCards.length > 0;
  elements.shuffle.textContent = state.testMode ? "End Test" : "Shuffle Test";
  elements.sessionMode.textContent = state.testMode ? "Filtered flashcards" : "Study cards";

  renderMoreCards();
}

function renderMoreCards() {
  const next = state.visibleCards.slice(state.renderedCount, state.renderedCount + CARD_BATCH_SIZE);
  const fragment = document.createDocumentFragment();
  for (const card of next) {
    fragment.append(createCard(card));
  }
  elements.cards.append(fragment);
  state.renderedCount += next.length;
  elements.visibleCount.textContent = `${state.renderedCount}/${state.visibleCards.length}`;
}

function maybeRenderMoreCards() {
  if (state.renderedCount >= state.visibleCards.length) {
    return;
  }
  const distanceFromBottom = document.documentElement.scrollHeight - (window.scrollY + window.innerHeight);
  if (distanceFromBottom < 700) {
    renderMoreCards();
  }
}

function activeTitleText() {
  const level = state.levelFilter === "All" ? "" : `${state.levelFilter}: `;
  const category = state.categoryFilter === "All" ? "All cards" : state.categoryFilter;
  const status = state.statusFilter === "all" ? "" : `: ${statusLabels[state.statusFilter]}`;
  return `${level}${state.testMode ? "Test" : category}${state.testMode ? `: ${category}` : status}`;
}

function renderGrammar() {
  const visibleLessons = getVisibleGrammarLessons();
  const counts = countStatuses();
  elements.cards.replaceChildren();
  elements.activeTitle.textContent = grammarTitleText();
  elements.newCount.textContent = String(counts.new);
  elements.forgotCount.textContent = String(counts.forgot);
  elements.learntCount.textContent = String(counts.learnt);
  elements.visibleCount.textContent = `${visibleLessons.length}/${state.grammar.length}`;
  elements.empty.hidden = visibleLessons.length > 0;
  elements.sessionMode.textContent = "Grammar lessons";

  const fragment = document.createDocumentFragment();
  for (const lesson of visibleLessons) {
    fragment.append(createGrammarCard(lesson));
  }
  elements.cards.append(fragment);
}

function getVisibleGrammarLessons() {
  return state.grammar.filter((lesson) => {
    if (state.levelFilter !== "All" && (lesson.level || "A1") !== state.levelFilter) {
      return false;
    }
    if (state.categoryFilter !== "All" && lesson.scenario !== state.categoryFilter) {
      return false;
    }
    if (!state.query) {
      return true;
    }
    const examples = (lesson.examples || [])
      .map((example) => `${example.polish} ${example.english}`)
      .join(" ");
    return `${lesson.unit} ${lesson.level} ${lesson.scenario} ${lesson.topic} ${lesson.focus} ${lesson.rule} ${lesson.pattern} ${examples} ${lesson.check?.prompt || ""} ${lesson.check?.answer || ""}`
      .toLowerCase()
      .includes(state.query);
  });
}

function grammarTitleText() {
  const level = state.levelFilter === "All" ? "" : `${state.levelFilter}: `;
  const scenario = state.categoryFilter === "All" ? "Grammar path" : state.categoryFilter;
  return `${level}${scenario}`;
}

function createGrammarCard(lesson) {
  const article = document.createElement("article");
  article.className = "study-card grammar-card";

  const meta = document.createElement("div");
  meta.className = "card-meta";
  const category = document.createElement("span");
  category.className = "category";
  category.textContent = `${lesson.level || "A1"} • ${lesson.scenario} • ${lesson.unit}`;
  const badge = document.createElement("span");
  badge.className = "memory-badge";
  badge.textContent = "Grammar";
  meta.append(category, badge);

  const title = document.createElement("h3");
  title.className = "polish";
  title.textContent = lesson.topic;

  const focus = document.createElement("p");
  focus.className = "english";
  focus.textContent = lesson.focus;

  const rule = document.createElement("p");
  rule.className = "note";
  rule.textContent = `Rule: ${lesson.rule}`;

  const pattern = document.createElement("p");
  pattern.className = "note";
  pattern.textContent = `Pattern: ${lesson.pattern}`;

  const examples = document.createElement("ul");
  examples.className = "grammar-examples";
  for (const example of lesson.examples || []) {
    const item = document.createElement("li");
    item.textContent = `${example.polish} - ${example.english}`;
    examples.append(item);
  }

  const check = document.createElement("p");
  check.className = "grammar-check";
  check.textContent = `Check: ${lesson.check?.prompt || ""}`;

  const answer = document.createElement("p");
  answer.className = "note";
  answer.hidden = true;
  answer.textContent = `Answer: ${lesson.check?.answer || ""}. ${lesson.check?.hint || ""}`;

  const actions = document.createElement("div");
  actions.className = "card-actions";
  const read = document.createElement("button");
  read.className = "speak-button";
  read.type = "button";
  read.textContent = "Read Examples";
  read.addEventListener("click", (event) => {
    const text = (lesson.examples || []).map((example) => example.polish).join(". ");
    readText(text, "pl", event.currentTarget);
  });
  const show = document.createElement("button");
  show.className = "ghost-button";
  show.type = "button";
  show.textContent = "Show Check";
  show.addEventListener("click", () => {
    answer.hidden = !answer.hidden;
    show.textContent = answer.hidden ? "Show Check" : "Hide Check";
  });
  actions.append(read, show);

  article.append(meta, title, focus, rule, pattern, examples, check, answer, actions);
  return article;
}

function createCard(card) {
  const node = elements.cardTemplate.content.firstElementChild.cloneNode(true);
  const status = getMemoryStatus(card);
  const english = node.querySelector(".english");
  const note = node.querySelector(".note");
  const reveal = node.querySelector(".reveal-button");
  const choices = node.querySelector(".choice-actions");

  node.dataset.status = status;
  node.classList.toggle("is-test", state.testMode);
  node.querySelector(".category").textContent = `${card.level || "A1"} • ${card.category}`;
  node.querySelector(".memory-badge").textContent = statusLabels[status];
  node.querySelector(".polish").textContent = card.polish;
  english.textContent = card.english;
  note.textContent = card.phonetic || "";

  if (state.testMode) {
    english.hidden = true;
    note.hidden = true;
    reveal.hidden = true;
    choices.hidden = false;
    for (const choice of answerChoices(card)) {
      const button = document.createElement("button");
      button.className = "choice-button";
      button.type = "button";
      button.textContent = choice;
      button.addEventListener("click", () => {
        const correct = choice === card.english;
        for (const other of choices.querySelectorAll("button")) {
          other.disabled = true;
          other.classList.toggle("is-correct", other.textContent === card.english);
        }
        button.classList.toggle("is-wrong", !correct);
        english.hidden = false;
      });
      choices.append(button);
    }
  }

  for (const button of node.querySelectorAll(".memory-button")) {
    const nextStatus = button.dataset.status;
    button.classList.toggle("is-active", nextStatus === status);
    button.addEventListener("click", () => setMemoryStatus(card, nextStatus));
  }

  node.querySelector(".speak-polish").addEventListener("click", (event) => {
    readText(card.polish, "pl", event.currentTarget);
  });
  node.querySelector(".speak-english").addEventListener("click", (event) => {
    readText(card.english, "en", event.currentTarget);
  });
  return node;
}

function answerChoices(card) {
  const choices = new Set([card.english]);
  const pool = shuffle(state.visibleCards.length > 1 ? state.visibleCards : state.cards);
  for (const candidate of pool) {
    if (choices.size >= 4) {
      break;
    }
    if (candidate.english !== card.english) {
      choices.add(candidate.english);
    }
  }
  return shuffle([...choices]);
}

function renderSourceLibrary() {
  if (!elements.sourceLibrary) {
    return;
  }
  elements.sourceLibrary.replaceChildren();

  for (const source of state.sources) {
    const node = elements.sourceTemplate.content.firstElementChild.cloneNode(true);
    const imageWrap = node.querySelector(".source-image-wrap");
    const images = source.images || [];

    if (images.length > 0) {
      imageWrap.classList.toggle("is-gallery", images.length > 1);
      for (const image of images.slice(0, 2)) {
        const img = document.createElement("img");
        img.src = image.url;
        img.alt = image.alt || source.title;
        img.loading = "lazy";
        imageWrap.append(img);
      }
    } else {
      const placeholder = document.createElement("div");
      placeholder.className = "source-placeholder";
      placeholder.textContent = source.source;
      imageWrap.append(placeholder);
    }

    node.querySelector(".source-name").textContent = source.source;
    node.querySelector("h3").textContent = source.title;
    node.querySelector("p").textContent = source.summary;

    const list = node.querySelector("ul");
    for (const item of source.items || []) {
      const li = document.createElement("li");
      li.textContent = item;
      list.append(li);
    }

    elements.sourceLibrary.append(node);
  }
}

function getMemoryStatus(card) {
  const status = state.memory[cardKey(card)];
  return status === "forgot" || status === "learnt" || status === "new" ? status : "new";
}

function setMemoryStatus(card, status) {
  state.memory[cardKey(card)] = status;
  localStorage.setItem(memoryKey, JSON.stringify(state.memory));
  renderCards();
  renderStatusFilters();
}

function countStatuses() {
  return state.cards.reduce(
    (counts, card) => {
      counts[getMemoryStatus(card)] += 1;
      return counts;
    },
    { new: 0, forgot: 0, learnt: 0 }
  );
}

async function checkTtsStatus() {
  try {
    const response = await fetch("/api/tts/status");
    if (!response.ok) {
      throw new Error("No local Supertonic server");
    }
    const data = await response.json();
    state.tts = {
      checked: true,
      ready: Boolean(data.ready),
      engine: data.ready ? "supertonic" : "browser",
    };
  } catch {
    state.tts = {
      checked: true,
      ready: false,
      engine: "browser",
    };
  }
  renderTtsStatus();
}

function renderTtsStatus() {
  if (!elements.ttsStatus) {
    return;
  }
  elements.ttsStatus.textContent = state.tts.ready
    ? "Voice: Supertonic reads Polish and English."
    : "Voice: browser fallback. Start the Supertonic server for local model audio.";
}

async function readText(text, lang, button) {
  const label = button?.textContent;
  if (button) {
    button.classList.add("is-loading");
    button.textContent = "Reading...";
    button.disabled = true;
  }

  try {
    await readWithSupertonic(text, lang);
  } catch (error) {
    readWithBrowserVoice(text, lang);
  } finally {
    if (button) {
      button.classList.remove("is-loading");
      button.textContent = label;
      button.disabled = false;
    }
  }
}

async function readWithSupertonic(text, lang) {
  const response = await fetch("/api/tts", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      text,
      lang,
      voice: "M1",
      speed: lang === "pl" ? 0.95 : 1.0,
      steps: 8,
    }),
  });

  if (!response.ok) {
    throw new Error("Supertonic unavailable");
  }

  const blob = await response.blob();
  const audioUrl = URL.createObjectURL(blob);
  const audio = new Audio(audioUrl);
  audio.addEventListener("ended", () => URL.revokeObjectURL(audioUrl), { once: true });
  await audio.play();
}

function readWithBrowserVoice(text, lang) {
  if (!("speechSynthesis" in window)) {
    return;
  }
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = lang === "pl" ? "pl-PL" : "en-US";
  utterance.rate = lang === "pl" ? 0.92 : 0.98;
  window.speechSynthesis.speak(utterance);
}

function shuffle(items) {
  const next = [...items];
  for (let index = next.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(Math.random() * (index + 1));
    [next[index], next[swapIndex]] = [next[swapIndex], next[index]];
  }
  return next;
}

function cardKey(card) {
  if (card.coreIndex) {
    return `core:${card.coreIndex}`;
  }
  return `${card.category}:${card.polish}`;
}

boot();
