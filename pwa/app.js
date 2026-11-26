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
    model: 'gemini-2.5-flash-lite',
    voiceEnabled: true,
  };

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
    editingMemoryId: null,
    deferredPrompt: null,
    recognition: null,
    dictationBaseDraft: '',
    dictationFinalText: '',
    manualMicStop: false,
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

  // ---- Speech Synthesis (French female voice + strip emojis/symbols) ----
  function sanitizeTextForSpeech(rawText) {
    if (!rawText) return '';
    return rawText
      .replace(/[\p{Extended_Pictographic}\uFE0F\u200D]/gu, ' ')
      .replace(/[^\p{L}\p{N}\s.,!?:;'’\-()…]/gu, ' ')
      .replace(/\s+([,.])/g, '$1')
      .replace(/\s+/g, ' ')
      .trim();
  }

  function scoreFrenchVoice(voice) {
    const lang = (voice.lang || '').toLowerCase();
    if (!lang.startsWith('fr')) return -1;

    const name = (voice.name || '').toLowerCase();
    const isFrFr = lang === 'fr-fr' || lang.startsWith('fr_fr') || lang.startsWith('fr-fr');
    const hasFemale = FEMALE_VOICE_HINTS.some((hint) => name.includes(hint));
    const hasMale = !hasFemale && MALE_VOICE_HINTS.some((hint) => name.includes(hint));

    let score = isFrFr ? 10000 : 5000;
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
    let bestVoice = null;
    let bestScore = -1;
    voices.forEach((voice) => {
      const s = scoreFrenchVoice(voice);
      if (s > bestScore) {
        bestScore = s;
        bestVoice = voice;
      }
    });
    return bestVoice;
  }

  function stopSpeaking() {
    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel();
    }
    state.isSpeaking = false;
    updateStatusLine();
  }

  function speakText(rawText) {
    if (!state.settings.voiceEnabled || !('speechSynthesis' in window)) return;
    const clean = sanitizeTextForSpeech(rawText);
    if (!clean) return;

    stopSpeaking();

    const utterance = new SpeechSynthesisUtterance(clean);
    utterance.lang = 'fr-FR';
    const preferredVoice = selectBestFrenchVoice();
    if (preferredVoice) {
      utterance.voice = preferredVoice;
      utterance.lang = preferredVoice.lang || 'fr-FR';
    }

    utterance.onstart = () => {
      state.isSpeaking = true;
      updateStatusLine();
    };
    utterance.onend = () => {
      state.isSpeaking = false;
      updateStatusLine();
    };
    utterance.onerror = () => {
      state.isSpeaking = false;
      updateStatusLine();
    };

    window.speechSynthesis.speak(utterance);
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
    const model = state.settings.model || 'gemini-2.5-flash-lite';
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
    els.modelSelect.value = state.settings.model || 'gemini-2.5-flash-lite';
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
        selectBestFrenchVoice();
      });
    }

    els.installBtn.addEventListener('click', handleInstallClick);
    els.installProfileBtn.addEventListener('click', handleInstallClick);
    els.headerAvatarBtn.addEventListener('click', () => switchTab('profile'));

    els.voiceToggleBtn.addEventListener('click', () => {
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
      saveJson(STORAGE_KEYS.settings, state.settings);
      updateApiKeyBanner();
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

  function init() {
    updateInstallButtons();
    updateConnectivityBanner();
    updateApiKeyBanner();
    updateVoiceButtonUI();
    updateMicButtonUI();
    updateStatusLine();
    updateSendButtonState();
    renderMessages();
    renderMemories();
    bindEvents();
  }

  init();
})();
