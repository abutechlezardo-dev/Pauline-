package com.example.voice

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

data class PiperModelPaths(
  val modelFile: File,
  val tokensFile: File,
  val espeakDataDir: File,
)

sealed interface PiperModelState {
  data object NotDownloaded : PiperModelState

  data class Downloading(
    val progressPercent: Int?,
    val downloadedBytes: Long,
    val totalBytes: Long?,
  ) : PiperModelState

  data object Extracting : PiperModelState

  data class Ready(
    val sizeBytes: Long,
  ) : PiperModelState

  data class Error(
    val message: String,
  ) : PiperModelState
}

internal fun resolveVerifiedPiperModelPaths(rootDir: File): PiperModelPaths? {
  if (!rootDir.exists() || !rootDir.isDirectory) return null

  val modelFile =
    rootDir
      .walkTopDown()
      .maxDepth(3)
      .firstOrNull { it.isFile && it.name.endsWith(".onnx") && it.length() > 1_000_000L }
      ?: return null

  val parentDir = modelFile.parentFile ?: rootDir
  val tokensFile =
    File(parentDir, "tokens.txt").takeIf { it.isFile && it.length() > 0L }
      ?: rootDir.walkTopDown().maxDepth(3).firstOrNull { it.isFile && it.name == "tokens.txt" && it.length() > 0L }
      ?: return null

  val espeakDir =
    File(parentDir, "espeak-ng-data").takeIf { it.isDirectory && hasNonEmptyFiles(it) }
      ?: rootDir
        .walkTopDown()
        .maxDepth(3)
        .firstOrNull { it.isDirectory && it.name == "espeak-ng-data" && hasNonEmptyFiles(it) }
      ?: return null

  return PiperModelPaths(
    modelFile = modelFile,
    tokensFile = tokensFile,
    espeakDataDir = espeakDir,
  )
}

internal fun resolveSafeArchiveTarget(destinationDir: File, entryName: String): File? {
  val normalized = entryName.replace('\\', '/').trimStart('/')
  if (normalized.isBlank()) return null
  val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
  if (parts.any { it == ".." }) return null
  val strippedParts = if (parts.size > 1) parts.drop(1) else parts
  if (strippedParts.isEmpty()) return null

  val destCanonical = destinationDir.canonicalFile
  val outFile = File(destCanonical, strippedParts.joinToString(File.separator)).canonicalFile
  val destPrefix = destCanonical.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
  return if (outFile.path.startsWith(destPrefix)) outFile else null
}

private fun hasNonEmptyFiles(dir: File): Boolean =
  dir.listFiles()?.any { (it.isFile && it.length() > 0L) || (it.isDirectory && hasNonEmptyFiles(it)) } == true

private fun computeDirectorySize(dir: File): Long =
  dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

