package com.example.data

import androidx.annotation.Keep
import com.example.ui.AvatarBackdrop
import com.example.ui.AvatarMood
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>

  data class Success<T>(val data: T) : UiState<T>

  data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserAvatarPreferences(
  val userId: String = "",
  val mood: String = AvatarMood.SMILE.name,
  val backdrop: String = AvatarBackdrop.CORAL.name,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun resolvedMood(): AvatarMood =
    enumValues<AvatarMood>().firstOrNull { it.name == mood } ?: AvatarMood.SMILE

  fun resolvedBackdrop(): AvatarBackdrop =
    enumValues<AvatarBackdrop>().firstOrNull { it.name == backdrop } ?: AvatarBackdrop.CORAL
}

@Keep
@IgnoreExtraProperties
data class ChatMessage(
  @DocumentId val id: String = "",
  val userId: String = "",
  val sender: String = "user",
  val text: String = "",
  val moodTag: String = AvatarMood.SMILE.name,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun isFromPauline(): Boolean = sender == "pauline"

  fun resolvedMood(): AvatarMood =
    enumValues<AvatarMood>().firstOrNull { it.name == moodTag } ?: AvatarMood.SMILE
}

@Keep
@IgnoreExtraProperties
data class MemoryItem(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val detail: String = "",
  val source: String = "manual",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
) {
  fun isAutoLearned(): Boolean = source == "auto"
}
