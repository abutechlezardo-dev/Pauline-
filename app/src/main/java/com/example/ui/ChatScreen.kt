package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.ChatMessage
import com.example.data.UiState
import com.example.ui.components.PaulineAvatar
import com.example.voice.VoiceDictationController

@Composable
fun ChatScreen(
  uiState: AvatarUiState,
  messagesState: UiState<List<ChatMessage>>,
  isVoiceEnabled: Boolean,
  isSpeaking: Boolean,
  onToggleVoice: () -> Unit,
  onStopSpeaking: () -> Unit,
  onSendMessage: (String) -> Unit,
  onClearChat: () -> Unit,
  onAvatarClick: () -> Unit,
  onOpenMemories: () -> Unit,
  onSignOut: () -> Unit,
  onDismissError: () -> Unit,
  onRetryLastMessage: () -> Unit,
  onDismissMemoryNotice: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var draft by rememberSaveable { mutableStateOf("") }
  val latestDraft by rememberUpdatedState(draft)
  var isListening by remember { mutableStateOf(false) }
  var voiceError by remember { mutableStateOf<String?>(null) }
  var showClearDialog by rememberSaveable { mutableStateOf(false) }
  val listState = rememberLazyListState()
  val latestOnStopSpeaking by rememberUpdatedState(onStopSpeaking)

  val voiceController =
    remember(context) {
      VoiceDictationController(
        context = context,
        onListeningChanged = { listening -> isListening = listening },
        onDraftUpdate = { updatedText -> draft = updatedText },
        onError = { message -> voiceError = message },
        onSpeechStarted = { latestOnStopSpeaking() },
      )
    }

  DisposableEffect(voiceController) {
    onDispose { voiceController.destroy() }
  }

  val permissionDeniedMessage = stringResource(R.string.voice_permission_denied)
  val recordAudioLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        voiceError = null
        latestOnStopSpeaking()
        voiceController.startListening(latestDraft)
      } else {
        voiceError = permissionDeniedMessage
      }
    }

  val messages = (messagesState as? UiState.Success)?.data ?: emptyList()
  val latestTotalItems by
    rememberUpdatedState(messages.size + if (uiState.streamingReply != null) 1 else 0)

  LaunchedEffect(messages.size, uiState.streamingReply) {
    if (latestTotalItems > 0) {
      listState.animateScrollToItem(latestTotalItems - 1)
    }
  }

  LaunchedEffect(listState) {
    var previousHeight = 0
    snapshotFlow { listState.layoutInfo.viewportSize.height }.collect { newHeight ->
      if (previousHeight > 0 && newHeight in 1 until previousHeight && latestTotalItems > 0) {
        listState.scrollToItem(latestTotalItems - 1)
      }
      if (newHeight > 0) {
        previousHeight = newHeight
      }
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    Image(
      painter = painterResource(id = R.drawable.pauline_wallpaper),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      alignment = BiasAlignment(0f, -0.35f),
      modifier = Modifier.fillMaxSize(),
    )
    Box(
      modifier =
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.70f))
    )
    Column(
      modifier = Modifier.fillMaxSize()
    ) {
    ChatHeader(
      uiState = uiState,
      hasMessages = messages.isNotEmpty(),
      isVoiceEnabled = isVoiceEnabled,
      isSpeaking = isSpeaking,
      onToggleVoice = onToggleVoice,
      onAvatarClick = onAvatarClick,
      onClearClick = { showClearDialog = true },
      onSignOut = onSignOut,
    )

    AnimatedVisibility(visible = uiState.lastSavedMemoryTitle != null) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable {
              onDismissMemoryNotice()
              onOpenMemories()
            }
            .testTag("memory_saved_banner"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text =
              "${stringResource(R.string.chat_memory_saved_banner)} : ${uiState.lastSavedMemoryTitle.orEmpty()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
          )
          IconButton(
            onClick = onDismissMemoryNotice,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.action_cancel),
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    AnimatedVisibility(visible = uiState.chatError != null) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = uiState.chatError.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
          )
          if (uiState.lastFailedMessage != null) {
            TextButton(
              onClick = onRetryLastMessage,
              modifier = Modifier.testTag("retry_chat_button"),
            ) {
              Text(
                text = stringResource(R.string.action_retry),
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
            }
          }
          IconButton(
            onClick = onDismissError,
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.action_cancel),
              tint = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      when (messagesState) {
        is UiState.Loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
        is UiState.Error -> {
          Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Surface(
              shape = RoundedCornerShape(20.dp),
              color = MaterialTheme.colorScheme.errorContainer,
            ) {
              Text(
                text = messagesState.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp),
              )
            }
          }
        }
        is UiState.Success -> {
          if (messages.isEmpty() && uiState.streamingReply == null) {
            EmptyChatWelcome(
              uiState = uiState,
              onAvatarClick = onAvatarClick,
              onSuggestionClick = onSendMessage,
            )
          } else {
            LazyColumn(
              state = listState,
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              items(items = messages, key = { it.id.ifEmpty { it.hashCode().toString() } }) { msg ->
                ChatBubble(
                  text = msg.text,
                  isFromPauline = msg.isFromPauline(),
                  backdrop = uiState.backdrop,
                )
              }
              if (uiState.streamingReply != null) {
                item(key = "streaming_reply") {
                  ChatBubble(
                    text =
                      uiState.streamingReply.ifBlank { stringResource(R.string.chat_status_typing) },
                    isFromPauline = true,
                    backdrop = uiState.backdrop,
                  )
                }
              }
            }
          }
        }
      }
    }

    AnimatedVisibility(visible = voiceError != null) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = voiceError.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.weight(1f),
          )
          IconButton(
            onClick = { voiceError = null },
            modifier = Modifier.minimumInteractiveComponentSize(),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = stringResource(R.string.action_cancel),
              tint = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    AnimatedVisibility(visible = isListening) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("voice_listening_banner"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(
            imageVector = Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.voice_listening_status),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }
    }

    ChatInputBar(
      draft = draft,
      isSending = uiState.isSending,
      isListening = isListening,
      onDraftChange = { newValue ->
        voiceError = null
        if (newValue != draft && newValue.isNotEmpty()) {
          latestOnStopSpeaking()
        }
        draft = newValue
      },
      onToggleMic = {
        voiceError = null
        if (voiceController.isListening()) {
          voiceController.stopListening()
        } else {
          latestOnStopSpeaking()
          val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
              PackageManager.PERMISSION_GRANTED
          if (granted) {
            voiceController.startListening(latestDraft)
          } else {
            recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
          }
        }
      },
      onSend = {
        if (voiceController.isListening()) {
          voiceController.stopListening()
        }
        val text = draft.trim()
        if (text.isNotEmpty() && !uiState.isSending) {
          latestOnStopSpeaking()
          draft = ""
          onSendMessage(text)
        }
      },
    )
    }
  }

  if (showClearDialog) {
    AlertDialog(
      onDismissRequest = { showClearDialog = false },
      title = { Text(stringResource(R.string.chat_clear_title)) },
      text = { Text(stringResource(R.string.chat_clear_body)) },
      confirmButton = {
        TextButton(
          onClick = {
            showClearDialog = false
            latestOnStopSpeaking()
            onClearChat()
          }
        ) {
          Text(stringResource(R.string.action_delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearDialog = false }) {
          Text(stringResource(R.string.action_cancel))
        }
      },
    )
  }
}

