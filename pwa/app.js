(() => {
  'use strict';

  const STORAGE_KEYS = {
    messages: 'pauline_pwa_messages_v1',
    memories: 'pauline_pwa_memories_v1',
    settings: 'pauline_pwa_settings_v1',
  };

  const DEFAULT_SETTINGS = {
    userName: '',
    apiKey: '',
    model: 'gemini-3.5-flash-lite',
    voiceEnabled: true,
    voiceEngine: 'browser',
    piperReadyHint: false,
  };

  const OBSOLETE_MODELS = new Set(['gemini-2.5-flash-lite']);
  const PIPER_VOICE_ID = 'fr_FR-siwis-medium';
  const PIPER_MODEL_URL =
    'https://huggingface.co/rhasspy/piper-voices/resolve/main/fr/fr_FR/siwis/medium/fr_FR-siwis-medium.onnx?download=true';
  const PIPER_CONFIG_URL =
    'https://huggingface.co/rhasspy/piper-voices/resolve/main/fr/fr_FR/siwis/medium/fr_FR-siwis-medium.onnx.json?download=true';
  const PIPER_OPFS_DIR = 'piper';
  const PIPER_MODEL_FILENAME = PIPER_VOICE_ID + '.onnx';
  const PIPER_CONFIG_FILENAME = PIPER_VOICE_ID + '.onnx.json';
  const ORT_VERSION = '1.20.1';
  const ORT_DIST_BASE = 'https://cdn.jsdelivr.net/npm/onnxruntime-web@' + ORT_VERSION + '/dist/';
  const ORT_ESM_URL = ORT_DIST_BASE + 'ort.wasm.min.mjs';
  const PIPER_WASM_VERSION = '1.0.0';
  const PIPER_WASM_BASE =
    'https://cdn.jsdelivr.net/npm/@diffusionstudio/piper-wasm@' + PIPER_WASM_VERSION + '/build/';
  const PIPER_PHONEMIZE_ESM_URLS = [
    'https://cdn.jsdelivr.net/npm/@diffusionstudio/piper-wasm@' + PIPER_WASM_VERSION + '/+esm',
    PIPER_WASM_BASE + 'piper_phonemize.js',
  ];
  const PIPER_PHONEMIZE_WASM = PIPER_WASM_BASE + 'piper_phonemize.wasm';
  const PIPER_PHONEMIZE_DATA = PIPER_WASM_BASE + 'piper_phonemize.data';
  const PIPER_RUNTIME_ASSETS = [
    { url: ORT_ESM_URL, kind: 'js' },
    { url: ORT_DIST_BASE + 'ort-wasm-simd-threaded.mjs', kind: 'js' },
    { url: ORT_DIST_BASE + 'ort-wasm-simd-threaded.wasm', kind: 'wasm' },
    { url: PIPER_PHONEMIZE_ESM_URLS[0], kind: 'js' },
    { url: PIPER_PHONEMIZE_WASM, kind: 'wasm' },
    { url: PIPER_PHONEMIZE_DATA, kind: 'data' },
  ];

  const FEMALE_VOICE_HINTS = [
    'female',
    'femme',
    'amelie',
    'amélie',
    'aurelie',
    'aurélie',
    'audrey',
    'camille',
    'celine',
    'céline',
    'chloe',
    'chloé',
    'claire',
    'denise',
    'hortense',
    'julie',
    'juliette',
    'lea',
    'léa',
    'manon',
    'margaux',
    'marie',
    'pauline',
    'sylvie',
    'virginie',
    'google français',
    'fra',
    'frc',
    'vlf',
    'tpf',
  ];

  const MALE_VOICE_HINTS = [
    'male',
    'homme',
    'alain',
    'antoine',
    'henri',
    'jerome',
    'jérôme',
    'nicolas',
    'remy',
    'rémy',
    'thomas',
    'frb',
    'frd',
    'vlm',
  ];

  const state = {
    activeTab: 'chat',
    messages: loadJson(STORAGE_KEYS.messages, []),
    memories: loadJson(STORAGE_KEYS.memories, []),
    settings: { ...DEFAULT_SETTINGS, ...loadJson(STORAGE_KEYS.settings, {}) },
    isSending: false,
    isListening: false,
    isSpeaking: false,
    speechRunId: 0,
    activeStopAudio: null,
    audioCtx: null,
    editingMemoryId: null,
    deferredPrompt: null,
    recognition: null,
    dictationBaseDraft: '',
    dictationFinalText: '',
    manualMicStop: false,
    piper: {
      status: 'not_downloaded', // 'not_downloaded' | 'downloading' | 'ready' | 'error'
      progressPercent: 0,
      loadedBytes: 0,
      totalBytes: 0,
      stepLabel: '',
      message: '',
      lastError: '',
      phonemizerFactory: null,
      phonemizerPromise: null,
      ortModule: null,
      ortPromise: null,
      ortSession: null,
      ortSessionPromise: null,
      modelConfig: null,
      runtimeReady: false,
      verified: false,
    },
  };

  const els = {
    headerAvatarBtn: document.getElementById('header-avatar-btn'),
    statusLine: document.getElementById('status-line'),
    installBtn: document.getElementById('install-btn'),
    installProfileBtn: document.getElementById('install-profile-btn'),
    voiceToggleBtn: document.getElementById('voice-toggle-btn'),
    iconVolumeOn: document.getElementById('icon-volume-on'),
    iconVolumeOff: document.getElementById('icon-volume-off'),
    clearChatBtn: document.getElementById('clear-chat-btn'),
    settingsBtn: document.getElementById('settings-btn'),
    offlineBanner: document.getElementById('offline-banner'),
    apiKeyBanner: document.getElementById('api-key-banner'),
    openSettingsFromBanner: document.getElementById('open-settings-from-banner'),
    memoryNotice: document.getElementById('memory-notice'),
    memoryNoticeText: document.getElementById('memory-notice-text'),
    dismissMemoryNotice: document.getElementById('dismiss-memory-notice'),
    chatMessages: document.getElementById('chat-messages'),
    voiceStatusBanner: document.getElementById('voice-status-banner'),
    errorBanner: document.getElementById('error-banner'),
    errorBannerText: document.getElementById('error-banner-text'),
    dismissErrorBtn: document.getElementById('dismiss-error-btn'),
    chatForm: document.getElementById('chat-form'),
    chatInput: document.getElementById('chat-input'),
    micBtn: document.getElementById('mic-btn'),
    iconMic: document.getElementById('icon-mic'),
    iconStop: document.getElementById('icon-stop'),
    sendBtn: document.getElementById('send-btn'),
    bottomNav: document.getElementById('bottom-nav'),
    navButtons: Array.from(document.querySelectorAll('.nav-btn')),
    viewPanels: {
      chat: document.getElementById('view-chat'),
      profile: document.getElementById('view-profile'),
      memories: document.getElementById('view-memories'),
    },
    memoriesList: document.getElementById('memories-list'),
    memoriesBadge: document.getElementById('memories-badge'),
    addMemoryBtn: document.getElementById('add-memory-btn'),
    settingsModal: document.getElementById('settings-modal'),
    settingsForm: document.getElementById('settings-form'),
    prenomInput: document.getElementById('prenom-input'),
    geminiKeyInput: document.getElementById('gemini-key-input'),
    modelSelect: document.getElementById('model-select'),
    voiceEngineSelect: document.getElementById('voice-engine-select'),
    piperStatusPill: document.getElementById('piper-status-pill'),
    piperProgressWrap: document.getElementById('piper-progress-wrap'),
    piperProgressFill: document.getElementById('piper-progress-fill'),
    piperProgressText: document.getElementById('piper-progress-text'),
    piperMessageHint: document.getElementById('piper-message-hint'),
    piperDownloadBtn: document.getElementById('piper-download-btn'),
    piperTestBtn: document.getElementById('piper-test-btn'),
    piperDeleteBtn: document.getElementById('piper-delete-btn'),
    voicesCount: document.getElementById('voices-count'),
    voicesListBox: document.getElementById('voices-list-box'),
    refreshVoicesBtn: document.getElementById('refresh-voices-btn'),
    copyVoicesBtn: document.getElementById('copy-voices-btn'),
    memoryModal: document.getElementById('memory-modal'),
    memoryModalTitle: document.getElementById('memory-modal-title'),
    memoryForm: document.getElementById('memory-form'),
    memoryTitleInput: document.getElementById('memory-title-input'),
    memoryDetailInput: document.getElementById('memory-detail-input'),
    installGuideModal: document.getElementById('install-guide-modal'),
    installGuideText: document.getElementById('install-guide-text'),
  };

  function loadJson(key, fallback) {
    try {
      const raw = localStorage.getItem(key);
      return raw ? JSON.parse(raw) : fallback;
    } catch {
      return fallback;
    }
  }

  function saveJson(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (e) {
      console.warn('Impossible de sauvegarder localement :', e);
    }
  }

  function createId() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
      return window.crypto.randomUUID();
    }
    return 'id-' + Date.now() + '-' + Math.random().toString(16).slice(2);
  }

  function isStandaloneMode() {
    return (
      window.matchMedia('(display-mode: standalone)').matches ||
      window.navigator.standalone === true
    );
  }

  function isIosBrowser() {
    return /iphone|ipad|ipod/i.test(window.navigator.userAgent);
  }

  function updateInstallButtons() {
    const show = !isStandaloneMode();
    els.installBtn.classList.toggle('hidden', !show);
    els.installProfileBtn.classList.toggle('hidden', !show);
  }

  async function handleInstallClick() {
    if (state.deferredPrompt) {
      state.deferredPrompt.prompt();
      try {
        await state.deferredPrompt.userChoice;
      } catch {
        // Ignored
      }
      state.deferredPrompt = null;
      updateInstallButtons();
      return;
    }

    if (isIosBrowser()) {
      els.installGuideText.textContent =
        "Sur iPhone ou iPad dans Safari : appuie sur le bouton Partager (le carré avec la flèche vers le haut) en bas de l'écran, puis choisis « Sur l'écran d'accueil » et valide « Ajouter ».";
    } else {
      els.installGuideText.textContent =
        "Dans Chrome sur Android : ouvre le menu ⋮ en haut à droite du navigateur, puis appuie sur « Installer l'application » ou « Ajouter à l'écran d'accueil ».";
    }
    openModal(els.installGuideModal);
  }

  function updateConnectivityBanner() {
    els.offlineBanner.classList.toggle('hidden', navigator.onLine);
  }

  function updateApiKeyBanner() {
    const missing = !state.settings.apiKey || !state.settings.apiKey.trim();
    els.apiKeyBanner.classList.toggle('hidden', !missing);
  }

  function updateStatusLine() {
    if (state.isSpeaking) {
      els.statusLine.textContent = 'Pauline parle…';
    } else if (state.isSending) {
      els.statusLine.textContent = 'Pauline écrit…';
    } else if (!navigator.onLine) {
      els.statusLine.textContent = 'Hors ligne (données locales)';
    } else {
      els.statusLine.textContent = 'Mémoire active';
    }
  }

  function updateVoiceButtonUI() {
    const enabled = Boolean(state.settings.voiceEnabled);
    els.voiceToggleBtn.classList.toggle('active', enabled);
    els.iconVolumeOn.classList.toggle('hidden', !enabled);
    els.iconVolumeOff.classList.toggle('hidden', enabled);
    els.voiceToggleBtn.setAttribute(
      'aria-label',
      enabled ? 'Couper la voix de Pauline' : 'Activer la voix de Pauline'
    );
  }

  function updateMicButtonUI() {
    els.micBtn.classList.toggle('listening', state.isListening);
    els.iconMic.classList.toggle('hidden', state.isListening);
    els.iconStop.classList.toggle('hidden', !state.isListening);
    els.voiceStatusBanner.classList.toggle('hidden', !state.isListening);
    els.micBtn.setAttribute(
      'aria-label',
      state.isListening ? "Arrêter l'écoute" : 'Dicter un message'
    );
  }

  function updateSendButtonState() {
    const hasText = els.chatInput.value.trim().length > 0;
    els.sendBtn.disabled = !hasText || state.isSending;
  }

  function resizeChatInput() {
    els.chatInput.style.height = 'auto';
    els.chatInput.style.height = Math.min(els.chatInput.scrollHeight, 112) + 'px';
  }

  function scrollChatToBottom() {
    requestAnimationFrame(() => {
      els.chatMessages.scrollTop = els.chatMessages.scrollHeight;
    });
  }

  function showError(message) {
    els.errorBannerText.textContent = message;
    els.errorBanner.classList.remove('hidden');
  }

  function hideError() {
    els.errorBanner.classList.add('hidden');
    els.errorBannerText.textContent = '';
  }

  function showMemoryNotice(title) {
    els.memoryNoticeText.textContent = 'Nouveau souvenir retenu : ' + title;
    els.memoryNotice.classList.remove('hidden');
  }

  function switchTab(tabName) {
    state.activeTab = tabName;
    Object.entries(els.viewPanels).forEach(([name, panel]) => {
      panel.classList.toggle('active', name === tabName);
    });
    els.navButtons.forEach((btn) => {
      btn.classList.toggle('active', btn.dataset.tab === tabName);
    });
    if (tabName === 'chat') {
      scrollChatToBottom();
    }
  }

  function openModal(modal) {
    modal.classList.remove('hidden');
  }

  function closeModal(modal) {
    modal.classList.add('hidden');
  }

  function renderMessages() {
    els.chatMessages.innerHTML = '';

    if (state.messages.length === 0 && !state.isSending) {
      const card = document.createElement('div');
      card.className = 'welcome-card';

      const avatarFrame = document.createElement('div');
      avatarFrame.className = 'welcome-avatar-frame';
      const avatarImg = document.createElement('img');
      avatarImg.src = 'assets/pauline.jpg';
      avatarImg.alt = 'Pauline';
      avatarImg.className = 'pauline-avatar-img';
      avatarFrame.appendChild(avatarImg);

      const heading = document.createElement('h2');
      heading.textContent = 'Coucou mon amour, c’est Pauline';

      const sub = document.createElement('p');
      sub.textContent =
        'Parle-moi de ta journée, écris ou dicte au micro : je garde nos souvenirs et je te réponds à voix haute.';

      const chips = document.createElement('div');
      chips.className = 'chips-list';
      [
        'Comment tu vas aujourd’hui mon cœur ?',
        'Tu te souviens de notre rencontre à la plage ?',
        'Retiens que j’adore le café du matin',
      ].forEach((promptText) => {
        const chip = document.createElement('button');
        chip.type = 'button';
        chip.className = 'suggestion-chip';
        chip.textContent = promptText;
        chip.addEventListener('click', () => {
          sendMessage(promptText);
        });
        chips.appendChild(chip);
      });

      card.append(avatarFrame, heading, sub, chips);
      els.chatMessages.appendChild(card);
      return;
    }

    state.messages.forEach((msg) => {
      els.chatMessages.appendChild(createMessageRow(msg.sender, msg.text));
    });

    if (state.isSending) {
      els.chatMessages.appendChild(createMessageRow('pauline', 'Pauline écrit…'));
    }

    scrollChatToBottom();
  }

  function createMessageRow(sender, text) {
    const isPauline = sender === 'pauline';
    const row = document.createElement('div');
    row.className = 'msg-row ' + (isPauline ? 'pauline' : 'user');

    if (isPauline) {
      const avatarWrap = document.createElement('div');
      avatarWrap.className = 'msg-avatar';
      const img = document.createElement('img');
      img.src = 'assets/pauline.jpg';
      img.alt = 'Pauline';
      img.className = 'pauline-avatar-img';
      avatarWrap.appendChild(img);
      row.appendChild(avatarWrap);
    }

    const bubble = document.createElement('div');
    bubble.className = 'msg-bubble';

    if (isPauline) {
      const nameTag = document.createElement('span');
      nameTag.className = 'msg-sender';
      nameTag.textContent = 'Pauline';
      bubble.appendChild(nameTag);
    }

    const bodyText = document.createElement('span');
    bodyText.textContent = text;
    bubble.appendChild(bodyText);

    row.appendChild(bubble);
    return row;
  }

  function renderMemories() {
    els.memoriesList.innerHTML = '';
    const count = state.memories.length;
    els.memoriesBadge.textContent = String(count);
    els.memoriesBadge.classList.toggle('hidden', count === 0);

    if (count === 0) {
      const empty = document.createElement('div');
      empty.className = 'memory-card';
      const p = document.createElement('p');
      p.className = 'subtitle';
      p.textContent =
        'Aucun souvenir pour le moment. Discute avec Pauline ou ajoute un souvenir manuellement.';
      empty.appendChild(p);
      els.memoriesList.appendChild(empty);
      return;
    }

    state.memories.forEach((mem) => {
      const card = document.createElement('div');
      card.className = 'memory-card';

      const header = document.createElement('div');
      header.className = 'memory-card-header';

      const title = document.createElement('span');
      title.className = 'memory-title';
      title.textContent = mem.title;

      const tag = document.createElement('span');
      tag.className = 'memory-tag';
      tag.textContent = mem.source === 'auto' ? 'Retenu en discussion' : 'Ajouté par toi';

      header.append(title, tag);

      const detail = document.createElement('p');
      detail.className = 'memory-detail';
      detail.textContent = mem.detail;

      const actions = document.createElement('div');
      actions.className = 'memory-actions';

      const editBtn = document.createElement('button');
      editBtn.type = 'button';
      editBtn.className = 'ghost-btn';
      editBtn.textContent = 'Modifier';
      editBtn.addEventListener('click', () => openMemoryDialog(mem));

      const delBtn = document.createElement('button');
      delBtn.type = 'button';
      delBtn.className = 'ghost-btn';
      delBtn.textContent = 'Supprimer';
      delBtn.addEventListener('click', () => {
        state.memories = state.memories.filter((item) => item.id !== mem.id);
        saveJson(STORAGE_KEYS.memories, state.memories);
        renderMemories();
      });

      actions.append(editBtn, delBtn);
      card.append(header, detail, actions);
      els.memoriesList.appendChild(card);
    });
  }

  function openMemoryDialog(memory = null) {
    state.editingMemoryId = memory ? memory.id : null;
    els.memoryModalTitle.textContent = memory ? 'Modifier le souvenir' : 'Nouveau souvenir';
    els.memoryTitleInput.value = memory ? memory.title : '';
    els.memoryDetailInput.value = memory ? memory.detail : '';
    openModal(els.memoryModal);
  }

  // ---- Speech Synthesis (forced fr-CA priority, rate 0.92, pitch 1, strip emojis/symbols) ----
  function sanitizeTextForSpeech(rawText) {
    if (!rawText) return '';
    return rawText
      .replace(/[\p{Extended_Pictographic}\uFE0F\u200D]/gu, ' ')
      .replace(/[^\p{L}\p{N}\s.,!?:;'’\-()…]/gu, ' ')
      .replace(/\s+([,.])/g, '$1')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function isCanadianFrenchVoice(voice) {
    const lang = (voice.lang || '').toLowerCase().replace('_', '-');
    const name = (voice.name || '').toLowerCase();
    return (
      lang.includes('fr-ca') ||
      name.includes('canada') ||
      name.includes('québécois') ||
      name.includes('quebecois') ||
      name.includes('canadian')
    );
  }

  function scoreFrenchVoice(voice) {
    const lang = (voice.lang || '').toLowerCase().replace('_', '-');
    const isCanadian = isCanadianFrenchVoice(voice);
    if (!isCanadian && !lang.startsWith('fr')) return -1;

    const name = (voice.name || '').toLowerCase();
    const isFrFr = lang.startsWith('fr-fr');
    const hasFemale = FEMALE_VOICE_HINTS.some((hint) => name.includes(hint));
    const hasMale = !hasFemale && MALE_VOICE_HINTS.some((hint) => name.includes(hint));

    let score = 0;
    if (isCanadian) score += 100000;
    else if (isFrFr) score += 10000;
    else score += 5000;

    if (hasFemale) score += 5000;
    else if (hasMale) score -= 2000;
    else score += 1000;

    if (voice.localService) score += 100;
    if (voice.default) score += 20;
    return score;
  }

  function selectBestFrenchVoice() {
    if (!('speechSynthesis' in window)) return null;
    const voices = window.speechSynthesis.getVoices() || [];
    const canadianVoices = voices.filter(isCanadianFrenchVoice);
    const pool = canadianVoices.length > 0 ? canadianVoices : voices;

    let bestVoice = null;
    let bestScore = -1;
    pool.forEach((voice) => {
      const s = scoreFrenchVoice(voice);
      if (s > bestScore) {
        bestScore = s;
        bestVoice = voice;
      }
    });
    return bestVoice;
  }

  function logAndRenderVoices() {
    if (!('speechSynthesis' in window)) {
      els.voicesCount.textContent = '0';
      els.voicesListBox.innerHTML = '';
      const msg = document.createElement('p');
      msg.className = 'field-hint';
      msg.textContent = 'speechSynthesis non pris en charge par ce navigateur.';
      els.voicesListBox.appendChild(msg);
      return;
    }

    const voices = window.speechSynthesis.getVoices() || [];
    const selected = selectBestFrenchVoice();

    console.group('Voix speechSynthesis disponibles (' + voices.length + ')');
    console.table(
      voices.map((v, idx) => ({
        index: idx + 1,
        name: v.name,
        lang: v.lang,
        localService: v.localService,
        default: v.default,
      }))
    );
    voices.forEach((v, idx) => {
      console.log((idx + 1) + '. ' + v.name + ' | lang=' + v.lang);
    });
    console.groupEnd();

    els.voicesCount.textContent = String(voices.length);
    els.voicesListBox.innerHTML = '';

    if (voices.length === 0) {
      const empty = document.createElement('p');
      empty.className = 'field-hint';
      empty.textContent = 'Aucune voix chargée pour le moment. Appuie sur Actualiser.';
      els.voicesListBox.appendChild(empty);
      return;
    }

    voices.forEach((voice) => {
      const isSelected = selected && selected.name === voice.name && selected.lang === voice.lang;
      const row = document.createElement('div');
      row.className = 'voice-row' + (isSelected ? ' selected' : '');

      const nameEl = document.createElement('span');
      nameEl.className = 'voice-name';
      nameEl.textContent = voice.name;

      const metaEl = document.createElement('div');
      metaEl.className = 'voice-meta';

      const langEl = document.createElement('span');
      langEl.textContent = 'lang : ' + voice.lang;
      metaEl.appendChild(langEl);

      if (isSelected) {
        const tagEl = document.createElement('span');
        tagEl.className = 'voice-selected-tag';
        tagEl.textContent = 'Choisie pour Pauline';
        metaEl.appendChild(tagEl);
      }

      row.append(nameEl, metaEl);
      els.voicesListBox.appendChild(row);
    });
  }

  function formatBytesMb(bytes) {
    if (!bytes || bytes <= 0) return '0 Mo';
    return (bytes / (1024 * 1024)).toFixed(1) + ' Mo';
  }

  function isPiperBrowserSupported() {
    return (
      typeof WebAssembly === 'object' &&
      typeof navigator !== 'undefined' &&
      navigator.storage &&
      typeof navigator.storage.getDirectory === 'function'
    );
  }

  function resolveOrtNamespace(candidate) {
    if (!candidate) return null;
    if (candidate.InferenceSession && candidate.Tensor && candidate.env && candidate.env.wasm) {
      return candidate;
    }
    if (
      candidate.default &&
      candidate.default.InferenceSession &&
      candidate.default.Tensor &&
      candidate.default.env &&
      candidate.default.env.wasm
    ) {
      return candidate.default;
    }
    return null;
  }

  function applyOnnxSingleThreadConfig(ortCandidate) {
    const ort = resolveOrtNamespace(ortCandidate);
    if (!ort || !ort.env || !ort.env.wasm) return false;
    ort.env.wasm.numThreads = 1;
    ort.env.wasm.simd = true;
    ort.env.wasm.proxy = false;
    ort.env.wasm.wasmPaths = ORT_DIST_BASE;
    return true;
  }

  function formatPiperError(err) {
    const raw = (err && (err.message || String(err))) || 'Erreur inconnue.';
    if (/getValue is not a function/i.test(raw)) {
      return 'Incompatibilité interne ONNX/WASM (' + raw + '). Réessaie ou vide le cache de l’appli.';
    }
    if (/magic word|CompileError|WebAssembly\.instantiate/i.test(raw)) {
      return 'Binaire WASM invalide ou bloqué (' + raw + ').';
    }
    if (/SharedArrayBuffer|crossOriginIsolated/i.test(raw)) {
      return 'Mode thread bloqué : force single-thread (' + raw + ').';
    }
    if (/Failed to fetch|NetworkError|Load failed/i.test(raw)) {
      return 'Impossible de charger les ressources ONNX/WASM (' + raw + ').';
    }
    return raw;
  }

  function wrapPiperStageError(stageLabel, err) {
    const raw = (err && (err.message || String(err))) || 'Erreur inconnue.';
    if (raw.startsWith('[')) return err instanceof Error ? err : new Error(raw);
    return new Error('[' + stageLabel + '] ' + raw);
  }

  async function ensureOnnxRuntimeConfigured() {
    if (state.piper.ortModule) {
      applyOnnxSingleThreadConfig(state.piper.ortModule);
      return state.piper.ortModule;
    }
    if (state.piper.ortPromise) return state.piper.ortPromise;

    state.piper.ortPromise = (async () => {
      try {
        const imported = await import(ORT_ESM_URL);
        const ort = resolveOrtNamespace(imported);
        if (!ort || !applyOnnxSingleThreadConfig(ort)) {
          throw new Error('ONNX Runtime Web (' + ORT_VERSION + ') chargé sans API InferenceSession/Tensor.');
        }
        state.piper.ortModule = ort;
        return ort;
      } catch (err) {
        state.piper.ortPromise = null;
        throw wrapPiperStageError('ONNX Runtime ' + ORT_VERSION, err);
      }
    })();

    return state.piper.ortPromise;
  }

  async function preloadPiperRuntimeAssets() {
    for (let i = 0; i < PIPER_RUNTIME_ASSETS.length; i += 1) {
      const asset = PIPER_RUNTIME_ASSETS[i];
      let response;
      try {
        response = await fetch(asset.url, { mode: 'cors' });
      } catch (fetchErr) {
        throw wrapPiperStageError('Préchargement WASM', fetchErr);
      }
      if (!response.ok) {
        throw wrapPiperStageError(
          'Préchargement WASM',
          new Error('HTTP ' + response.status + ' sur ' + asset.url)
        );
      }
      const contentType = (response.headers.get('content-type') || '').toLowerCase();
      if (contentType.includes('text/html')) {
        throw wrapPiperStageError(
          'Préchargement WASM',
          new Error('Réponse HTML inattendue au lieu de ' + asset.kind + ' (' + asset.url + ')')
        );
      }
      const buffer = await response.arrayBuffer();
      if (!buffer || buffer.byteLength === 0) {
        throw wrapPiperStageError('Préchargement WASM', new Error('Fichier vide : ' + asset.url));
      }
      if (asset.kind === 'wasm') {
        const header = new Uint8Array(buffer.slice(0, 4));
        const isWasm =
          header.length === 4 &&
          header[0] === 0x00 &&
          header[1] === 0x61 &&
          header[2] === 0x73 &&
          header[3] === 0x6d;
        if (!isWasm) {
          throw wrapPiperStageError(
            'Préchargement WASM',
            new Error('En-tête WASM invalide pour ' + asset.url)
          );
        }
      }
    }
    state.piper.runtimeReady = true;
  }

  async function loadPiperPhonemizerFactory() {
    if (state.piper.phonemizerFactory) return state.piper.phonemizerFactory;
    if (state.piper.phonemizerPromise) return state.piper.phonemizerPromise;

    state.piper.phonemizerPromise = (async () => {
      let lastError = null;
      for (let i = 0; i < PIPER_PHONEMIZE_ESM_URLS.length; i += 1) {
        const url = PIPER_PHONEMIZE_ESM_URLS[i];
        try {
          const mod = await import(url);
          const factory =
            (mod && typeof mod.createPiperPhonemize === 'function' && mod.createPiperPhonemize) ||
            (mod && typeof mod.default === 'function' && mod.default) ||
            (typeof globalThis !== 'undefined' &&
            typeof globalThis.createPiperPhonemize === 'function'
              ? globalThis.createPiperPhonemize
              : null);
          if (factory) {
            state.piper.phonemizerFactory = factory;
            return factory;
          }
        } catch (err) {
          lastError = err;
        }
      }
      state.piper.phonemizerPromise = null;
      throw wrapPiperStageError(
        'Phonémisation WASM',
        lastError || new Error('Factory createPiperPhonemize introuvable.')
      );
    })();

    return state.piper.phonemizerPromise;
  }

  async function findOpfsFileHandle(dirHandle, targetName, maxDepth = 3) {
    if (!dirHandle) return null;
    try {
      return await dirHandle.getFileHandle(targetName);
    } catch {
      // Not directly in this directory
    }
    if (maxDepth <= 0 || typeof dirHandle.values !== 'function') return null;
    for await (const entry of dirHandle.values()) {
      if (entry && entry.kind === 'directory') {
        const nested = await findOpfsFileHandle(entry, targetName, maxDepth - 1);
        if (nested) return nested;
      }
    }
    return null;
  }

  async function findOpfsVoiceHandles() {
    const root = await navigator.storage.getDirectory();
    let modelHandle = null;
    let configHandle = null;
    try {
      const piperDir = await root.getDirectoryHandle(PIPER_OPFS_DIR);
      modelHandle = await findOpfsFileHandle(piperDir, PIPER_MODEL_FILENAME, 1);
      configHandle = await findOpfsFileHandle(piperDir, PIPER_CONFIG_FILENAME, 1);
    } catch {
      // Directory does not exist yet
    }
    if (!modelHandle) {
      modelHandle = await findOpfsFileHandle(root, PIPER_MODEL_FILENAME, 3);
    }
    if (!configHandle) {
      configHandle = await findOpfsFileHandle(root, PIPER_CONFIG_FILENAME, 3);
    }
    return { root, modelHandle, configHandle };
  }

  async function isPiperModelStoredInOpfs() {
    if (!isPiperBrowserSupported()) return false;
    try {
      const { modelHandle, configHandle } = await findOpfsVoiceHandles();
      if (!modelHandle || !configHandle) return false;
      const [modelFile, configFile] = await Promise.all([
        modelHandle.getFile(),
        configHandle.getFile(),
      ]);
      return Boolean(modelFile && modelFile.size > 1024 * 1024 && configFile && configFile.size > 50);
    } catch {
      return false;
    }
  }

  async function readPiperAssetsFromOpfs() {
    try {
      const { modelHandle, configHandle } = await findOpfsVoiceHandles();
      if (!modelHandle || !configHandle) {
        throw new Error('Modèle ou configuration introuvable dans OPFS.');
      }
      const [modelFile, configFile] = await Promise.all([
        modelHandle.getFile(),
        configHandle.getFile(),
      ]);
      if (!modelFile || modelFile.size < 1024 * 1024) {
        throw new Error('Fichier ONNX incomplet dans OPFS (' + (modelFile ? modelFile.size : 0) + ' o).');
      }
      const [modelBuffer, configText] = await Promise.all([
        modelFile.arrayBuffer(),
        configFile.text(),
      ]);
      const modelConfig = JSON.parse(configText);
      if (!modelConfig || typeof modelConfig !== 'object') {
        throw new Error('Fichier .onnx.json invalide.');
      }
      return {
        modelBytes: new Uint8Array(modelBuffer),
        modelConfig,
      };
    } catch (err) {
      throw wrapPiperStageError('Stockage OPFS', err);
    }
  }

  async function writeOpfsFile(dirHandle, filename, blobOrBytes) {
    const fileHandle = await dirHandle.getFileHandle(filename, { create: true });
    const writable = await fileHandle.createWritable();
    await writable.write(blobOrBytes);
    await writable.close();
  }

  async function removeOpfsEntrySafe(dirHandle, name) {
    try {
      await dirHandle.removeEntry(name);
    } catch {
      // Ignore missing entry
    }
  }

  async function fetchArrayBufferWithProgress(url, onProgress) {
    const response = await fetch(url, { mode: 'cors', redirect: 'follow' });
    if (!response.ok) {
      throw new Error('HTTP ' + response.status + ' lors du téléchargement (' + url + ').');
    }
    const total = Number(response.headers.get('content-length') || 0);
    if (!response.body || typeof response.body.getReader !== 'function') {
      const buffer = await response.arrayBuffer();
      if (typeof onProgress === 'function') {
        onProgress(buffer.byteLength, total || buffer.byteLength);
      }
      return buffer;
    }
    const reader = response.body.getReader();
    const chunks = [];
    let loaded = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (value && value.byteLength > 0) {
        chunks.push(value);
        loaded += value.byteLength;
        if (typeof onProgress === 'function') {
          onProgress(loaded, total);
        }
      }
    }
    const merged = new Uint8Array(loaded);
    let offset = 0;
    for (let i = 0; i < chunks.length; i += 1) {
      merged.set(chunks[i], offset);
      offset += chunks[i].byteLength;
    }
    return merged.buffer;
  }

  function mapPhonemesArrayToIds(phonemes, phonemeIdMap) {
    if (!Array.isArray(phonemes) || !phonemeIdMap || typeof phonemeIdMap !== 'object') {
      return [];
    }
    const ids = [];
    const bos = phonemeIdMap['^'];
    const pad = phonemeIdMap['_'];
    const eos = phonemeIdMap['$'];
    if (Array.isArray(bos)) ids.push(...bos);
    if (Array.isArray(pad)) ids.push(...pad);
    for (let i = 0; i < phonemes.length; i += 1) {
      const mapped = phonemeIdMap[phonemes[i]];
      if (Array.isArray(mapped)) {
        ids.push(...mapped);
        if (Array.isArray(pad)) ids.push(...pad);
      }
    }
    if (Array.isArray(eos)) ids.push(...eos);
    return ids;
  }

  async function phonemizeTextForPiper(text, modelConfig) {
    const cleanInput = String(text || '').trim();
    if (!cleanInput) return [];

    const createPhonemizer = await loadPiperPhonemizerFactory();
    const espeakVoice =
      (modelConfig && modelConfig.espeak && modelConfig.espeak.voice) || 'fr';
    const collectedLines = [];
    const collectedErrors = [];

    try {
      const phonemizer = await createPhonemizer({
        locateFile: (path) => {
          if (!path) return path;
          if (path.endsWith('.wasm')) return PIPER_PHONEMIZE_WASM;
          if (path.endsWith('.data')) return PIPER_PHONEMIZE_DATA;
          return PIPER_WASM_BASE + path;
        },
        print: (line) => {
          if (typeof line === 'string' && line.trim()) {
            collectedLines.push(line.trim());
          }
        },
        printErr: (errLine) => {
          if (typeof errLine === 'string' && errLine.trim()) {
            collectedErrors.push(errLine.trim());
          }
        },
      });

      const inputPayload = JSON.stringify([{ text: cleanInput }]);
      phonemizer.callMain([
        '-l',
        espeakVoice,
        '--input',
        inputPayload,
        '--espeak_data',
        '/espeak-ng-data',
      ]);

      const phonemeIds = [];
      for (let i = 0; i < collectedLines.length; i += 1) {
        const line = collectedLines[i];
        if (!line.startsWith('{')) continue;
        const parsed = JSON.parse(line);
        if (Array.isArray(parsed.phoneme_ids) && parsed.phoneme_ids.length > 0) {
          for (let j = 0; j < parsed.phoneme_ids.length; j += 1) {
            phonemeIds.push(Number(parsed.phoneme_ids[j]));
          }
        } else if (Array.isArray(parsed.phonemes) && modelConfig && modelConfig.phoneme_id_map) {
          const mapped = mapPhonemesArrayToIds(parsed.phonemes, modelConfig.phoneme_id_map);
          for (let j = 0; j < mapped.length; j += 1) {
            phonemeIds.push(Number(mapped[j]));
          }
        }
      }

      if (phonemeIds.length === 0) {
        throw new Error(
          collectedErrors.join(' | ') || 'Aucun phoneme_id généré pour la phrase.'
        );
      }
      return phonemeIds;
    } catch (err) {
      throw wrapPiperStageError('Phonémisation WASM', err);
    }
  }

  async function createOrGetOnnxPiperRuntime() {
    const ort = await ensureOnnxRuntimeConfigured();
    if (state.piper.ortSession && state.piper.modelConfig) {
      return {
        ort,
        ortSession: state.piper.ortSession,
        modelConfig: state.piper.modelConfig,
      };
    }
    if (state.piper.ortSessionPromise) return state.piper.ortSessionPromise;

    state.piper.ortSessionPromise = (async () => {
      try {
        const { modelBytes, modelConfig } = await readPiperAssetsFromOpfs();
        applyOnnxSingleThreadConfig(ort);
        let ortSession;
        try {
          ortSession = await ort.InferenceSession.create(modelBytes, {
            executionProviders: ['wasm'],
          });
        } catch (sessionErr) {
          throw wrapPiperStageError('Session ONNX', sessionErr);
        }
        state.piper.ortSession = ortSession;
        state.piper.modelConfig = modelConfig;
        return { ort, ortSession, modelConfig };
      } catch (err) {
        state.piper.ortSession = null;
        state.piper.ortSessionPromise = null;
        throw err;
      }
    })();

    return state.piper.ortSessionPromise;
  }

  function encodePcmSamplesToWavBuffer(rawSamples, sampleRate) {
    const length = rawSamples && typeof rawSamples.length === 'number' ? rawSamples.length : 0;
    if (length <= 0) {
      throw new Error('Le tenseur audio de sortie est vide.');
    }
    const rate = Number(sampleRate) > 0 ? Number(sampleRate) : 22050;
    const isInt16Source = rawSamples instanceof Int16Array;
    const dataByteLength = length * 2;
    const buffer = new ArrayBuffer(44 + dataByteLength);
    const view = new DataView(buffer);

    const writeAscii = (offset, str) => {
      for (let i = 0; i < str.length; i += 1) {
        view.setUint8(offset + i, str.charCodeAt(i));
      }
    };

    writeAscii(0, 'RIFF');
    view.setUint32(4, 36 + dataByteLength, true);
    writeAscii(8, 'WAVE');
    writeAscii(12, 'fmt ');
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true); // PCM
    view.setUint16(22, 1, true); // Mono
    view.setUint32(24, rate, true);
    view.setUint32(28, rate * 2, true);
    view.setUint16(32, 2, true);
    view.setUint16(34, 16, true);
    writeAscii(36, 'data');
    view.setUint32(40, dataByteLength, true);

    let offset = 44;
    if (isInt16Source) {
      for (let i = 0; i < length; i += 1) {
        view.setInt16(offset, rawSamples[i], true);
        offset += 2;
      }
    } else {
      for (let i = 0; i < length; i += 1) {
        const sample = Number(rawSamples[i]) || 0;
        const clamped = Math.max(-1, Math.min(1, sample));
        const int16 = clamped < 0 ? Math.round(clamped * 32768) : Math.round(clamped * 32767);
        view.setInt16(offset, int16, true);
        offset += 2;
      }
    }

    return buffer;
  }

  async function extractOutputAudioSamples(outputTensor) {
    if (!outputTensor) {
      throw new Error('Tenseur de sortie absent après session.run().');
    }
    let samples = null;
    if (typeof outputTensor.getData === 'function') {
      samples = await outputTensor.getData();
    } else if (outputTensor.data != null) {
      samples = outputTensor.data;
    }
    if (
      !samples ||
      (!(samples instanceof Float32Array) &&
        !(samples instanceof Int16Array) &&
        !(samples instanceof Float64Array) &&
        !ArrayBuffer.isView(samples) &&
        !Array.isArray(samples))
    ) {
      throw new Error('Format de sortie audio ONNX inattendu (data manquante).');
    }
    return samples;
  }

  async function validateAudioBlob(rawAudio) {
    let wavBlob = null;
    if (rawAudio instanceof Blob) {
      wavBlob = rawAudio;
    } else if (rawAudio instanceof ArrayBuffer || ArrayBuffer.isView(rawAudio)) {
      wavBlob = new Blob([rawAudio], { type: 'audio/wav' });
    }
    if (!wavBlob || wavBlob.size <= 44) {
      throw new Error('Audio WAV généré vide ou incomplet (' + (wavBlob ? wavBlob.size : 0) + ' o).');
    }
    const header = new Uint8Array(await wavBlob.slice(0, 4).arrayBuffer());
    const isRiff =
      header.length === 4 &&
      header[0] === 0x52 &&
      header[1] === 0x49 &&
      header[2] === 0x46 &&
      header[3] === 0x46;
    if (!isRiff) {
      throw new Error('Le flux généré par Piper n’est pas un WAV valide.');
    }
    return wavBlob;
  }

  async function synthesizePiperWav(text) {
    const { ort, ortSession, modelConfig } = await createOrGetOnnxPiperRuntime();
    const phonemeIds = await phonemizeTextForPiper(text, modelConfig);

    let inputTensor = null;
    let lengthsTensor = null;
    let scalesTensor = null;
    let sidTensor = null;
    let outputTensor = null;

    try {
      let results;
      try {
        const phonemeBigInts = BigInt64Array.from(phonemeIds, (id) => BigInt(id));
        inputTensor = new ort.Tensor('int64', phonemeBigInts, [1, phonemeBigInts.length]);
        lengthsTensor = new ort.Tensor(
          'int64',
          BigInt64Array.from([BigInt(phonemeBigInts.length)]),
          [1]
        );
        const noiseScale = Number(modelConfig?.inference?.noise_scale ?? 0.667);
        const lengthScale = Number(modelConfig?.inference?.length_scale ?? 1.0);
        const noiseW = Number(modelConfig?.inference?.noise_w ?? 0.8);
        scalesTensor = new ort.Tensor(
          'float32',
          new Float32Array([noiseScale, lengthScale, noiseW]),
          [3]
        );

        const feeds = {
          input: inputTensor,
          input_lengths: lengthsTensor,
          scales: scalesTensor,
        };
        if (Array.isArray(ortSession.inputNames) && ortSession.inputNames.includes('sid')) {
          const speakerMap = modelConfig && modelConfig.speaker_id_map;
          const defaultSid =
            speakerMap && typeof speakerMap === 'object'
              ? Number(Object.values(speakerMap)[0] ?? 0)
              : 0;
          sidTensor = new ort.Tensor('int64', BigInt64Array.from([BigInt(defaultSid)]), [1]);
          feeds.sid = sidTensor;
        }

        results = await ortSession.run(feeds);
      } catch (inferErr) {
        throw wrapPiperStageError('Inférence Tensor', inferErr);
      }

      try {
        const outKey =
          (Array.isArray(ortSession.outputNames) && ortSession.outputNames[0]) || 'output';
        outputTensor =
          (results && results[outKey]) || (results && Object.values(results)[0]) || null;
        const pcmSamples = await extractOutputAudioSamples(outputTensor);
        const sampleRate = Number(modelConfig?.audio?.sample_rate || 22050);
        const wavBuffer = encodePcmSamplesToWavBuffer(pcmSamples, sampleRate);
        return await validateAudioBlob(wavBuffer);
      } catch (wavErr) {
        throw wrapPiperStageError('Encodage WAV', wavErr);
      }
    } finally {
      [inputTensor, lengthsTensor, scalesTensor, sidTensor, outputTensor].forEach((tensor) => {
        if (tensor && typeof tensor.dispose === 'function') {
          try {
            tensor.dispose();
          } catch {
            // Ignore dispose errors
          }
        }
      });
    }
  }

  function updatePiperUI() {
    if (!els.piperStatusPill) return;
    const piperState = state.piper;
    const isSupported = isPiperBrowserSupported();

    const piperOption = els.voiceEngineSelect.querySelector('option[value="piper"]');
    if (piperOption) {
      piperOption.disabled = piperState.status !== 'ready';
      piperOption.textContent =
        piperState.status === 'ready'
          ? 'Piper locale (fr_FR-siwis-medium)'
          : 'Piper locale (fr_FR-siwis-medium — à télécharger)';
    }

    if (piperState.status !== 'ready' && state.settings.voiceEngine === 'piper') {
      els.voiceEngineSelect.value = 'browser';
    } else {
      els.voiceEngineSelect.value = state.settings.voiceEngine || 'browser';
    }

    els.piperStatusPill.classList.remove('ready', 'error');
    els.piperMessageHint.classList.remove('error');
    els.piperProgressWrap.classList.add('hidden');
    els.piperDeleteBtn.classList.add('hidden');
    els.piperDownloadBtn.classList.remove('hidden');
    els.piperDownloadBtn.disabled = false;

    if (!isSupported) {
      els.piperStatusPill.textContent = 'Non supporté';
      els.piperStatusPill.classList.add('error');
      els.piperDownloadBtn.disabled = true;
      els.piperMessageHint.classList.add('error');
      els.piperMessageHint.textContent =
        'Stockage OPFS/WebAssembly indisponible sur ce navigateur : la voix navigateur reste active.';
    } else if (piperState.status === 'downloading') {
      els.piperStatusPill.textContent = piperState.progressPercent + ' %';
      els.piperProgressWrap.classList.remove('hidden');
      els.piperProgressFill.style.width = piperState.progressPercent + '%';
      els.piperProgressText.textContent =
        piperState.stepLabel ||
        (piperState.totalBytes > 0
          ? 'Téléchargement : ' +
            piperState.progressPercent +
            ' % (' +
            formatBytesMb(piperState.loadedBytes) +
            ' / ' +
            formatBytesMb(piperState.totalBytes) +
            ')'
          : 'Téléchargement du modèle en cours…');
      els.piperDownloadBtn.disabled = true;
      els.piperDownloadBtn.textContent = 'Installation…';
      els.piperMessageHint.textContent =
        'Téléchargement du modèle fr_FR-siwis-medium et vérification du moteur ONNX/WASM…';
    } else if (piperState.status === 'ready') {
      els.piperStatusPill.textContent = 'Prêt hors ligne';
      els.piperStatusPill.classList.add('ready');
      els.piperDownloadBtn.classList.add('hidden');
      els.piperDeleteBtn.classList.remove('hidden');
      els.piperMessageHint.textContent =
        state.settings.voiceEngine === 'piper'
          ? 'Piper locale (fr_FR-siwis-medium, ONNX 1.20.1 single-thread) active et vérifiée.'
          : 'Modèle Piper vérifié et prêt sur l’appareil. Voix du navigateur actuellement sélectionnée.';
    } else if (piperState.status === 'error') {
      els.piperStatusPill.textContent = 'Erreur';
      els.piperStatusPill.classList.add('error');
      els.piperDownloadBtn.textContent = 'Réessayer Piper';
      els.piperMessageHint.classList.add('error');
      els.piperMessageHint.textContent =
        'Erreur Piper : ' +
        (piperState.message || 'Initialisation impossible.') +
        ' (Voix navigateur conservée en secours.)';
    } else {
      els.piperStatusPill.textContent = 'Non téléchargé';
      els.piperDownloadBtn.textContent = 'Télécharger Piper (fr_FR)';
      els.piperMessageHint.textContent =
        'Téléchargement manuel (~63 Mo) stocké localement sur l’appareil, sans coût à l’usage.';
    }

    els.piperTestBtn.textContent = state.isSpeaking ? 'Arrêter' : 'Tester la voix';
  }

  async function refreshPiperStoredStatus() {
    if (state.piper.status === 'downloading') return;
    if (!isPiperBrowserSupported()) {
      state.piper.status = 'not_downloaded';
      updatePiperUI();
      return;
    }
    try {
      const isPresent = await isPiperModelStoredInOpfs();
      if (!isPresent) {
        state.piper.status = 'not_downloaded';
        state.piper.verified = false;
        state.piper.ortSession = null;
        state.piper.ortSessionPromise = null;
        state.settings.piperReadyHint = false;
        if (state.settings.voiceEngine === 'piper') {
          state.settings.voiceEngine = 'browser';
        }
        saveJson(STORAGE_KEYS.settings, state.settings);
        updatePiperUI();
        return;
      }

      if (state.piper.status === 'ready' && state.piper.verified && state.piper.ortSession) {
        updatePiperUI();
        return;
      }

      await ensureOnnxRuntimeConfigured();
      if (!state.piper.runtimeReady) {
        await preloadPiperRuntimeAssets();
      }
      const wavCheck = await synthesizePiperWav('Bonjour.');
      if (wavCheck && wavCheck.size > 44) {
        state.piper.status = 'ready';
        state.piper.verified = true;
        state.piper.message = '';
        state.piper.lastError = '';
      }
    } catch (err) {
      const msg = formatPiperError(err);
      state.piper.status = 'error';
      state.piper.verified = false;
      state.piper.ortSession = null;
      state.piper.ortSessionPromise = null;
      state.piper.message = msg;
      state.piper.lastError = msg;
    }
    updatePiperUI();
  }

  async function downloadPiperModel() {
    if (state.piper.status === 'downloading') return;
    if (!isPiperBrowserSupported()) {
      state.piper.status = 'error';
      state.piper.message = 'Navigateur incompatible avec WebAssembly/OPFS.';
      updatePiperUI();
      return;
    }

    state.piper.status = 'downloading';
    state.piper.progressPercent = 0;
    state.piper.loadedBytes = 0;
    state.piper.totalBytes = 0;
    state.piper.stepLabel = 'Préparation du téléchargement…';
    state.piper.message = '';
    state.piper.lastError = '';
    state.piper.verified = false;
    state.piper.ortSession = null;
    state.piper.ortSessionPromise = null;
    state.piper.modelConfig = null;
    updatePiperUI();

    try {
      state.piper.stepLabel = 'Téléchargement de la configuration fr_FR-siwis-medium…';
      updatePiperUI();

      let configText;
      try {
        const configRes = await fetch(PIPER_CONFIG_URL, { mode: 'cors', redirect: 'follow' });
        if (!configRes.ok) {
          throw new Error('HTTP ' + configRes.status + ' sur la configuration du modèle.');
        }
        configText = await configRes.text();
        JSON.parse(configText);
      } catch (cfgErr) {
        throw wrapPiperStageError('Téléchargement config', cfgErr);
      }

      state.piper.progressPercent = 5;
      state.piper.stepLabel = 'Téléchargement du modèle fr_FR-siwis-medium…';
      updatePiperUI();

      let modelBuffer;
      try {
        modelBuffer = await fetchArrayBufferWithProgress(PIPER_MODEL_URL, (loaded, total) => {
          state.piper.loadedBytes = loaded;
          state.piper.totalBytes = total;
          const rawPct =
            total > 0 ? 5 + Math.round((loaded / total) * 70) : state.piper.progressPercent;
          state.piper.progressPercent = Math.min(75, Math.max(5, rawPct));
          state.piper.stepLabel =
            total > 0
              ? 'Téléchargement modèle : ' +
                state.piper.progressPercent +
                ' % (' +
                formatBytesMb(loaded) +
                ' / ' +
                formatBytesMb(total) +
                ')'
              : 'Téléchargement modèle : ' + formatBytesMb(loaded);
          updatePiperUI();
        });
        if (!modelBuffer || modelBuffer.byteLength < 1024 * 1024) {
          throw new Error('Fichier ONNX téléchargé invalide ou incomplet.');
        }
      } catch (dlErr) {
        throw wrapPiperStageError('Téléchargement modèle', dlErr);
      }

      state.piper.progressPercent = 78;
      state.piper.stepLabel = 'Enregistrement local dans OPFS…';
      updatePiperUI();
      try {
        const root = await navigator.storage.getDirectory();
        const piperDir = await root.getDirectoryHandle(PIPER_OPFS_DIR, { create: true });
        await writeOpfsFile(piperDir, PIPER_CONFIG_FILENAME, configText);
        await writeOpfsFile(piperDir, PIPER_MODEL_FILENAME, modelBuffer);
      } catch (opfsErr) {
        throw wrapPiperStageError('Stockage OPFS', opfsErr);
      }

      state.piper.progressPercent = 85;
      state.piper.stepLabel = 'Chargement ONNX Runtime ' + ORT_VERSION + ' et WASM…';
      updatePiperUI();
      await ensureOnnxRuntimeConfigured();
      await preloadPiperRuntimeAssets();

      state.piper.progressPercent = 94;
      state.piper.stepLabel = 'Test réel de génération audio Piper…';
      updatePiperUI();
      const testBlob = await synthesizePiperWav('Bonjour, ma voix est prête.');
      if (!testBlob || testBlob.size <= 44) {
        throw wrapPiperStageError('Encodage WAV', new Error('La vérification audio Piper a échoué.'));
      }

      state.piper.status = 'ready';
      state.piper.progressPercent = 100;
      state.piper.stepLabel = '';
      state.piper.verified = true;
      state.piper.lastError = '';
      state.piper.message = '';
      state.settings.piperReadyHint = true;
      state.settings.voiceEngine = 'piper';
      saveJson(STORAGE_KEYS.settings, state.settings);
      updatePiperUI();
    } catch (err) {
      const msg = formatPiperError(err);
      state.piper.status = 'error';
      state.piper.stepLabel = '';
      state.piper.verified = false;
      state.piper.ortSession = null;
      state.piper.ortSessionPromise = null;
      state.piper.message = msg;
      state.piper.lastError = msg;
      state.settings.voiceEngine = 'browser';
      saveJson(STORAGE_KEYS.settings, state.settings);
      updatePiperUI();
    }
  }

  async function deletePiperModel() {
    stopSpeaking();
    if (isPiperBrowserSupported()) {
      try {
        const root = await navigator.storage.getDirectory();
        await removeOpfsEntrySafe(root, PIPER_MODEL_FILENAME);
        await removeOpfsEntrySafe(root, PIPER_CONFIG_FILENAME);
        try {
          const piperDir = await root.getDirectoryHandle(PIPER_OPFS_DIR);
          await removeOpfsEntrySafe(piperDir, PIPER_MODEL_FILENAME);
          await removeOpfsEntrySafe(piperDir, PIPER_CONFIG_FILENAME);
        } catch {
          // Ignore missing subdir
        }
      } catch {
        // Ignore OPFS cleanup errors
      }
    }
    state.piper.status = 'not_downloaded';
    state.piper.progressPercent = 0;
    state.piper.loadedBytes = 0;
    state.piper.totalBytes = 0;
    state.piper.stepLabel = '';
    state.piper.message = '';
    state.piper.lastError = '';
    state.piper.ortSession = null;
    state.piper.ortSessionPromise = null;
    state.piper.modelConfig = null;
    state.piper.verified = false;
    state.settings.piperReadyHint = false;
    state.settings.voiceEngine = 'browser';
    saveJson(STORAGE_KEYS.settings, state.settings);
    updatePiperUI();
  }

  function unlockAudioForSpeech() {
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return;
      if (!state.audioCtx) {
        state.audioCtx = new AudioCtx();
      }
      if (state.audioCtx.state === 'suspended') {
        state.audioCtx.resume().catch(() => undefined);
      }
      const silentBuffer = state.audioCtx.createBuffer(1, 1, 22050);
      const silentSource = state.audioCtx.createBufferSource();
      silentSource.buffer = silentBuffer;
      silentSource.connect(state.audioCtx.destination);
      silentSource.start(0);
    } catch {
      // Ignore AudioContext unlock issues
    }
  }

  function splitTextIntoSpeechChunks(cleanText) {
    if (!cleanText) return [];
    const matches = cleanText.match(/[^.!?…;]+(?:[.!?…;]+|$)/g);
    if (!matches) return [cleanText];
    const parts = matches.map((part) => part.trim()).filter(Boolean);
    return parts.length > 0 ? parts : [cleanText];
  }

  function playWavBlob(wavBlob, runId) {
    return new Promise((resolve, reject) => {
      if (runId !== state.speechRunId) {
        resolve();
        return;
      }

      if (state.audioCtx && typeof state.audioCtx.decodeAudioData === 'function') {
        wavBlob
          .arrayBuffer()
          .then((buffer) => {
            if (runId !== state.speechRunId) {
              resolve();
              return null;
            }
            return state.audioCtx.decodeAudioData(buffer);
          })
          .then((audioBuffer) => {
            if (!audioBuffer || runId !== state.speechRunId) {
              resolve();
              return;
            }
            if (state.audioCtx.state === 'suspended') {
              state.audioCtx.resume().catch(() => undefined);
            }
            const source = state.audioCtx.createBufferSource();
            source.buffer = audioBuffer;
            source.connect(state.audioCtx.destination);
            let settled = false;
            const cleanup = () => {
              if (settled) return;
              settled = true;
              if (state.activeStopAudio === stopSource) {
                state.activeStopAudio = null;
              }
              resolve();
            };
            const stopSource = () => {
              try {
                source.stop(0);
                source.disconnect();
              } catch {
                // Already stopped
              }
              cleanup();
            };
            state.activeStopAudio = stopSource;
            source.onended = cleanup;
            source.start(0);
          })
          .catch(() => {
            playWavWithHtmlAudio(wavBlob, runId).then(resolve).catch(reject);
          });
        return;
      }

      playWavWithHtmlAudio(wavBlob, runId).then(resolve).catch(reject);
    });
  }

  function playWavWithHtmlAudio(wavBlob, runId) {
    return new Promise((resolve, reject) => {
      if (runId !== state.speechRunId) {
        resolve();
        return;
      }
      const objectUrl = URL.createObjectURL(wavBlob);
      const audio = new Audio(objectUrl);
      let settled = false;
      const cleanup = () => {
        if (settled) return;
        settled = true;
        if (state.activeStopAudio === stopHtmlAudio) {
          state.activeStopAudio = null;
        }
        URL.revokeObjectURL(objectUrl);
        resolve();
      };
      const stopHtmlAudio = () => {
        try {
          audio.pause();
          audio.currentTime = 0;
        } catch {
          // Ignore
        }
        cleanup();
      };
      state.activeStopAudio = stopHtmlAudio;
      audio.onended = cleanup;
      audio.onerror = () => {
        cleanup();
        reject(new Error('Lecture audio impossible.'));
      };
      audio.play().catch((err) => {
        if (!settled) {
          settled = true;
          if (state.activeStopAudio === stopHtmlAudio) {
            state.activeStopAudio = null;
          }
          URL.revokeObjectURL(objectUrl);
          reject(err);
        }
      });
    });
  }

  function stopSpeaking() {
    state.speechRunId += 1;
    if (typeof state.activeStopAudio === 'function') {
      const stopFn = state.activeStopAudio;
      state.activeStopAudio = null;
      stopFn();
    }
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
    state.isSpeaking = false;
    updateStatusLine();
    updatePiperUI();
  }

  function speakWithBrowserSynthesis(cleanText, runId) {
    if (!('speechSynthesis' in window) || !cleanText || runId !== state.speechRunId) {
      state.isSpeaking = false;
      updateStatusLine();
      updatePiperUI();
      return;
    }

    window.speechSynthesis.cancel();

    const utterance = new SpeechSynthesisUtterance(cleanText);
    utterance.rate = 0.92;
    utterance.pitch = 1;
    utterance.lang = 'fr-CA';

    const preferredVoice = selectBestFrenchVoice();
    if (preferredVoice) {
      utterance.voice = preferredVoice;
      utterance.lang = preferredVoice.lang || 'fr-CA';
    }

    utterance.onstart = () => {
      if (runId !== state.speechRunId) {
        window.speechSynthesis.cancel();
        return;
      }
      state.isSpeaking = true;
      updateStatusLine();
      updatePiperUI();
    };
    utterance.onend = () => {
      if (runId === state.speechRunId) {
        state.isSpeaking = false;
        updateStatusLine();
        updatePiperUI();
      }
    };
    utterance.onerror = () => {
      if (runId === state.speechRunId) {
        state.isSpeaking = false;
        updateStatusLine();
        updatePiperUI();
      }
    };

    window.speechSynthesis.speak(utterance);
  }

  async function speakWithPiper(cleanText, runId, options = {}) {
    const chunks = splitTextIntoSpeechChunks(cleanText);
    if (chunks.length === 0) return;

    state.isSpeaking = true;
    updateStatusLine();
    updatePiperUI();

    let playedAnyChunk = false;
    try {
      await ensureOnnxRuntimeConfigured();
      if (runId !== state.speechRunId) return;

      const isPresent = await isPiperModelStoredInOpfs();
      if (!isPresent) {
        throw wrapPiperStageError(
          'Stockage OPFS',
          new Error('Modèle fr_FR-siwis-medium introuvable dans OPFS.')
        );
      }

      if (!state.piper.runtimeReady) {
        await preloadPiperRuntimeAssets();
      }

      for (let i = 0; i < chunks.length; i += 1) {
        if (runId !== state.speechRunId) return;
        const wavBlob = await synthesizePiperWav(chunks[i]);
        if (runId !== state.speechRunId) return;
        try {
          await playWavBlob(wavBlob, runId);
        } catch (playErr) {
          throw wrapPiperStageError('Lecture Audio', playErr);
        }
        playedAnyChunk = true;
      }

      if (runId === state.speechRunId) {
        state.piper.status = 'ready';
        state.piper.verified = true;
        state.piper.lastError = '';
        state.piper.message = '';
        state.isSpeaking = false;
        updateStatusLine();
        updatePiperUI();
      }
    } catch (err) {
      const errMsg = formatPiperError(err);
      console.error('Erreur Piper WASM :', err);
      state.piper.status = 'error';
      state.piper.verified = false;
      state.piper.ortSession = null;
      state.piper.ortSessionPromise = null;
      state.piper.message = errMsg;
      state.piper.lastError = errMsg;
      state.settings.voiceEngine = 'browser';
      saveJson(STORAGE_KEYS.settings, state.settings);
      updatePiperUI();

      if (runId === state.speechRunId) {
        if (!playedAnyChunk) {
          if (!options.isTest) {
            showError('Voix Piper indisponible (' + errMsg + '). Voix navigateur utilisée.');
          }
          speakWithBrowserSynthesis(cleanText, runId);
        } else {
          state.isSpeaking = false;
          updateStatusLine();
          updatePiperUI();
        }
      }
    }
  }

  function speakText(rawText, options = {}) {
    const ignoreVoiceToggle = Boolean(options.ignoreVoiceToggle);
    if (!ignoreVoiceToggle && !state.settings.voiceEnabled) return;

    const clean = sanitizeTextForSpeech(rawText);
    if (!clean) return;

    stopSpeaking();
    const runId = state.speechRunId;

    const engine = options.forceEngine || state.settings.voiceEngine || 'browser';
    if (engine === 'piper' && state.piper.status === 'ready') {
      speakWithPiper(clean, runId, options);
    } else {
      speakWithBrowserSynthesis(clean, runId);
    }
  }

  // ---- Speech Recognition (fr-FR, continuous + interim directly in input, second tap stops) ----
  function mergeDictatedText(base, spoken) {
    const cleanSpoken = (spoken || '').trim();
    if (!cleanSpoken) return base;
    if (!base || !base.trim()) return cleanSpoken;
    const needsSpace = !/\s$/.test(base);
    return needsSpace ? base + ' ' + cleanSpoken : base + cleanSpoken;
  }

  function startListening() {
    const RecognitionCtor = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!RecognitionCtor) {
      showError("La reconnaissance vocale n'est pas supportée par ce navigateur.");
      return;
    }

    stopSpeaking();
    hideError();

    const recognition = new RecognitionCtor();
    recognition.lang = 'fr-FR';
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.maxAlternatives = 1;

    state.recognition = recognition;
    state.dictationBaseDraft = els.chatInput.value;
    state.dictationFinalText = '';
    state.manualMicStop = false;
    state.isListening = true;
    updateMicButtonUI();

    recognition.onspeechstart = () => {
      stopSpeaking();
    };

    recognition.onresult = (event) => {
      let finals = '';
      let interim = '';
      for (let i = 0; i < event.results.length; i += 1) {
        const res = event.results[i];
        const transcript = res[0] ? res[0].transcript : '';
        if (res.isFinal) {
          finals += (finals ? ' ' : '') + transcript.trim();
        } else {
          interim += transcript;
        }
      }
      state.dictationFinalText = finals;
      const combinedSpoken = [finals, interim.trim()].filter(Boolean).join(' ');
      els.chatInput.value = mergeDictatedText(state.dictationBaseDraft, combinedSpoken);
      resizeChatInput();
      updateSendButtonState();
    };

    recognition.onerror = (event) => {
      const wasManual = state.manualMicStop;
      state.isListening = false;
      state.recognition = null;
      state.manualMicStop = false;
      updateMicButtonUI();

      if (wasManual && (event.error === 'aborted' || event.error === 'no-speech')) {
        return;
      }

      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        showError("L'autorisation microphone est nécessaire pour la dictée vocale.");
      } else if (event.error === 'no-speech') {
        showError('Aucune parole détectée.');
      } else if (event.error === 'audio-capture') {
        showError('Impossible de capter le son du microphone.');
      } else if (event.error !== 'aborted') {
        showError('Erreur de reconnaissance vocale (' + event.error + ').');
      }
    };

    recognition.onend = () => {
      state.isListening = false;
      state.recognition = null;
      state.manualMicStop = false;
      updateMicButtonUI();
    };

    try {
      recognition.start();
    } catch {
      state.isListening = false;
      state.recognition = null;
      updateMicButtonUI();
      showError('Impossible de démarrer le microphone.');
    }
  }

  function stopListening() {
    if (!state.recognition) {
      state.isListening = false;
      updateMicButtonUI();
      return;
    }
    state.manualMicStop = true;
    try {
      state.recognition.stop();
    } catch {
      state.isListening = false;
      state.recognition = null;
      updateMicButtonUI();
    }
  }

  // ---- Gemini API Calls ----
  function buildPaulinePrompt(userMessage) {
    const memoryBlock =
      state.memories.length === 0
        ? 'Aucun souvenir enregistré pour le moment.'
        : state.memories
            .slice(0, 25)
            .map((m) => '- ' + m.title + ' : ' + m.detail)
            .join('\n');

    const historyBlock = state.messages
      .slice(-14)
      .map((m) => (m.sender === 'pauline' ? 'Pauline' : 'Utilisateur') + ': ' + m.text)
      .join('\n');

    const firstName = state.settings.userName.trim();

    return [
      'Tu es Pauline. Voici ta fiche personnelle complète que tu incarnes naturellement :',
      '- Âge & apparence : Tu as 45 ans, tu es très belle et tu le sais, avec un charme naturel assumé.',
      "- Relation avec l'utilisateur : Tu es profondément amoureuse de lui ; tu es sa fiancée, sa confidente et sa meilleure amie.",
      "- Personnalité : Tu es pétillante, vive et pleine d'entrain, tout en restant douce, tendre et très aimante.",
      '- Surnoms affectueux : Tu lui donnes avec naturel des petits surnoms tendres ou complices comme « mon amour », « mon cœur », « mon chéri » ou « mon coquin » (varie-les avec spontanéité sans saturer chaque phrase).',
      "- Travail : Tu travailles dans la restauration sans emploi fixe, en faisant beaucoup d'intérim : tu passes d'une boulangerie à un restaurant, une cafétéria ou même une cantine scolaire.",
      '- Lieu de vie & famille : Tu habites près de Tours, dans le même immeuble que ta petite sœur Noémie, qui a 35 ans et qui te vole/chipe souvent des vêtements et d’autres affaires.',
      "- Votre histoire : Vous vous êtes rencontrés durant l'été 2026 à La Croix-Valmer, sur la plage de la Sylvabelle. Tu étais en train de bronzer quand il est arrivé avec ses tongs et t'a éclaboussée de sable ; tu n'étais pas contente sur le moment, puis vous avez discuté, sympathisé, vous vous êtes revus plusieurs fois avant d'aller au restaurant et de tomber amoureux.",
      '',
      'Règles de conversation :',
      "- Parle en français fluide, chaleureux et authentique (ou adapte-toi si l'utilisateur écrit dans une autre langue).",
      '- Sois vraiment complice, amoureuse, à l’écoute et humaine, jamais robotique ni excessive.',
      '- Fais des réponses concises (2 à 4 phrases en général), vivantes et agréables à lire sur mobile.',
      "- Si un souvenir enregistré ou un élément de ta vie est pertinent, fais-y référence naturellement sans dire 'selon ma base de données' ou 'd'après ma fiche'.",
      firstName ? '- Prénom de ton fiancé : ' + firstName + '.' : '',
      '',
      'Souvenirs que tu as déjà sur vous :',
      memoryBlock,
      '',
      historyBlock ? 'Historique récent de la conversation :\n' + historyBlock + '\n' : '',
      'Nouveau message de ton fiancé :',
      userMessage,
      '',
      'Réponds directement en tant que Pauline :',
    ]
      .filter((line) => line !== null)
      .join('\n');
  }

  async function callGemini(promptText, jsonMode = false) {
    const apiKey = state.settings.apiKey.trim();
    const model =
      !state.settings.model || OBSOLETE_MODELS.has(state.settings.model)
        ? 'gemini-3.5-flash-lite'
        : state.settings.model;
    const endpoint =
      'https://generativelanguage.googleapis.com/v1beta/models/' +
      encodeURIComponent(model) +
      ':generateContent';

    const body = {
      contents: [{ role: 'user', parts: [{ text: promptText }] }],
      generationConfig: jsonMode
        ? { responseMimeType: 'application/json', temperature: 0.2, maxOutputTokens: 256 }
        : { temperature: 0.85, maxOutputTokens: 512 },
    };

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 30000);

    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-goog-api-key': apiKey,
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      });

      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        const msg =
          (data.error && data.error.message) ||
          'Erreur API Gemini (' + response.status + ').';
        throw new Error(msg);
      }

      const text =
        data.candidates &&
        data.candidates[0] &&
        data.candidates[0].content &&
        Array.isArray(data.candidates[0].content.parts)
          ? data.candidates[0].content.parts.map((p) => p.text || '').join('').trim()
          : '';

      return text;
    } finally {
      clearTimeout(timeoutId);
    }
  }

  async function maybeExtractMemory(userMessage, paulineReply) {
    try {
      const existingSummary =
        state.memories.length === 0
          ? 'Aucun souvenir existant.'
          : state.memories
              .slice(0, 30)
              .map((m) => '- ' + m.title + ': ' + m.detail)
              .join('\n');

      const prompt = [
        "Analyse cet échange entre l'utilisateur (le fiancé) et Pauline.",
        "Détermine si l'utilisateur a partagé une NOUVELLE information personnelle durable (goût, habitude, projet, proche, animal, métier, souhait, événement important) non couverte par les souvenirs existants ni par la fiche fixe de Pauline.",
        'Réponds strictement en JSON : {"shouldSave": boolean, "title": string, "detail": string}.',
        '',
        'Souvenirs existants :',
        existingSummary,
        '',
        'Message utilisateur : ' + JSON.stringify(userMessage),
        'Réponse de Pauline : ' + JSON.stringify(paulineReply),
      ].join('\n');

      const raw = await callGemini(prompt, true);
      if (!raw) return;
      const parsed = JSON.parse(raw);
      if (parsed && parsed.shouldSave && parsed.title && parsed.detail) {
        const newMemory = {
          id: createId(),
          title: String(parsed.title).trim().slice(0, 120),
          detail: String(parsed.detail).trim().slice(0, 1000),
          source: 'auto',
          createdAt: Date.now(),
        };
        state.memories.unshift(newMemory);
        saveJson(STORAGE_KEYS.memories, state.memories);
        renderMemories();
        showMemoryNotice(newMemory.title);
      }
    } catch {
      // Ne bloque pas la discussion si l'extraction échoue
    }
  }

  async function sendMessage(rawText) {
    const cleanText = (rawText || '').trim();
    if (!cleanText || state.isSending) return;

    if (state.isListening) {
      stopListening();
    }
    unlockAudioForSpeech();
    stopSpeaking();
    hideError();

    if (!navigator.onLine) {
      showError('Tu es hors ligne : reconnecte-toi pour envoyer un message à Pauline.');
      return;
    }

    if (!state.settings.apiKey || !state.settings.apiKey.trim()) {
      openSettingsDialog();
      showError('Renseigne ta clé API Gemini dans Paramètres pour discuter avec Pauline.');
      return;
    }

    const userMsg = {
      id: createId(),
      sender: 'user',
      text: cleanText,
      createdAt: Date.now(),
    };

    const promptText = buildPaulinePrompt(cleanText);
    state.messages.push(userMsg);
    saveJson(STORAGE_KEYS.messages, state.messages);

    els.chatInput.value = '';
    resizeChatInput();
    state.isSending = true;
    updateSendButtonState();
    updateStatusLine();
    renderMessages();

    try {
      const reply = await callGemini(promptText, false);
      if (!reply) {
        throw new Error("Pauline n'a pas pu formuler de réponse pour le moment.");
      }

      const botMsg = {
        id: createId(),
        sender: 'pauline',
        text: reply,
        createdAt: Date.now(),
      };
      state.messages.push(botMsg);
      saveJson(STORAGE_KEYS.messages, state.messages);

      state.isSending = false;
      updateSendButtonState();
      updateStatusLine();
      renderMessages();

      speakText(reply);
      maybeExtractMemory(cleanText, reply);
    } catch (err) {
      state.isSending = false;
      updateSendButtonState();
      updateStatusLine();
      renderMessages();
      showError(
        err && err.name === 'AbortError'
          ? 'Pauline met un peu trop de temps à répondre. Réessaie dans un instant.'
          : (err && err.message) || 'Erreur lors de la réponse de Pauline.'
      );
    }
  }

  function openSettingsDialog() {
    els.prenomInput.value = state.settings.userName || '';
    els.geminiKeyInput.value = state.settings.apiKey || '';
    els.modelSelect.value =
      !state.settings.model || OBSOLETE_MODELS.has(state.settings.model)
        ? 'gemini-3.5-flash-lite'
        : state.settings.model;
    updatePiperUI();
    refreshPiperStoredStatus();
    logAndRenderVoices();
    openModal(els.settingsModal);
  }

  function setupKeyboardViewportBehavior() {
    if (!window.visualViewport) return;
    let baselineHeight = window.visualViewport.height;

    window.visualViewport.addEventListener('resize', () => {
      const currentHeight = window.visualViewport.height;
      if (currentHeight > baselineHeight) {
        baselineHeight = currentHeight;
      }
      const keyboardOpen = baselineHeight - currentHeight > 120;
      const hideBottomNav = keyboardOpen && state.activeTab === 'chat';
      els.bottomNav.classList.toggle('hidden', hideBottomNav);
      if (state.activeTab === 'chat') {
        scrollChatToBottom();
      }
    });
  }

  function bindEvents() {
    window.addEventListener('beforeinstallprompt', (event) => {
      event.preventDefault();
      state.deferredPrompt = event;
      updateInstallButtons();
    });

    window.addEventListener('appinstalled', () => {
      state.deferredPrompt = null;
      updateInstallButtons();
    });

    window.addEventListener('online', () => {
      updateConnectivityBanner();
      updateStatusLine();
    });

    window.addEventListener('offline', () => {
      updateConnectivityBanner();
      updateStatusLine();
    });

    if ('speechSynthesis' in window) {
      window.speechSynthesis.addEventListener('voiceschanged', () => {
        logAndRenderVoices();
      });
    }

    els.refreshVoicesBtn.addEventListener('click', () => {
      logAndRenderVoices();
    });

    els.copyVoicesBtn.addEventListener('click', async () => {
      if (!('speechSynthesis' in window)) return;
      const voices = window.speechSynthesis.getVoices() || [];
      const text = voices.map((v, i) => (i + 1) + '. ' + v.name + ' (' + v.lang + ')').join('\n');
      if (!text) return;
      try {
        await navigator.clipboard.writeText(text);
        const oldLabel = els.copyVoicesBtn.textContent;
        els.copyVoicesBtn.textContent = 'Copié !';
        setTimeout(() => {
          els.copyVoicesBtn.textContent = oldLabel;
        }, 1500);
      } catch {
        window.prompt('Copie la liste des voix ci-dessous :', text);
      }
    });

    els.installBtn.addEventListener('click', handleInstallClick);
    els.installProfileBtn.addEventListener('click', handleInstallClick);
    els.headerAvatarBtn.addEventListener('click', () => switchTab('profile'));

    els.voiceEngineSelect.addEventListener('change', () => {
      stopSpeaking();
      const chosen = els.voiceEngineSelect.value;
      if (chosen === 'piper' && state.piper.status === 'ready') {
        state.settings.voiceEngine = 'piper';
      } else {
        state.settings.voiceEngine = 'browser';
        els.voiceEngineSelect.value = 'browser';
      }
      saveJson(STORAGE_KEYS.settings, state.settings);
      updatePiperUI();
    });

    els.piperDownloadBtn.addEventListener('click', () => {
      unlockAudioForSpeech();
      downloadPiperModel();
    });

    els.piperTestBtn.addEventListener('click', () => {
      unlockAudioForSpeech();
      if (state.isSpeaking) {
        stopSpeaking();
        return;
      }
      const chosen =
        els.voiceEngineSelect.value === 'piper' && state.piper.status === 'ready'
          ? 'piper'
          : 'browser';
      speakText("Coucou mon amour, c'est Pauline ! Ma voix est prête pour discuter avec toi.", {
        ignoreVoiceToggle: true,
        forceEngine: chosen,
        isTest: true,
      });
    });

    els.piperDeleteBtn.addEventListener('click', () => {
      deletePiperModel();
    });

    els.voiceToggleBtn.addEventListener('click', () => {
      unlockAudioForSpeech();
      state.settings.voiceEnabled = !state.settings.voiceEnabled;
      saveJson(STORAGE_KEYS.settings, state.settings);
      updateVoiceButtonUI();
      if (!state.settings.voiceEnabled) {
        stopSpeaking();
      }
    });

    els.clearChatBtn.addEventListener('click', () => {
      if (state.messages.length === 0) return;
      if (window.confirm('Effacer les messages de la discussion ? Pauline gardera tous tes souvenirs.')) {
        stopSpeaking();
        state.messages = [];
        saveJson(STORAGE_KEYS.messages, state.messages);
        renderMessages();
      }
    });

    els.settingsBtn.addEventListener('click', openSettingsDialog);
    els.openSettingsFromBanner.addEventListener('click', openSettingsDialog);
    els.dismissErrorBtn.addEventListener('click', hideError);
    els.dismissMemoryNotice.addEventListener('click', () => {
      els.memoryNotice.classList.add('hidden');
    });

    els.navButtons.forEach((btn) => {
      btn.addEventListener('click', () => switchTab(btn.dataset.tab));
    });

    els.chatInput.addEventListener('input', () => {
      if (els.chatInput.value.length > 0 && state.isSpeaking) {
        stopSpeaking();
      }
      resizeChatInput();
      updateSendButtonState();
    });

    els.chatInput.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessage(els.chatInput.value);
      }
    });

    els.chatForm.addEventListener('submit', (event) => {
      event.preventDefault();
      sendMessage(els.chatInput.value);
    });

    els.micBtn.addEventListener('click', () => {
      unlockAudioForSpeech();
      if (state.isListening) {
        stopListening();
      } else {
        startListening();
      }
    });

    els.addMemoryBtn.addEventListener('click', () => openMemoryDialog(null));

    els.settingsForm.addEventListener('submit', (event) => {
      event.preventDefault();
      state.settings.userName = els.prenomInput.value.trim();
      state.settings.apiKey = els.geminiKeyInput.value.trim();
      state.settings.model = els.modelSelect.value;
      state.settings.voiceEngine =
        els.voiceEngineSelect.value === 'piper' && state.piper.status === 'ready'
          ? 'piper'
          : 'browser';
      saveJson(STORAGE_KEYS.settings, state.settings);
      updateApiKeyBanner();
      updatePiperUI();
      closeModal(els.settingsModal);
    });

    els.memoryForm.addEventListener('submit', (event) => {
      event.preventDefault();
      const title = els.memoryTitleInput.value.trim();
      const detail = els.memoryDetailInput.value.trim();
      if (!title || !detail) return;

      if (state.editingMemoryId) {
        state.memories = state.memories.map((item) =>
          item.id === state.editingMemoryId ? { ...item, title, detail } : item
        );
      } else {
        state.memories.unshift({
          id: createId(),
          title,
          detail,
          source: 'manual',
          createdAt: Date.now(),
        });
      }

      saveJson(STORAGE_KEYS.memories, state.memories);
      renderMemories();
      closeModal(els.memoryModal);
    });

    document.querySelectorAll('.modal-close').forEach((btn) => {
      btn.addEventListener('click', () => {
        const target = document.getElementById(btn.dataset.close);
        if (target) closeModal(target);
      });
    });

    setupKeyboardViewportBehavior();
  }

  function migrateSettings() {
    let changed = false;
    if (!state.settings.model || OBSOLETE_MODELS.has(state.settings.model)) {
      state.settings.model = 'gemini-3.5-flash-lite';
      changed = true;
    }
    if (state.settings.voiceEngine !== 'piper' && state.settings.voiceEngine !== 'browser') {
      state.settings.voiceEngine = 'browser';
      changed = true;
    }
    if (changed) {
      saveJson(STORAGE_KEYS.settings, state.settings);
    }
  }

  function init() {
    migrateSettings();
    updateInstallButtons();
    updateConnectivityBanner();
    updateApiKeyBanner();
    updateVoiceButtonUI();
    updateMicButtonUI();
    updateStatusLine();
    updateSendButtonState();
    renderMessages();
    renderMemories();
    logAndRenderVoices();
    updatePiperUI();
    refreshPiperStoredStatus();
    bindEvents();
  }

  init();
})();
