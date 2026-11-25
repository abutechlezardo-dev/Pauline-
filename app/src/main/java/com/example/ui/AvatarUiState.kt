package com.example.ui

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.ai.PaulineAiService
import com.example.data.ChatMessage
import com.example.data.MemoryItem
import com.example.data.PaulineRepository
import com.example.data.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AvatarMood(@StringRes val labelRes: Int, @StringRes val greetingRes: Int) {
  SMILE(R.string.mood_smile, R.string.greeting_default),
  HAPPY(R.string.mood_happy, R.string.greeting_joy),
  WINK(R.string.mood_wink, R.string.greeting_wink),
  CALM(R.string.mood_calm, R.string.greeting_calm),
}

enum class AvatarBackdrop(
  @StringRes val labelRes: Int,
  val primaryColor: Color,
  val softAccent: Color,
) {
  CORAL(R.string.color_coral, Color(0xFFE87A5D), Color(0xFFFFD8CC)),
  SAGE(R.string.color_sage, Color(0xFF6EA18E), Color(0xFFD8EFE6)),
  LAVENDER(R.string.color_lavender, Color(0xFF9381C9), Color(0xFFE7E0FA)),
  HONEY(R.string.color_honey, Color(0xFFE2A84B), Color(0xFFFFEBC7)),
  SKY(R.string.color_sky, Color(0xFF5E9CC8), Color(0xFFD6ECFA)),
}

data class AvatarUiState(
  val mood: AvatarMood = AvatarMood.SMILE,
  val backdrop: AvatarBackdrop = AvatarBackdrop.CORAL,
  val waveCount: Int = 0,
  val isGreetingWave: Boolean = false,
  val streamingReply: String? = null,
  val isSending: Boolean = false,
  val chatError: String? = null,
  val lastFailedMessage: String? = null,
  val actionError: String? = null,
  val lastSavedMemoryTitle: String? = null,
)

