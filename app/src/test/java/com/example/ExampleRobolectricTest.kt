package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChatMessage
import com.example.data.MemoryItem
import com.example.data.UserAvatarPreferences
import com.example.ui.AvatarBackdrop
import com.example.ui.AvatarMood
import com.example.voice.VoiceCandidate
import com.example.voice.VoiceEnginePreference
import com.example.voice.floatSamplesToPcm16
import com.example.voice.mergeDictatedText
import com.example.voice.resolveEffectiveVoiceEngine
import com.example.voice.resolveSafeArchiveTarget
import com.example.voice.resolveVerifiedPiperModelPaths
import com.example.voice.sanitizeTextForSpeech
import com.example.voice.selectBestFrenchVoiceCandidate
import com.example.voice.splitTextIntoSpeechChunks
import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Pauline", appName)
  }

  @Test
  fun `models safely deserialize enums and defaults`() {
    val prefs = UserAvatarPreferences(mood = "UNKNOWN", backdrop = "UNKNOWN")
    assertEquals(AvatarMood.SMILE, prefs.resolvedMood())
    assertEquals(AvatarBackdrop.CORAL, prefs.resolvedBackdrop())

    val msg = ChatMessage(sender = "pauline", text = "Coucou !", moodTag = "HAPPY")
    assertTrue(msg.isFromPauline())
    assertEquals(AvatarMood.HAPPY, msg.resolvedMood())

    val manualMemory = MemoryItem(title = "Café", detail = "Aime le café", source = "manual")
    assertFalse(manualMemory.isAutoLearned())
  }

  @Test
  fun `mergeDictatedText combines base draft and spoken text cleanly`() {
    assertEquals("Bonjour mon amour", mergeDictatedText("", "  Bonjour mon amour  "))
    assertEquals("Coucou Pauline", mergeDictatedText("Coucou", "Pauline"))
    assertEquals("Coucou Pauline", mergeDictatedText("Coucou ", "Pauline"))
    assertEquals("Coucou", mergeDictatedText("Coucou", "   "))
  }

  @Test
  fun `sanitizeTextForSpeech removes emojis and symbols while keeping French text`() {
    val input = "Coucou mon cœur ❤️✨ ! **On se voit** à La Croix-Valmer 🏖️ ? #été ~ 100% • oui…"
    val sanitized = sanitizeTextForSpeech(input)
    assertEquals("Coucou mon cœur ! On se voit à La Croix-Valmer ? été 100 oui…", sanitized)
    assertEquals("", sanitizeTextForSpeech("❤️✨🎉"))
  }

  @Test
  fun `selectBestFrenchVoiceCandidate prefers fr-FR female voice then best French fallback`() {
    val frMale =
      VoiceCandidate(
        id = "fr-fr-x-frb-local",
        name = "fr-fr-x-frb-local",
        language = "fr",
        country = "FR",
        quality = 500,
        requiresNetwork = false,
        features = emptySet(),
      )
    val frFemale =
      VoiceCandidate(
        id = "fr-fr-x-fra-local",
        name = "fr-fr-x-fra-local",
        language = "fr",
        country = "FR",
        quality = 400,
        requiresNetwork = false,
        features = emptySet(),
      )
    val frCaFemale =
      VoiceCandidate(
        id = "fr-ca-x-caa-local",
        name = "fr-ca-x-caa-local",
        language = "fr",
        country = "CA",
        quality = 500,
        requiresNetwork = false,
        features = setOf("female"),
      )
    val enUs =
      VoiceCandidate(
        id = "en-us-x-sfg-local",
        name = "en-us-x-sfg-local",
        language = "en",
        country = "US",
        quality = 500,
        requiresNetwork = false,
        features = setOf("female"),
      )

    assertEquals(
      frFemale.id,
      selectBestFrenchVoiceCandidate(listOf(enUs, frMale, frCaFemale, frFemale))?.id,
    )
    assertEquals(
      frCaFemale.id,
      selectBestFrenchVoiceCandidate(listOf(enUs, frCaFemale, frMale))?.id,
    )
    assertEquals(
      frMale.id,
      selectBestFrenchVoiceCandidate(listOf(enUs, frMale))?.id,
    )
    assertNull(selectBestFrenchVoiceCandidate(listOf(enUs)))
  }

  @Test
  fun `splitTextIntoSpeechChunks splits sentences naturally for fast streaming`() {
    val chunks =
      splitTextIntoSpeechChunks(
        "Coucou mon cœur ! Comment s'est passée ta journée à Tours ? Moi ça va super.",
      )
    assertEquals(
      listOf(
        "Coucou mon cœur !",
        "Comment s'est passée ta journée à Tours ?",
        "Moi ça va super.",
      ),
      chunks,
    )
    assertEquals(emptyList<String>(), splitTextIntoSpeechChunks("   "))
  }

  @Test
  fun `floatSamplesToPcm16 clamps out of bounds audio safely`() {
    val pcm = floatSamplesToPcm16(floatArrayOf(-1.5f, -1.0f, 0.0f, 1.0f, 2.0f))
    assertArrayEquals(
      shortArrayOf(
        (-Short.MAX_VALUE).toShort(),
        (-Short.MAX_VALUE).toShort(),
        0,
        Short.MAX_VALUE,
        Short.MAX_VALUE,
      ),
      pcm,
    )
  }

  @Test
  fun `resolveEffectiveVoiceEngine always falls back to Android when Piper is not ready`() {
    assertEquals(
      VoiceEnginePreference.ANDROID_SYSTEM,
      resolveEffectiveVoiceEngine(VoiceEnginePreference.PIPER_LOCAL, isPiperModelReady = false),
    )
    assertEquals(
      VoiceEnginePreference.PIPER_LOCAL,
      resolveEffectiveVoiceEngine(VoiceEnginePreference.PIPER_LOCAL, isPiperModelReady = true),
    )
    assertEquals(
      VoiceEnginePreference.ANDROID_SYSTEM,
      resolveEffectiveVoiceEngine(VoiceEnginePreference.ANDROID_SYSTEM, isPiperModelReady = true),
    )
  }

  @Test
  fun `resolveSafeArchiveTarget blocks traversal and unwraps root folder`() {
    val destDir = createTempDirectory("piper-tar-test").toFile()
    try {
      assertNull(resolveSafeArchiveTarget(destDir, "../../etc/passwd"))
      assertNull(resolveSafeArchiveTarget(destDir, "pkg/../outside.onnx"))

      val onnxTarget =
        resolveSafeArchiveTarget(destDir, "vits-piper-fr_FR-siwis-medium/fr_FR-siwis-medium.onnx")
      assertNotNull(onnxTarget)
      assertEquals(File(destDir, "fr_FR-siwis-medium.onnx").canonicalPath, onnxTarget?.canonicalPath)

      val espeakTarget =
        resolveSafeArchiveTarget(destDir, "vits-piper-fr_FR-siwis-medium/espeak-ng-data/phontab")
      assertNotNull(espeakTarget)
      assertEquals(
        File(destDir, "espeak-ng-data/phontab").canonicalPath,
        espeakTarget?.canonicalPath,
      )
    } finally {
      destDir.deleteRecursively()
    }
  }

  @Test
  fun `resolveVerifiedPiperModelPaths validates required onnx tokens and espeak data`() {
    val root = createTempDirectory("piper-verify-test").toFile()
    try {
      assertNull(resolveVerifiedPiperModelPaths(root))

      val modelFile = File(root, "fr_FR-siwis-medium.onnx")
      modelFile.writeBytes(ByteArray(1_100_000))
      assertNull(resolveVerifiedPiperModelPaths(root))

      File(root, "tokens.txt").writeText("a 1\n")
      assertNull(resolveVerifiedPiperModelPaths(root))

      val espeakDir = File(root, "espeak-ng-data").apply { mkdirs() }
      File(espeakDir, "phontab").writeBytes(ByteArray(64))

      val resolved = resolveVerifiedPiperModelPaths(root)
      assertNotNull(resolved)
      assertEquals(modelFile.absolutePath, resolved?.modelFile?.absolutePath)
      assertEquals("tokens.txt", resolved?.tokensFile?.name)
      assertEquals("espeak-ng-data", resolved?.espeakDataDir?.name)
    } finally {
      root.deleteRecursively()
    }
  }
}
