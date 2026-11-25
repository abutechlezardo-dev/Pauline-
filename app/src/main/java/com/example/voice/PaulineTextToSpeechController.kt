package com.example.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import java.util.UUID

internal data class VoiceCandidate(
  val id: String,
  val name: String,
  val language: String,
  val country: String,
  val quality: Int,
  val requiresNetwork: Boolean,
  val features: Set<String>,
)

internal fun sanitizeTextForSpeech(rawText: String): String {
  if (rawText.isBlank()) return ""
  val sb = StringBuilder(rawText.length)
  var index = 0
  while (index < rawText.length) {
    val cp = rawText.codePointAt(index)
    val charCount = Character.charCount(cp)
    val keep =
      Character.isLetterOrDigit(cp) ||
        Character.isWhitespace(cp) ||
        cp in ALLOWED_SPEECH_PUNCTUATION
    if (keep) {
      sb.appendCodePoint(cp)
    } else {
      sb.append(' ')
    }
    index += charCount
  }
  return sb
    .toString()
    .replace(Regex("\\s+([,.])"), "$1")
    .replace(Regex("\\s+"), " ")
    .trim()
}

internal fun selectBestFrenchVoiceCandidate(candidates: List<VoiceCandidate>): VoiceCandidate? {
  val frenchVoices =
    candidates.filter { it.language.equals(Locale.FRENCH.language, ignoreCase = true) }
  if (frenchVoices.isEmpty()) return null
  return frenchVoices.maxByOrNull { scoreFrenchVoice(it) }
}

private fun scoreFrenchVoice(voice: VoiceCandidate): Int {
  val isFrance = voice.country.equals(Locale.FRANCE.country, ignoreCase = true)
  val nameLower = voice.name.lowercase(Locale.ROOT)
  val featuresLower = voice.features.map { it.lowercase(Locale.ROOT) }

  val hasFemaleFeature =
    featuresLower.any { it == "female" || it.contains("gender=female") || it.contains("femme") }
  val hasMaleFeature =
    featuresLower.any {
      it == "male" || it.contains("gender=male") || it.contains("homme")
    }

  val tokens = nameLower.split('-', '_', '.', ' ', ':').filter { it.isNotBlank() }.toSet()
  val hasFemaleHint =
    hasFemaleFeature ||
      tokens.any { it in FEMALE_VOICE_TOKENS } ||
      FEMALE_VOICE_SUBSTRINGS.any { nameLower.contains(it) }
  val hasMaleHint =
    !hasFemaleHint &&
      (hasMaleFeature ||
        tokens.any { it in MALE_VOICE_TOKENS } ||
        MALE_VOICE_SUBSTRINGS.any { nameLower.contains(it) })

  var score = 0
  score += if (isFrance) 10_000 else 5_000
  score +=
    when {
      hasFemaleHint -> 5_000
      hasMaleHint -> -2_000
      else -> 1_000
    }
  score += voice.quality.coerceIn(0, 1000)
  if (!voice.requiresNetwork) {
    score += 20
  }
  return score
}

private val ALLOWED_SPEECH_PUNCTUATION =
  setOf(
    '.'.code,
    ','.code,
    '!'.code,
    '?'.code,
    ':'.code,
    ';'.code,
    '\''.code,
    '\u2019'.code, // ’
    '-'.code,
    '('.code,
    ')'.code,
    '\u2026'.code, // …
  )

private val FEMALE_VOICE_TOKENS =
  setOf(
    "female",
    "femme",
    "woman",
    "girl",
    "f1",
    "f2",
    "fra",
    "frc",
    "vlf",
    "tpf",
  )

private val FEMALE_VOICE_SUBSTRINGS =
  listOf(
    "female",
    "femme",
    "amelie",
    "aurelie",
    "audrey",
    "camille",
    "celine",
    "chloe",
    "claire",
    "denise",
    "hortense",
    "julie",
    "juliette",
    "lea",
    "manon",
    "margaux",
    "marie",
    "pauline",
    "sylvie",
    "virginie",
    "-fra-",
    "-frc-",
    "-vlf-",
    "-tpf-",
  )

