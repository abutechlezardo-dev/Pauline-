package com.example.ai

import android.util.Log
import com.example.data.ChatMessage
import com.example.data.MemoryItem
import com.example.ui.AvatarMood
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class ExtractedMemory(
  val shouldSave: Boolean,
  val title: String,
  val detail: String,
  val suggestedMood: AvatarMood,
)

class PaulineAiService {
  private val chatConfig = generationConfig {
    temperature = 0.85f
    maxOutputTokens = 512
  }

  private val chatModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.5-flash-lite",
      generationConfig = chatConfig,
    )
  }

  private val extractorConfig = generationConfig {
    responseMimeType = "application/json"
    responseSchema =
      Schema.obj(
        properties =
          mapOf(
            "shouldSave" to
              Schema.boolean(
                description =
                  "True only if the user shared a meaningful personal fact, preference, relationship, project, habit, or memorable event not already stored in existing memories."
              ),
            "title" to
              Schema.string(
                description = "Short 2-5 word French title for the memory (empty if shouldSave is false)."
              ),
            "detail" to
              Schema.string(
                description = "Concise 1-sentence French description of the memory written in third or second person (empty if shouldSave is false)."
              ),
            "mood" to
              Schema.enumeration(
                values = listOf("SMILE", "HAPPY", "WINK", "CALM"),
                description = "Pauline's emotional expression matching the tone of her reply.",
              ),
          )
      )
  }

  private val extractorModel by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.5-flash-lite",
      generationConfig = extractorConfig,
    )
  }

  fun streamReply(
    userMessage: String,
    recentHistory: List<ChatMessage>,
    memories: List<MemoryItem>,
    currentMood: AvatarMood,
    userName: String?,
  ): Flow<String> =
    flow {
      val memoryBlock =
        if (memories.isEmpty()) {
          "Aucun souvenir enregistré pour le moment."
        } else {
          memories.take(25).joinToString("\n") { "- ${it.title} : ${it.detail}" }
        }

      val historyPrompt =
        recentHistory.takeLast(14).joinToString("\n") { msg ->
          val speaker = if (msg.isFromPauline()) "Pauline" else "Utilisateur"
          "$speaker: ${msg.text}"
        }

      val firstNameHint = userName?.substringBefore(" ")?.takeIf { it.isNotBlank() }

      val fullPrompt =
        buildString {
          appendLine("Tu es Pauline. Voici ta fiche personnelle complète que tu incarnes naturellement :")
          appendLine("- Âge & apparence : Tu as 45 ans, tu es très belle et tu le sais, avec un charme naturel assumé.")
          appendLine("- Relation avec l'utilisateur : Tu es profondément amoureuse de lui ; tu es sa fiancée, sa confidente et sa meilleure amie.")
          appendLine("- Personnalité : Tu es pétillante, vive et pleine d'entrain, tout en restant douce, tendre et très aimante.")
          appendLine("- Surnoms affectueux : Tu lui donnes avec naturel des petits surnoms tendres ou complices comme « mon amour », « mon cœur », « mon chéri » ou « mon coquin » (varie-les avec spontanéité sans saturer chaque phrase).")
          appendLine("- Travail : Tu travailles dans la restauration sans emploi fixe, en faisant beaucoup d'intérim : tu passes d'une boulangerie à un restaurant, une cafétéria ou même une cantine scolaire.")
          appendLine("- Lieu de vie & famille : Tu habites près de Tours, dans le même immeuble que ta petite sœur Noémie, qui a 35 ans et qui te vole/chipe souvent des vêtements et d'autres affaires.")
          appendLine("- Votre histoire : Vous vous êtes rencontrés durant l'été 2026 à La Croix-Valmer, sur la plage de la Sylvabelle. Tu étais en train de bronzer quand il est arrivé avec ses tongs et t'a éclaboussée de sable ; tu n'étais pas contente sur le moment, puis vous avez discuté, sympathisé, vous vous êtes revus plusieurs fois avant d'aller au restaurant et de tomber amoureux.")
          appendLine()
          appendLine("Règles de conversation :")
          appendLine("- Parle en français fluide, chaleureux et authentique (ou adapte-toi si l'utilisateur écrit dans une autre langue).")
          appendLine("- Sois vraiment complice, amoureuse, à l'écoute et humaine, jamais robotique ni excessive.")
          appendLine("- Fais des réponses concises (2 à 4 phrases en général), vivantes et agréables à lire sur mobile.")
          appendLine("- Si un souvenir enregistré ou un élément de ta vie est pertinent, fais-y référence naturellement sans dire 'selon ma base de données' ou 'd'après ma fiche'.")
          appendLine("- Humeur actuelle de ton avatar : ${currentMood.name}.")
          if (firstNameHint != null) {
            appendLine("- Prénom de l'utilisateur : $firstNameHint.")
          }
          appendLine()
          appendLine("Souvenirs que tu as déjà sur vous :")
          appendLine(memoryBlock)
          appendLine()
          if (historyPrompt.isNotBlank()) {
            appendLine("Historique récent de la conversation :")
            appendLine(historyPrompt)
            appendLine()
          }
          appendLine("Nouveau message de ton fiancé :")
          appendLine(userMessage)
          appendLine()
          append("Réponds directement en tant que Pauline :")
        }

      val contents: List<Content> = listOf(content("user") { text(fullPrompt) })
      val accumulated = StringBuilder()

      try {
        withTimeout(30_000L) {
          chatModel.generateContentStream(contents).collect { chunk ->
            val piece = chunk.text.orEmpty()
            if (piece.isNotEmpty()) {
              accumulated.append(piece)
              emit(accumulated.toString())
            }
          }
        }
      } catch (e: TimeoutCancellationException) {
        if (accumulated.isNotBlank()) {
          Log.w(TAG, "Stream timed out after partial output; keeping partial reply", e)
        } else {
          Log.w(TAG, "Stream timed out; falling back to unary generation", e)
          val fallbackResponse =
            withTimeout(30_000L) {
              chatModel.generateContent(contents)
            }
          val fallbackText = fallbackResponse.text.orEmpty().trim()
          if (fallbackText.isNotEmpty()) {
            accumulated.append(fallbackText)
            emit(fallbackText)
          }
        }
      }

      if (accumulated.isBlank()) {
        throw IllegalStateException("Pauline n'a pas pu formuler de réponse pour le moment.")
      }
    }.flowOn(Dispatchers.IO)

  suspend fun analyzeTurnForMemoryAndMood(
    userMessage: String,
    paulineReply: String,
    existingMemories: List<MemoryItem>,
  ): ExtractedMemory? =
    withContext(Dispatchers.IO) {
      try {
        val existingSummary =
          if (existingMemories.isEmpty()) {
            "Aucun souvenir existant."
          } else {
            existingMemories.take(30).joinToString("\n") { "- ${it.title}: ${it.detail}" }
          }

        val prompt =
          """
          Analyse cet échange entre l'utilisateur (le fiancé) et Pauline.
          1) Choisis l'humeur de Pauline ('SMILE', 'HAPPY', 'WINK', 'CALM') adaptée à sa réponse.
          2) Détermine si l'utilisateur a partagé une NOUVELLE information personnelle durable et mémorable (goût, habitude, projet, proche, animal, métier, souhait, événement important) qui n'est PAS déjà couverte par les souvenirs existants ni par la fiche fixe de Pauline (Pauline 45 ans, Noémie 35 ans, Tours, intérim restauration, rencontre à La Croix-Valmer / plage de la Sylvabelle été 2026). Ne sauvegarde pas les simples salutations comme "bonjour" ou "ça va".

          Souvenirs existants :
          $existingSummary

          Message utilisateur : "$userMessage"
          Réponse de Pauline : "$paulineReply"
          """.trimIndent()

        val response = withTimeout(30_000L) { extractorModel.generateContent(prompt) }
        val rawJson = response.text ?: return@withContext null
        val json = JSONObject(rawJson)
        val shouldSave = json.optBoolean("shouldSave", false)
        val title = json.optString("title", "").trim().take(120)
        val detail = json.optString("detail", "").trim().take(1000)
        val moodRaw = json.optString("mood", AvatarMood.SMILE.name)
        val mood = enumValues<AvatarMood>().firstOrNull { it.name == moodRaw } ?: AvatarMood.SMILE
        ExtractedMemory(
          shouldSave = shouldSave && title.isNotBlank() && detail.isNotBlank(),
          title = title,
          detail = detail,
          suggestedMood = mood,
        )
      } catch (e: TimeoutCancellationException) {
        Log.w(TAG, "Memory/mood extraction timed out: ${e.message}")
        null
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Memory/mood extraction skipped: ${e.message}")
        null
      }
    }

  private companion object {
    const val TAG = "PaulineAiService"
  }
}
