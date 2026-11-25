package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.R

internal fun mergeDictatedText(baseDraft: String, spoken: String): String {
  val cleanSpoken = spoken.trim()
  if (cleanSpoken.isEmpty()) return baseDraft
  if (baseDraft.isBlank()) return cleanSpoken
  val needsSpace = !baseDraft.last().isWhitespace()
  return if (needsSpace) "$baseDraft $cleanSpoken" else "$baseDraft$cleanSpoken"
}

class VoiceDictationController(
  private val context: Context,
  private val onListeningChanged: (Boolean) -> Unit,
  private val onDraftUpdate: (String) -> Unit,
  private val onError: (String) -> Unit,
  private val onSpeechStarted: () -> Unit = {},
) {
  private var recognizer: SpeechRecognizer? = null
  private var baseDraft: String = ""
  private var active = false
  private var userStoppedManually = false

  fun isListening(): Boolean = active

  fun startListening(currentDraft: String) {
    if (active) return

    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
      onError(context.getString(R.string.voice_unavailable))
      return
    }

    val speechRecognizer = ensureRecognizer()
    if (speechRecognizer == null) {
      onError(context.getString(R.string.voice_unavailable))
      return
    }

    baseDraft = currentDraft
    userStoppedManually = false
    active = true
    onListeningChanged(true)

    val intent =
      Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, SPEECH_LOCALE_FR_FR)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, SPEECH_LOCALE_FR_FR)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Allow natural pauses without prematurely cutting continuous dictation
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_500L)
      }

    try {
      speechRecognizer.startListening(intent)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start SpeechRecognizer", e)
      active = false
      onListeningChanged(false)
      resetRecognizer()
      onError(context.getString(R.string.voice_error_generic))
    }
  }

  fun stopListening() {
    if (!active) return
    userStoppedManually = true
    active = false
    onListeningChanged(false)
    try {
      recognizer?.stopListening()
    } catch (e: Exception) {
      Log.w(TAG, "stopListening failed: ${e.message}")
    }
  }

  fun destroy() {
    active = false
    userStoppedManually = true
    resetRecognizer()
  }

  private fun resetRecognizer() {
    try {
      recognizer?.destroy()
    } catch (e: Exception) {
      Log.w(TAG, "destroy SpeechRecognizer failed: ${e.message}")
    } finally {
      recognizer = null
    }
  }

  private fun ensureRecognizer(): SpeechRecognizer? {
    recognizer?.let { return it }
    val created =
      try {
        SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
      } catch (e: Exception) {
        Log.e(TAG, "Unable to create SpeechRecognizer", e)
        return null
      }
    created.setRecognitionListener(listener)
    recognizer = created
    return created
  }

  private val listener =
    object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) = Unit

      override fun onBeginningOfSpeech() {
        onSpeechStarted()
      }

      override fun onRmsChanged(rmsdB: Float) = Unit

      override fun onBufferReceived(buffer: ByteArray?) = Unit

      override fun onEndOfSpeech() {
        // Do NOT auto-restart listening; wait for final results or manual tap.
      }

      override fun onError(error: Int) {
        val wasManualStop = userStoppedManually
        active = false
        userStoppedManually = false
        onListeningChanged(false)
        resetRecognizer()

        if (wasManualStop && (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_NO_MATCH)) {
          return
        }

        val message =
          when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
              context.getString(R.string.voice_permission_denied)
            SpeechRecognizer.ERROR_AUDIO ->
              context.getString(R.string.voice_error_audio)
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
              context.getString(R.string.voice_error_network)
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
              context.getString(R.string.voice_no_match)
            else -> context.getString(R.string.voice_error_generic)
          }
        onError(message)
      }

      override fun onResults(results: Bundle?) {
        val spoken =
          results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (spoken.isNotBlank()) {
          onDraftUpdate(mergeDictatedText(baseDraft, spoken))
        }
        // Always stop cleanly after final analysis; never auto-restart in a loop.
        active = false
        userStoppedManually = false
        onListeningChanged(false)
      }

      override fun onPartialResults(partialResults: Bundle?) {
        if (!active) return
        val partial =
          partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (partial.isNotBlank()) {
          onDraftUpdate(mergeDictatedText(baseDraft, partial))
        }
      }

      override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

  private companion object {
    const val TAG = "VoiceDictation"
    const val SPEECH_LOCALE_FR_FR = "fr-FR"
  }
}
