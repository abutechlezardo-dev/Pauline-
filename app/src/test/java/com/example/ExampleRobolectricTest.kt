package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChatMessage
import com.example.data.MemoryItem
import com.example.data.UserAvatarPreferences
import com.example.ui.AvatarBackdrop
import com.example.ui.AvatarMood
import com.example.voice.VoiceCandidate
import com.example.voice.mergeDictatedText
import com.example.voice.sanitizeTextForSpeech
import com.example.voice.selectBestFrenchVoiceCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
