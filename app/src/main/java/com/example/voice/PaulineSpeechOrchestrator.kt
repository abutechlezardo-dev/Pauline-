package com.example.voice

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class VoiceEnginePreference {
  PIPER_LOCAL,
  ANDROID_SYSTEM,
}

data class PaulineVoiceSettingsState(
  val enginePreference: VoiceEnginePreference = VoiceEnginePreference.PIPER_LOCAL,
  val piperModelState: PiperModelState = PiperModelState.NotDownloaded,
  val usingFallbackRightNow: Boolean = false,
) {
  val isPiperReady: Boolean
    get() = piperModelState is PiperModelState.Ready

  val isUsingPiperEffective: Boolean
    get() =
      enginePreference == VoiceEnginePreference.PIPER_LOCAL &&
        isPiperReady &&
        !usingFallbackRightNow
}

internal fun resolveEffectiveVoiceEngine(
  preference: VoiceEnginePreference,
  isPiperModelReady: Boolean,
): VoiceEnginePreference =
  if (preference == VoiceEnginePreference.PIPER_LOCAL && isPiperModelReady) {
    VoiceEnginePreference.PIPER_LOCAL
  } else {
    VoiceEnginePreference.ANDROID_SYSTEM
  }

class PaulineSpeechOrchestrator(
  context: Context,
  private val onSpeakingChanged: (Boolean) -> Unit,
) {
  private val appContext = context.applicationContext
  private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val modelManager = PiperModelManager(appContext)
  private val piperSpeaking = MutableStateFlow(false)
  private val androidSpeaking = MutableStateFlow(false)
  private val usingFallbackNotice = MutableStateFlow(false)

  private val enginePreference =
    MutableStateFlow(readSavedPreference())

  private val androidTtsController =
    PaulineTextToSpeechController(
      context = appContext,
      onSpeakingChanged = { speaking ->
        androidSpeaking.value = speaking
        emitCombinedSpeaking()
      },
    )

  private val piperTtsEngine =
    PiperTtsEngine(
      context = appContext,
      modelManager = modelManager,
      onSpeakingChanged = { speaking ->
        piperSpeaking.value = speaking
        emitCombinedSpeaking()
      },
    )

  val voiceSettingsState: StateFlow<PaulineVoiceSettingsState> =
    combine(
      enginePreference,
      modelManager.state,
      usingFallbackNotice,
    ) { pref, modelState, fallbackNotice ->
      PaulineVoiceSettingsState(
        enginePreference = pref,
        piperModelState = modelState,
        usingFallbackRightNow = fallbackNotice,
      )
    }.stateIn(
      scope = scope,
      started = SharingStarted.Eagerly,
      initialValue =
        PaulineVoiceSettingsState(
          enginePreference = enginePreference.value,
          piperModelState = modelManager.state.value,
          usingFallbackRightNow = false,
        ),
    )

  fun speak(rawText: String) {
    val cleanText = sanitizeTextForSpeech(rawText)
    if (cleanText.isBlank()) return

    stop()

    val effectiveEngine =
      resolveEffectiveVoiceEngine(
        preference = enginePreference.value,
        isPiperModelReady = modelManager.resolveModelPaths() != null,
      )

    if (effectiveEngine == VoiceEnginePreference.PIPER_LOCAL) {
      usingFallbackNotice.value = false
      scope.launch {
        piperTtsEngine.speak(cleanText) { fallbackText ->
          usingFallbackNotice.value = true
          androidTtsController.speak(fallbackText)
        }
      }
    } else {
      usingFallbackNotice.value = false
      androidTtsController.speak(cleanText)
    }
  }

  fun stop() {
    piperTtsEngine.stop()
    androidTtsController.stop()
    piperSpeaking.value = false
    androidSpeaking.value = false
    onSpeakingChanged(false)
  }

  fun startPiperDownload() {
    // Switching back to Piper preference when the user explicitly downloads the voice
    selectVoiceEngine(VoiceEnginePreference.PIPER_LOCAL)
    usingFallbackNotice.value = false
    modelManager.startDownload()
  }

  fun cancelPiperDownload() {
    modelManager.cancelDownload()
  }

  fun deletePiperModel() {
    stop()
    piperTtsEngine.unloadModel()
    modelManager.deleteModel()
    usingFallbackNotice.value = false
  }

  fun selectVoiceEngine(preference: VoiceEnginePreference) {
    stop()
    enginePreference.value = preference
    usingFallbackNotice.value = false
    prefs.edit().putString(KEY_VOICE_ENGINE, preference.name).apply()
  }

  fun shutdown() {
    stop()
    piperTtsEngine.shutdown()
    androidTtsController.shutdown()
    modelManager.shutdown()
    scope.cancel()
  }

  private fun emitCombinedSpeaking() {
    onSpeakingChanged(piperSpeaking.value || androidSpeaking.value)
  }

  private fun readSavedPreference(): VoiceEnginePreference {
    val raw = prefs.getString(KEY_VOICE_ENGINE, null)
    return VoiceEnginePreference.entries.firstOrNull { it.name == raw }
      ?: VoiceEnginePreference.PIPER_LOCAL
  }

  private companion object {
    const val PREFS_NAME = "pauline_voice_settings"
    const val KEY_VOICE_ENGINE = "voice_engine_preference"
  }
}