class PaulineViewModel(
  private val repository: PaulineRepository,
  private val aiService: PaulineAiService,
  private val currentUserId: String,
  private val userDisplayName: String?,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AvatarUiState())
  val uiState: StateFlow<AvatarUiState> = _uiState.asStateFlow()

  private val _completedReplies = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val completedReplies: SharedFlow<String> = _completedReplies.asSharedFlow()

  val messagesState: StateFlow<UiState<List<ChatMessage>>> =
    repository
      .observeMessages(currentUserId)
      .map<List<ChatMessage>, UiState<List<ChatMessage>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing messages", rootError)
        emit(UiState.Error(rootError.localizedMessage ?: "Impossible de charger la discussion."))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val memoriesState: StateFlow<UiState<List<MemoryItem>>> =
    repository
      .observeMemories(currentUserId)
      .map<List<MemoryItem>, UiState<List<MemoryItem>>> { UiState.Success(it) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing memories", rootError)
        emit(UiState.Error(rootError.localizedMessage ?: "Impossible de charger les souvenirs."))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  init {
    viewModelScope.launch {
      repository
        .observePreferences(currentUserId)
        .catch { error ->
          val firestoreError =
            generateSequence(error) { it.cause }
              .filterIsInstance<FirebaseFirestoreException>()
              .firstOrNull()
          if (firestoreError == null && error is CancellationException) throw error
          Log.w(TAG, "Error observing preferences", firestoreError ?: error)
        }
        .collect { prefs ->
          if (prefs != null) {
            _uiState.update {
              it.copy(
                mood = prefs.resolvedMood(),
                backdrop = prefs.resolvedBackdrop(),
              )
            }
          }
        }
    }
  }

  fun selectMood(mood: AvatarMood) {
    val backdrop = _uiState.value.backdrop
    _uiState.update { it.copy(mood = mood, isGreetingWave = false) }
    viewModelScope.launch {
      repository.savePreferences(currentUserId, mood, backdrop).onFailure { error ->
        _uiState.update {
          it.copy(actionError = error.localizedMessage ?: "Impossible d'enregistrer l'humeur.")
        }
      }
    }
  }

  fun selectBackdrop(backdrop: AvatarBackdrop) {
    val mood = _uiState.value.mood
    _uiState.update { it.copy(backdrop = backdrop) }
    viewModelScope.launch {
      repository.savePreferences(currentUserId, mood, backdrop).onFailure { error ->
        _uiState.update {
          it.copy(actionError = error.localizedMessage ?: "Impossible d'enregistrer la couleur.")
        }
      }
    }
  }

  fun sayHello() {
    _uiState.update {
      it.copy(
        waveCount = it.waveCount + 1,
        isGreetingWave = true,
      )
    }
  }

  fun sendMessage(rawText: String) {
    val cleanText = rawText.trim()
    if (cleanText.isEmpty() || _uiState.value.isSending) return

    val currentMessages = (messagesState.value as? UiState.Success)?.data ?: emptyList()
    val currentMemories = (memoriesState.value as? UiState.Success)?.data ?: emptyList()
    val currentMood = _uiState.value.mood

    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isSending = true,
          streamingReply = "",
          chatError = null,
          lastFailedMessage = null,
          lastSavedMemoryTitle = null,
        )
      }

      val saveUserResult =
        repository.addMessage(
          userId = currentUserId,
          sender = "user",
          text = cleanText,
          moodTag = currentMood,
        )
      if (saveUserResult.isFailure) {
        val err = saveUserResult.exceptionOrNull()
        _uiState.update {
          it.copy(
            isSending = false,
            streamingReply = null,
            chatError = err?.localizedMessage ?: "Impossible d'envoyer le message.",
          )
        }
        return@launch
      }

      generateAndSaveReply(
        userMessage = cleanText,
        recentHistory = currentMessages,
        memories = currentMemories,
        currentMood = currentMood,
      )
    }
  }

  fun retryLastMessage() {
    val failedText = _uiState.value.lastFailedMessage ?: return
    if (_uiState.value.isSending) return
    val currentMessages = (messagesState.value as? UiState.Success)?.data ?: emptyList()
    val currentMemories = (memoriesState.value as? UiState.Success)?.data ?: emptyList()
    val currentMood = _uiState.value.mood

    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isSending = true,
          streamingReply = "",
          chatError = null,
          lastFailedMessage = null,
          lastSavedMemoryTitle = null,
        )
      }
      generateAndSaveReply(
        userMessage = failedText,
        recentHistory = currentMessages,
        memories = currentMemories,
        currentMood = currentMood,
      )
    }
  }

  private suspend fun generateAndSaveReply(
    userMessage: String,
    recentHistory: List<ChatMessage>,
    memories: List<MemoryItem>,
    currentMood: AvatarMood,
  ) {
    var finalReply = ""
    try {
      aiService
        .streamReply(
          userMessage = userMessage,
          recentHistory = recentHistory,
          memories = memories,
          currentMood = currentMood,
          userName = userDisplayName,
        )
        .collect { accumulated ->
          finalReply = accumulated
          _uiState.update { it.copy(streamingReply = accumulated) }
        }

      val extraction =
        aiService.analyzeTurnForMemoryAndMood(
          userMessage = userMessage,
          paulineReply = finalReply,
          existingMemories = memories,
        )
      val replyMood = extraction?.suggestedMood ?: currentMood

      val saveBotResult =
        repository.addMessage(
          userId = currentUserId,
          sender = "pauline",
          text = finalReply,
          moodTag = replyMood,
        )
      if (saveBotResult.isFailure) {
        val err = saveBotResult.exceptionOrNull()
        _uiState.update {
          it.copy(
            isSending = false,
            streamingReply = null,
            lastFailedMessage = userMessage,
            chatError = err?.localizedMessage ?: "Impossible d'enregistrer la réponse.",
          )
        }
        return
      }

      _uiState.update {
        it.copy(
          isSending = false,
          streamingReply = null,
          lastFailedMessage = null,
          mood = replyMood,
          waveCount = it.waveCount + 1,
        )
      }
      _completedReplies.tryEmit(finalReply)

      if (replyMood != currentMood) {
        repository.savePreferences(currentUserId, replyMood, _uiState.value.backdrop)
      }

      if (extraction != null && extraction.shouldSave) {
        val memResult =
          repository.addMemory(
            userId = currentUserId,
            title = extraction.title,
            detail = extraction.detail,
            source = "auto",
          )
        if (memResult.isSuccess) {
          _uiState.update { it.copy(lastSavedMemoryTitle = extraction.title) }
        }
      }
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "Pauline AI reply timed out", e)
      _uiState.update {
        it.copy(
          isSending = false,
          streamingReply = null,
          lastFailedMessage = userMessage,
          chatError = "Pauline met un peu trop de temps à répondre. Réessaie dans un instant.",
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Pauline AI generation failed", e)
      _uiState.update {
        it.copy(
          isSending = false,
          streamingReply = null,
          lastFailedMessage = userMessage,
          chatError = e.localizedMessage ?: "Erreur lors de la réponse de Pauline.",
        )
      }
    }
  }

  fun addMemory(title: String, detail: String) {
    viewModelScope.launch {
      repository.addMemory(currentUserId, title, detail, source = "manual").onFailure { error ->
        _uiState.update {
          it.copy(actionError = error.localizedMessage ?: "Impossible d'ajouter ce souvenir.")
        }
      }
    }
  }

  fun updateMemory(memoryId: String, title: String, detail: String) {
    viewModelScope.launch {
      repository.updateMemory(currentUserId, memoryId, title, detail).onFailure { error ->
        _uiState.update {
          it.copy(actionError = error.localizedMessage ?: "Impossible de modifier ce souvenir.")
        }
      }
    }
  }

  fun deleteMemory(memoryId: String) {
    viewModelScope.launch {
      repository.deleteMemory(currentUserId, memoryId).onFailure { error ->
        _uiState.update {
          it.copy(actionError = error.localizedMessage ?: "Impossible de supprimer ce souvenir.")
        }
      }
    }
  }

  fun clearMessages() {
    viewModelScope.launch {
      repository.clearMessages(currentUserId).onFailure { error ->
        _uiState.update {
          it.copy(chatError = error.localizedMessage ?: "Impossible d'effacer la discussion.")
        }
      }
    }
  }

  fun dismissChatError() {
    _uiState.update { it.copy(chatError = null, lastFailedMessage = null) }
  }

  fun dismissActionError() {
    _uiState.update { it.copy(actionError = null) }
  }

  fun dismissMemoryNotice() {
    _uiState.update { it.copy(lastSavedMemoryTitle = null) }
  }

  private companion object {
    const val TAG = "PaulineViewModel"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