private val MALE_VOICE_TOKENS =
  setOf(
    "male",
    "homme",
    "man",
    "frb",
    "frd",
    "vlm",
  )

private val MALE_VOICE_SUBSTRINGS =
  listOf(
    "alain",
    "antoine",
    "henri",
    "jerome",
    "nicolas",
    "paul",
    "remy",
    "thomas",
    "-frb-",
    "-frd-",
    "-vlm-",
  )

class PaulineTextToSpeechController(
  context: Context,
  private val onSpeakingChanged: (Boolean) -> Unit,
) {
  private val appContext = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())
  private var tts: TextToSpeech? = null
  private var isInitialized = false
  private var pendingSpeech: String? = null

  init {
    tts =
      TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
          val engine = tts ?: return@TextToSpeech
          configureFrenchVoice(engine)
          engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
              override fun onStart(utteranceId: String?) {
                mainHandler.post { onSpeakingChanged(true) }
              }

              override fun onDone(utteranceId: String?) {
                mainHandler.post { onSpeakingChanged(false) }
              }

              override fun onStop(utteranceId: String?, interrupted: Boolean) {
                mainHandler.post { onSpeakingChanged(false) }
              }

              @Deprecated("Deprecated in Java")
              override fun onError(utteranceId: String?) {
                mainHandler.post { onSpeakingChanged(false) }
              }

              override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post { onSpeakingChanged(false) }
              }
            }
          )
          isInitialized = true
          pendingSpeech?.let { queued ->
            pendingSpeech = null
            speak(queued)
          }
        } else {
          Log.w(TAG, "TextToSpeech initialization failed with status=$status")
        }
      }
  }

  private fun configureFrenchVoice(engine: TextToSpeech) {
    try {
      val availableVoices: Set<Voice> = engine.voices ?: emptySet()
      val candidates =
        availableVoices.map { voice ->
          VoiceCandidate(
            id = voice.name,
            name = voice.name,
            language = voice.locale?.language.orEmpty(),
            country = voice.locale?.country.orEmpty(),
            quality = voice.quality,
            requiresNetwork = voice.isNetworkConnectionRequired,
            features = voice.features ?: emptySet(),
          )
        }
      val best = selectBestFrenchVoiceCandidate(candidates)
      val matchedVoice = best?.let { chosen -> availableVoices.firstOrNull { it.name == chosen.id } }
      if (matchedVoice != null) {
        val result = engine.setVoice(matchedVoice)
        if (result == TextToSpeech.ERROR) {
          engine.language = Locale.FRANCE
        } else {
          Log.d(TAG, "Selected French TTS voice: ${matchedVoice.name} (${matchedVoice.locale})")
        }
      } else {
        engine.language = Locale.FRANCE
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to configure preferred French voice, falling back to Locale.FRANCE", e)
      runCatching { engine.language = Locale.FRANCE }
    }
  }

  fun speak(rawText: String) {
    val cleanText = sanitizeTextForSpeech(rawText)
    if (cleanText.isBlank()) return
    val engine = tts ?: return
    if (!isInitialized) {
      pendingSpeech = cleanText
      return
    }
    try {
      val utteranceId = UUID.randomUUID().toString()
      engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    } catch (e: Exception) {
      Log.w(TAG, "TextToSpeech speak failed: ${e.message}")
      onSpeakingChanged(false)
    }
  }

  fun stop() {
    pendingSpeech = null
    try {
      tts?.stop()
    } catch (e: Exception) {
      Log.w(TAG, "TextToSpeech stop failed: ${e.message}")
    }
    onSpeakingChanged(false)
  }

  fun shutdown() {
    pendingSpeech = null
    isInitialized = false
    try {
      tts?.stop()
      tts?.shutdown()
    } catch (e: Exception) {
      Log.w(TAG, "TextToSpeech shutdown failed: ${e.message}")
    } finally {
      tts = null
      onSpeakingChanged(false)
    }
  }

  private companion object {
    const val TAG = "PaulineTTS"
  }
}
