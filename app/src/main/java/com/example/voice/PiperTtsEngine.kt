package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun splitTextIntoSpeechChunks(cleanText: String): List<String> {
  if (cleanText.isBlank()) return emptyList()
  val chunks = mutableListOf<String>()
  val current = StringBuilder()
  for (i in cleanText.indices) {
    val ch = cleanText[i]
    current.append(ch)
    val isSentenceEnd = ch == '.' || ch == '!' || ch == '?' || ch == '…' || ch == ';'
    val isNextSpaceOrEnd = i == cleanText.lastIndex || cleanText[i + 1].isWhitespace()
    if (isSentenceEnd && isNextSpaceOrEnd && current.length >= 4) {
      val trimmed = current.toString().trim()
      if (trimmed.isNotEmpty()) {
        chunks += trimmed
      }
      current.clear()
    }
  }
  val tail = current.toString().trim()
  if (tail.isNotEmpty()) {
    chunks += tail
  }
  return chunks
}

internal fun floatSamplesToPcm16(samples: FloatArray): ShortArray {
  val pcm = ShortArray(samples.size)
  for (i in samples.indices) {
    val clamped = samples[i].coerceIn(-1.0f, 1.0f)
    pcm[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
  }
  return pcm
}

class PiperTtsEngine(
  context: Context,
  private val modelManager: PiperModelManager,
  private val onSpeakingChanged: (Boolean) -> Unit,
) {
  private val appContext = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())
  private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val ttsMutex = Mutex()
  private var offlineTts: OfflineTts? = null
  private var loadedModelPath: String? = null
  @Volatile private var isShutDown = false

  private val playbackLock = Any()
  private var activeJob: Job? = null
  private var activeAudioTrack: AudioTrack? = null
  @Volatile private var currentCancelSignal: AtomicBoolean? = null

  suspend fun speak(
    cleanText: String,
    onFallbackNeeded: (String) -> Unit,
  ) {
    if (isShutDown || cleanText.isBlank()) return
    val chunks = splitTextIntoSpeechChunks(cleanText)
    if (chunks.isEmpty()) return

    stopPlayback(notifyStopped = false)

    val cancelFlag = AtomicBoolean(false)
    val job =
      engineScope.launch(start = CoroutineStart.LAZY) {
        var didStartAudio = false
        var localTrack: AudioTrack? = null
        var focusRequest: AudioFocusRequest? = null
        try {
          val tts =
            ttsMutex.withLock {
              if (isShutDown) return@withLock null
              ensureInitializedLocked()
            }

          if (tts == null || cancelFlag.get()) {
            if (!cancelFlag.get() && !isShutDown) {
              mainHandler.post { onFallbackNeeded(cleanText) }
            }
            return@launch
          }

          val sampleRate = tts.sampleRate().takeIf { it > 0 } ?: DEFAULT_SAMPLE_RATE
          localTrack = createAudioTrack(sampleRate)
          if (localTrack == null) {
            if (!cancelFlag.get() && !isShutDown) {
              mainHandler.post { onFallbackNeeded(cleanText) }
            }
            return@launch
          }

          synchronized(playbackLock) {
            if (cancelFlag.get() || isShutDown) {
              runCatching { localTrack.release() }
              return@launch
            }
            activeAudioTrack = localTrack
          }

          focusRequest = requestTransientAudioFocus()
          localTrack.play()

          for (chunk in chunks) {
            if (cancelFlag.get() || isShutDown) break
            ttsMutex.withLock {
              if (cancelFlag.get() || isShutDown || offlineTts !== tts) return@withLock
              tts.generateWithCallback(
                text = chunk,
                sid = 0,
                speed = PIPER_SPEECH_SPEED,
              ) { floatSamples ->
                if (cancelFlag.get() || isShutDown) {
                  0
                } else {
                  if (!didStartAudio && floatSamples.isNotEmpty()) {
                    didStartAudio = true
                    mainHandler.post { onSpeakingChanged(true) }
                  }
                  if (floatSamples.isNotEmpty()) {
                    val pcm = floatSamplesToPcm16(floatSamples)
                    val written = localTrack.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                    if (written < 0 || cancelFlag.get()) {
                      0
                    } else {
                      1
                    }
                  } else {
                    1
                  }
                }
              }
            }
          }

          if (!didStartAudio && !cancelFlag.get() && !isShutDown) {
            mainHandler.post { onFallbackNeeded(cleanText) }
          }
        } catch (_: CancellationException) {
          // Normal interruption
        } catch (t: Throwable) {
          Log.w(TAG, "Piper synthesis failed, falling back to Android TTS", t)
          if (!didStartAudio && !cancelFlag.get() && !isShutDown) {
            mainHandler.post { onFallbackNeeded(cleanText) }
          }
        } finally {
          synchronized(playbackLock) {
            if (activeAudioTrack === localTrack) {
              activeAudioTrack = null
            }
          }
          localTrack?.let { track ->
            runCatching {
              if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
              }
            }
            runCatching { track.release() }
          }
          abandonAudioFocus(focusRequest)
          mainHandler.post { onSpeakingChanged(false) }
        }
      }

    synchronized(playbackLock) {
      currentCancelSignal = cancelFlag
      activeJob = job
      job.invokeOnCompletion {
        synchronized(playbackLock) {
          if (activeJob === job) {
            activeJob = null
          }
          if (currentCancelSignal === cancelFlag) {
            currentCancelSignal = null
          }
        }
      }
    }
    job.start()
  }

  fun stop() {
    stopPlayback(notifyStopped = true)
  }

  private fun stopPlayback(notifyStopped: Boolean) {
    synchronized(playbackLock) {
      currentCancelSignal?.set(true)
      activeAudioTrack?.let { track ->
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
      }
      activeJob?.cancel()
      activeJob = null
    }
    if (notifyStopped) {
      mainHandler.post { onSpeakingChanged(false) }
    }
  }

  fun unloadModel() {
    stop()
    engineScope.launch {
      ttsMutex.withLock {
        runCatching { offlineTts?.release() }
        offlineTts = null
        loadedModelPath = null
      }
    }
  }

  fun shutdown() {
    isShutDown = true
    stop()
    CoroutineScope(Dispatchers.Default).launch {
      ttsMutex.withLock {
        runCatching { offlineTts?.release() }
        offlineTts = null
        loadedModelPath = null
      }
      engineScope.cancel()
    }
  }

  private fun ensureInitializedLocked(): OfflineTts? {
    val paths = modelManager.resolveModelPaths() ?: return null
    val modelAbsPath = paths.modelFile.absolutePath
    val existing = offlineTts
    if (existing != null && loadedModelPath == modelAbsPath) {
      return existing
    }

    runCatching { existing?.release() }
    offlineTts = null
    loadedModelPath = null

    return try {
      val vitsConfig =
        OfflineTtsVitsModelConfig(
          model = paths.modelFile.absolutePath,
          tokens = paths.tokensFile.absolutePath,
          dataDir = paths.espeakDataDir.absolutePath,
        )
      val modelConfig =
        OfflineTtsModelConfig(
          vits = vitsConfig,
          numThreads = 2,
          debug = false,
          provider = "cpu",
        )
      val ttsConfig =
        OfflineTtsConfig(
          model = modelConfig,
          maxNumSentences = 1,
        )
      val created = OfflineTts(assetManager = null, config = ttsConfig)
      offlineTts = created
      loadedModelPath = modelAbsPath
      Log.d(TAG, "Loaded Piper OfflineTts (${paths.modelFile.name}, rate=${created.sampleRate()})")
      created
    } catch (t: Throwable) {
      Log.w(TAG, "Unable to initialize Piper OfflineTts", t)
      offlineTts = null
      loadedModelPath = null
      null
    }
  }

  private fun createAudioTrack(sampleRate: Int): AudioTrack? {
    val minBufferBytes =
      AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
      )
    if (minBufferBytes <= 0) return null
    val bufferSize = maxOf(minBufferBytes * 2, sampleRate / 2 * 2)
    val attributes =
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    val format =
      AudioFormat.Builder()
        .setSampleRate(sampleRate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .build()
    return AudioTrack.Builder()
      .setAudioAttributes(attributes)
      .setAudioFormat(format)
      .setBufferSizeInBytes(bufferSize)
      .setTransferMode(AudioTrack.MODE_STREAM)
      .build()
  }

  private fun requestTransientAudioFocus(): AudioFocusRequest? {
    val manager = audioManager ?: return null
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val attributes =
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_ASSISTANT)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build()
      val request =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
          .setAudioAttributes(attributes)
          .setWillPauseWhenDucked(false)
          .build()
      runCatching { manager.requestAudioFocus(request) }
      request
    } else {
      @Suppress("DEPRECATION")
      runCatching {
        manager.requestAudioFocus(
          null,
          AudioManager.STREAM_MUSIC,
          AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
        )
      }
      null
    }
  }

  private fun abandonAudioFocus(request: AudioFocusRequest?) {
    val manager = audioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && request != null) {
      runCatching { manager.abandonAudioFocusRequest(request) }
    } else {
      @Suppress("DEPRECATION")
      runCatching { manager.abandonAudioFocus(null) }
    }
  }

  private companion object {
    const val TAG = "PiperTtsEngine"
    const val DEFAULT_SAMPLE_RATE = 22050
    const val PIPER_SPEECH_SPEED = 0.96f
  }
}