@Composable
private fun ChatHeader(
  uiState: AvatarUiState,
  hasMessages: Boolean,
  isVoiceEnabled: Boolean,
  isSpeaking: Boolean,
  onToggleVoice: () -> Unit,
  onAvatarClick: () -> Unit,
  onClearClick: () -> Unit,
  onSignOut: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
    tonalElevation = 2.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PaulineAvatar(
        mood = uiState.mood,
        backdrop = uiState.backdrop,
        waveCount = uiState.waveCount,
        onAvatarClick = onAvatarClick,
        modifier = Modifier.size(56.dp),
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text =
            when {
              isSpeaking -> stringResource(R.string.chat_status_speaking)
              uiState.isSending -> stringResource(R.string.chat_status_typing)
              else -> stringResource(R.string.chat_status_online)
            },
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.secondary,
        )
      }
      IconButton(
        onClick = onToggleVoice,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("voice_output_toggle_button"),
      ) {
        Icon(
          imageVector =
            if (isVoiceEnabled) {
              Icons.AutoMirrored.Rounded.VolumeUp
            } else {
              Icons.AutoMirrored.Rounded.VolumeOff
            },
          contentDescription =
            if (isVoiceEnabled) {
              stringResource(R.string.voice_output_mute_cd)
            } else {
              stringResource(R.string.voice_output_enable_cd)
            },
          tint =
            if (isVoiceEnabled) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
      }
      if (hasMessages) {
        IconButton(
          onClick = onClearClick,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_chat_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteSweep,
            contentDescription = stringResource(R.string.chat_clear_cd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      IconButton(
        onClick = onSignOut,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("sign_out_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.Logout,
          contentDescription = stringResource(R.string.auth_sign_out),
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun EmptyChatWelcome(
  uiState: AvatarUiState,
  onAvatarClick: () -> Unit,
  onSuggestionClick: (String) -> Unit,
) {
  val chip1 = stringResource(R.string.chat_chip_1)
  val chip2 = stringResource(R.string.chat_chip_2)
  val chip3 = stringResource(R.string.chat_chip_3)

  Box(
    modifier = Modifier.fillMaxSize().padding(20.dp),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(26.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
      tonalElevation = 2.dp,
      modifier = Modifier.widthIn(max = 380.dp),
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        PaulineAvatar(
          mood = uiState.mood,
          backdrop = uiState.backdrop,
          waveCount = uiState.waveCount,
          onAvatarClick = onAvatarClick,
          modifier = Modifier.size(128.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = stringResource(R.string.chat_empty_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = stringResource(R.string.chat_empty_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(18.dp))
        listOf(chip1, chip2, chip3).forEachIndexed { index, prompt ->
          SuggestionChip(
            onClick = { onSuggestionClick(prompt) },
            label = { Text(prompt) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("chat_suggestion_$index"),
          )
        }
      }
    }
  }
}

@Composable
private fun ChatBubble(
  text: String,
  isFromPauline: Boolean,
  backdrop: AvatarBackdrop,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isFromPauline) Arrangement.Start else Arrangement.End,
  ) {
    Surface(
      shape =
        RoundedCornerShape(
          topStart = 20.dp,
          topEnd = 20.dp,
          bottomStart = if (isFromPauline) 6.dp else 20.dp,
          bottomEnd = if (isFromPauline) 20.dp else 6.dp,
        ),
      color =
        if (isFromPauline) {
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.90f)
        } else {
          MaterialTheme.colorScheme.primary
        },
      tonalElevation = if (isFromPauline) 2.dp else 0.dp,
      modifier = Modifier.widthIn(max = 320.dp),
    ) {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (isFromPauline) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
              painter = painterResource(id = R.drawable.pauline_avatar),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              alignment = Alignment.Center,
              modifier =
                Modifier
                  .size(22.dp)
                  .clip(CircleShape)
                  .border(1.dp, backdrop.primaryColor, CircleShape),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
          text = text,
          style = MaterialTheme.typography.bodyLarge,
          color =
            if (isFromPauline) {
              MaterialTheme.colorScheme.onSurface
            } else {
              MaterialTheme.colorScheme.onPrimary
            },
        )
      }
    }
  }
}

@Composable
private fun ChatInputBar(
  draft: String,
  isSending: Boolean,
  isListening: Boolean,
  onDraftChange: (String) -> Unit,
  onToggleMic: () -> Unit,
  onSend: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    tonalElevation = 4.dp,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedTextField(
        value = draft,
        onValueChange = onDraftChange,
        placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
        singleLine = false,
        maxLines = 4,
        shape = RoundedCornerShape(24.dp),
        keyboardOptions =
          KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Send,
          ),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
          ),
        modifier = Modifier.weight(1f).testTag("chat_input_field"),
      )

      FilledTonalIconButton(
        onClick = onToggleMic,
        enabled = !isSending,
        colors =
          if (isListening) {
            IconButtonDefaults.filledTonalIconButtonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            )
          } else {
            IconButtonDefaults.filledTonalIconButtonColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          },
        modifier = Modifier.size(48.dp).testTag("chat_mic_button"),
      ) {
        Icon(
          imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
          contentDescription =
            if (isListening) {
              stringResource(R.string.voice_mic_stop_cd)
            } else {
              stringResource(R.string.voice_mic_start_cd)
            },
        )
      }

      FilledIconButton(
        onClick = onSend,
        enabled = draft.isNotBlank() && !isSending,
        colors =
          IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        modifier = Modifier.size(48.dp).testTag("chat_send_button"),
      ) {
        if (isSending) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            strokeWidth = 2.dp,
          )
        } else {
          Icon(
            imageVector = Icons.AutoMirrored.Rounded.Send,
            contentDescription = stringResource(R.string.chat_send_cd),
          )
        }
      }
    }
  }
}