class PiperModelManager(
  context: Context,
) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val piperBaseDir = File(appContext.noBackupFilesDir, "piper")
  private val modelDir = File(piperBaseDir, MODEL_ID)
  private val tempExtractDir = File(piperBaseDir, "$MODEL_ID.tmp")
  private val partialArchiveFile = File(piperBaseDir, "$MODEL_ID.tar.bz2.part")

  private val httpClient =
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .followRedirects(true)
      .followSslRedirects(true)
      .build()

  private var downloadJob: Job? = null
  private var activeCall: Call? = null
  private val isCancelled = AtomicBoolean(false)

  private val _state = MutableStateFlow(initialState())
  val state: StateFlow<PiperModelState> = _state.asStateFlow()

  private fun initialState(): PiperModelState {
    val existing = resolveVerifiedPiperModelPaths(modelDir)
    return if (existing != null) {
      PiperModelState.Ready(computeDirectorySize(modelDir))
    } else {
      PiperModelState.NotDownloaded
    }
  }

  fun resolveModelPaths(): PiperModelPaths? = resolveVerifiedPiperModelPaths(modelDir)

  fun startDownload() {
    val current = _state.value
    if (current is PiperModelState.Downloading || current is PiperModelState.Extracting) return
    if (resolveVerifiedPiperModelPaths(modelDir) != null) {
      _state.value = PiperModelState.Ready(computeDirectorySize(modelDir))
      return
    }

    isCancelled.set(false)
    downloadJob?.cancel()
    downloadJob =
      scope.launch {
        _state.value = PiperModelState.Downloading(progressPercent = 0, downloadedBytes = 0L, totalBytes = null)
        try {
          piperBaseDir.mkdirs()
          tempExtractDir.deleteRecursively()
          if (partialArchiveFile.exists()) {
            partialArchiveFile.delete()
          }

          downloadArchive(MODEL_ARCHIVE_URL, partialArchiveFile)
          currentCoroutineContext().ensureActive()

          _state.value = PiperModelState.Extracting
          tempExtractDir.mkdirs()
          extractTarBz2(partialArchiveFile, tempExtractDir)
          currentCoroutineContext().ensureActive()

          val verified = resolveVerifiedPiperModelPaths(tempExtractDir)
          if (verified == null) {
            throw IllegalStateException("Les fichiers du modèle Piper extraits sont incomplets.")
          }

          modelDir.deleteRecursively()
          if (!tempExtractDir.renameTo(modelDir)) {
            tempExtractDir.copyRecursively(modelDir, overwrite = true)
            tempExtractDir.deleteRecursively()
          }
          partialArchiveFile.delete()

          val finalVerified = resolveVerifiedPiperModelPaths(modelDir)
          if (finalVerified == null) {
            throw IllegalStateException("Impossible d'installer le modèle Piper localement.")
          }
          _state.value = PiperModelState.Ready(computeDirectorySize(modelDir))
          Log.d(TAG, "Piper model ready at ${modelDir.absolutePath}")
        } catch (ce: CancellationException) {
          cleanupTempFiles()
          _state.value =
            if (resolveVerifiedPiperModelPaths(modelDir) != null) {
              PiperModelState.Ready(computeDirectorySize(modelDir))
            } else {
              PiperModelState.NotDownloaded
            }
          throw ce
        } catch (e: Exception) {
          Log.w(TAG, "Piper model download failed", e)
          cleanupTempFiles()
          _state.value =
            if (isCancelled.get()) {
              PiperModelState.NotDownloaded
            } else {
              PiperModelState.Error(
                e.localizedMessage?.takeIf { it.isNotBlank() }
                  ?: "Téléchargement impossible. Vérifie ta connexion Internet.",
              )
            }
        }
      }
  }

  fun cancelDownload() {
    isCancelled.set(true)
    activeCall?.cancel()
    activeCall = null
    downloadJob?.cancel()
    downloadJob = null
    cleanupTempFiles()
    _state.value =
      if (resolveVerifiedPiperModelPaths(modelDir) != null) {
        PiperModelState.Ready(computeDirectorySize(modelDir))
      } else {
        PiperModelState.NotDownloaded
      }
  }

  fun deleteModel() {
    cancelDownload()
    modelDir.deleteRecursively()
    _state.value = PiperModelState.NotDownloaded
  }

  fun shutdown() {
    isCancelled.set(true)
    activeCall?.cancel()
    activeCall = null
    scope.cancel()
  }

  private suspend fun downloadArchive(url: String, outputFile: File) {
    val request = Request.Builder().url(url).build()
    val call = httpClient.newCall(request)
    activeCall = call
    try {
      call.execute().use { response ->
        if (!response.isSuccessful) {
          throw IllegalStateException("Erreur serveur HTTP ${response.code} lors du téléchargement.")
        }
        val body = response.body ?: throw IllegalStateException("Réponse vide du serveur.")
        val totalBytes = body.contentLength().takeIf { it > 0L }
        var downloadedBytes = 0L
        var lastReportedPercent = -1
        var lastEmitTimeMs = 0L

        body.byteStream().use { input ->
          FileOutputStream(outputFile).use { output ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
              currentCoroutineContext().ensureActive()
              val read = input.read(buffer)
              if (read < 0) break
              output.write(buffer, 0, read)
              downloadedBytes += read
              val now = System.currentTimeMillis()
              val percent =
                totalBytes?.let { ((downloadedBytes * 100L) / it).toInt().coerceIn(0, 100) }
              if (
                (percent != null && percent != lastReportedPercent) ||
                  now - lastEmitTimeMs >= 250L
              ) {
                lastReportedPercent = percent ?: -1
                lastEmitTimeMs = now
                _state.value =
                  PiperModelState.Downloading(
                    progressPercent = percent,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                  )
              }
            }
          }
        }
      }
    } finally {
      if (activeCall === call) {
        activeCall = null
      }
    }
  }

  private suspend fun extractTarBz2(archiveFile: File, targetDir: File) {
    FileInputStream(archiveFile).use { fileIn ->
      BufferedInputStream(fileIn).use { bufferedIn ->
        BZip2CompressorInputStream(bufferedIn).use { bzIn ->
          TarArchiveInputStream(bzIn).use { tarIn ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
              currentCoroutineContext().ensureActive()
              val entry = tarIn.nextEntry ?: break
              if (!tarIn.canReadEntryData(entry)) continue
              val outFile = resolveSafeArchiveTarget(targetDir, entry.name) ?: continue
              if (entry.isDirectory) {
                outFile.mkdirs()
              } else {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { out ->
                  while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = tarIn.read(buffer)
                    if (count < 0) break
                    out.write(buffer, 0, count)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private fun cleanupTempFiles() {
    runCatching { tempExtractDir.deleteRecursively() }
    runCatching { partialArchiveFile.delete() }
  }

  companion object {
    const val MODEL_ID = "fr_FR-siwis-medium"
    const val MODEL_ARCHIVE_URL =
      "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fr_FR-siwis-medium.tar.bz2"
    private const val TAG = "PiperModelManager"
  }
}
