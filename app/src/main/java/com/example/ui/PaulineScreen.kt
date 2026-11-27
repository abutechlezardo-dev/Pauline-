package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.text.format.Formatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.PaulineAvatar
import com.example.voice.PaulineVoiceSettingsState
import com.example.voice.PiperModelState
import com.example.voice.VoiceEnginePreference

@Composable
fun PaulineScreen(
  uiState: AvatarUiState,
  voiceSettingsState: PaulineVoiceSettingsState,
  isSpeaking: Boolean,
  onMoodSelected: (AvatarMood) -> Unit,
  onBackdropSelected: (AvatarBackdrop) -> Unit,
  onSayHello: () -> Unit,
  onSelectVoiceEngine: (VoiceEnginePreference) -> Unit,
  onStartPiperDownload: () -> Unit,
  onCancelPiperDownload: () -> Unit,
  onDeletePiperModel: () -> Unit,
  onTestVoiceSample: (String) -> Unit,
  onStopSpeaking: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val auraColor by
    animateColorAsState(
      targetValue = uiState.backdrop.primaryColor.copy(alpha = 0.16f),
      animationSpec = tween(durationMillis = 350),
      label = "ambient_aura",
    )

  BoxWithConstraints(
    modifier =
      modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
        .drawWithCache {
          val center = Offset(size.width * 0.5f, size.height * 0.34f)
          val radius = size.minDimension * 0.72f
          val brush =
            Brush.radialGradient(
              colors = listOf(auraColor, Color.Transparent),
              center = center,
              radius = radius,
            )
          onDrawBehind {
            drawCircle(
              brush = brush,
              radius = radius,
              center = center,
            )
          }
        }
  ) {
    val isWideLayout = maxWidth >= 600.dp && maxWidth > maxHeight

    if (isWideLayout) {
      Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AvatarShowcasePane(
          uiState = uiState,
          onAvatarClick = onSayHello,
          modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        Column(
          modifier =
            Modifier
              .weight(1f)
              .widthIn(max = 460.dp)
              .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          ControlsPane(
            uiState = uiState,
            onMoodSelected = onMoodSelected,
            onBackdropSelected = onBackdropSelected,
            onSayHello = onSayHello,
            modifier = Modifier.fillMaxWidth(),
          )
          PaulineVoiceSettingsCard(
            voiceSettingsState = voiceSettingsState,
            isSpeaking = isSpeaking,
            onSelectVoiceEngine = onSelectVoiceEngine,
            onStartPiperDownload = onStartPiperDownload,
            onCancelPiperDownload = onCancelPiperDownload,
            onDeletePiperModel = onDeletePiperModel,
            onTestVoiceSample = onTestVoiceSample,
            onStopSpeaking = onStopSpeaking,
            modifier = Modifier.fillMaxWidth(),
          )
          PaulineProfileCard(modifier = Modifier.fillMaxWidth())
        }
      }
    } else {
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        HeaderSection(modifier = Modifier.fillMaxWidth())

        AvatarShowcasePane(
          uiState = uiState,
          onAvatarClick = onSayHello,
          modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        )

        ControlsPane(
          uiState = uiState,
          onMoodSelected = onMoodSelected,
          onBackdropSelected = onBackdropSelected,
          onSayHello = onSayHello,
          modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
        )

        PaulineVoiceSettingsCard(
          voiceSettingsState = voiceSettingsState,
          isSpeaking = isSpeaking,
          onSelectVoiceEngine = onSelectVoiceEngine,
          onStartPiperDownload = onStartPiperDownload,
          onCancelPiperDownload = onCancelPiperDownload,
          onDeletePiperModel = onDeletePiperModel,
          onTestVoiceSample = onTestVoiceSample,
          onStopSpeaking = onStopSpeaking,
          modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
        )

        PaulineProfileCard(modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp))
      }
    }
  }
}

@Composable
private fun HeaderSection(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = stringResource(R.string.app_name),
      style = MaterialTheme.typography.displaySmall,
      color = MaterialTheme.colorScheme.onBackground,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = stringResource(R.string.pauline_subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun AvatarShowcasePane(
  uiState: AvatarUiState,
  onAvatarClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val greetingText =
    if (uiState.isGreetingWave) {
      stringResource(R.string.greeting_wave)
    } else {
      stringResource(uiState.mood.greetingRes)
    }

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
      modifier = Modifier.padding(horizontal = 12.dp),
    ) {
      AnimatedContent(
        targetState = greetingText,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
        label = "greeting_bubble",
      ) { text ->
        Text(
          text = text,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    PaulineAvatar(
      mood = uiState.mood,
      backdrop = uiState.backdrop,
      waveCount = uiState.waveCount,
      onAvatarClick = onAvatarClick,
      modifier = Modifier.size(220.dp),
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
      text = stringResource(R.string.tap_hint),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsPane(
  uiState: AvatarUiState,
  onMoodSelected: (AvatarMood) -> Unit,
  onBackdropSelected: (AvatarBackdrop) -> Unit,
  onSayHello: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    tonalElevation = 3.dp,
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(R.string.section_mood),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AvatarMood.entries.forEach { mood ->
            val selected = mood == uiState.mood
            FilterChip(
              selected = selected,
              onClick = { onMoodSelected(mood) },
              label = { Text(stringResource(mood.labelRes)) },
              leadingIcon = {
                Icon(
                  imageVector = moodIcon(mood),
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
              },
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
              modifier =
                Modifier.minimumInteractiveComponentSize().testTag("mood_${mood.name.lowercase()}"),
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = stringResource(R.string.section_backdrop),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          AvatarBackdrop.entries.forEach { backdrop ->
            val selected = backdrop == uiState.backdrop
            val colorLabel = stringResource(backdrop.labelRes)
            Box(
              modifier =
                Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .clickable(role = Role.RadioButton) { onBackdropSelected(backdrop) }
                  .semantics { contentDescription = colorLabel }
                  .testTag("backdrop_${backdrop.name.lowercase()}"),
              contentAlignment = Alignment.Center,
            ) {
              Box(
                modifier =
                  Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(backdrop.primaryColor)
                    .then(
                      if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                      } else {
                        Modifier
                      }
                    ),
                contentAlignment = Alignment.Center,
              ) {
                if (selected) {
                  Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }
        }
      }

      Button(
        onClick = onSayHello,
        shape = RoundedCornerShape(16.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("say_hello_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.WavingHand,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.action_say_hello),
          style = MaterialTheme.typography.labelLarge,
        )
      }
    }
  }
}

private fun moodIcon(mood: AvatarMood): ImageVector =
  when (mood) {
    AvatarMood.SMILE -> Icons.Rounded.SentimentSatisfied
    AvatarMood.HAPPY -> Icons.Rounded.SentimentVerySatisfied
    AvatarMood.WINK -> Icons.Rounded.AutoAwesome
    AvatarMood.CALM -> Icons.Rounded.Spa
  }

@Composable
private fun PaulineProfileCard(modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier.testTag("pauline_profile_card"),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    tonalElevation = 3.dp,
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.profile_card_title),
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(R.string.profile_card_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      ProfileDetailRow(
        icon = Icons.Rounded.Favorite,
        label = stringResource(R.string.profile_role_label),
        value = stringResource(R.string.profile_role_value),
      )
      ProfileDetailRow(
        icon = Icons.Rounded.Restaurant,
        label = stringResource(R.string.profile_job_label),
        value = stringResource(R.string.profile_job_value),
      )
      ProfileDetailRow(
        icon = Icons.Rounded.Home,
        label = stringResource(R.string.profile_home_family_label),
        value = stringResource(R.string.profile_home_family_value),
      )
      ProfileDetailRow(
        icon = Icons.Rounded.BeachAccess,
        label = stringResource(R.string.profile_story_label),
        value = stringResource(R.string.profile_story_value),
      )
    }
  }
}

@Composable
private fun ProfileDetailRow(
  icon: ImageVector,
  label: String,
  value: String,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.primaryContainer,
      modifier = Modifier.size(34.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(18.dp),
        )
      }
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaulineVoiceSettingsCard(
  voiceSettingsState: PaulineVoiceSettingsState,
  isSpeaking: Boolean,
  onSelectVoiceEngine: (VoiceEnginePreference) -> Unit,
  onStartPiperDownload: () -> Unit,
  onCancelPiperDownload: () -> Unit,
  onDeletePiperModel: () -> Unit,
  onTestVoiceSample: (String) -> Unit,
  onStopSpeaking: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val samplePhrase = stringResource(R.string.voice_test_sample_phrase)

  Surface(
    modifier = modifier.testTag("pauline_voice_settings_card"),
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    tonalElevation = 3.dp,
  ) {
    Column(
      modifier = Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(38.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.RecordVoiceOver,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(20.dp),
            )
          }
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.voice_settings_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = stringResource(R.string.voice_settings_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      val activeStatusText =
        when {
          voiceSettingsState.usingFallbackRightNow -> stringResource(R.string.voice_active_fallback)
          voiceSettingsState.isUsingPiperEffective -> stringResource(R.string.voice_active_piper)
          else -> stringResource(R.string.voice_active_android)
        }

      Surface(
        shape = RoundedCornerShape(14.dp),
        color =
          if (voiceSettingsState.isUsingPiperEffective) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.secondaryContainer
          },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector =
              if (voiceSettingsState.isUsingPiperEffective) {
                Icons.Rounded.GraphicEq
              } else {
                Icons.Rounded.CheckCircle
              },
            contentDescription = null,
            tint =
              if (voiceSettingsState.isUsingPiperEffective) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onSecondaryContainer
              },
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = activeStatusText,
            style = MaterialTheme.typography.labelLarge,
            color =
              if (voiceSettingsState.isUsingPiperEffective) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onSecondaryContainer
              },
          )
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = voiceSettingsState.enginePreference == VoiceEnginePreference.PIPER_LOCAL,
          onClick = { onSelectVoiceEngine(VoiceEnginePreference.PIPER_LOCAL) },
          enabled = voiceSettingsState.isPiperReady,
          label = { Text(stringResource(R.string.voice_engine_piper)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("voice_engine_piper_chip"),
        )
        FilterChip(
          selected =
            voiceSettingsState.enginePreference == VoiceEnginePreference.ANDROID_SYSTEM ||
              !voiceSettingsState.isPiperReady,
          onClick = { onSelectVoiceEngine(VoiceEnginePreference.ANDROID_SYSTEM) },
          label = { Text(stringResource(R.string.voice_engine_android)) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("voice_engine_android_chip"),
        )
      }

      when (val modelState = voiceSettingsState.piperModelState) {
        is PiperModelState.NotDownloaded -> {
          Text(
            text = stringResource(R.string.voice_model_not_downloaded),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = stringResource(R.string.voice_model_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Button(
            onClick = onStartPiperDownload,
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("piper_download_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Download,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.voice_download_button))
          }
        }

        is PiperModelState.Downloading -> {
          val downloadedLabel = Formatter.formatShortFileSize(context, modelState.downloadedBytes)
          val progressLabel =
            if (modelState.progressPercent != null && modelState.totalBytes != null) {
              val totalLabel = Formatter.formatShortFileSize(context, modelState.totalBytes)
              stringResource(
                R.string.voice_model_downloading_percent,
                modelState.progressPercent,
                downloadedLabel,
                totalLabel,
              )
            } else {
              stringResource(R.string.voice_model_downloading_bytes, downloadedLabel)
            }

          Text(
            text = progressLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          if (modelState.progressPercent != null) {
            LinearProgressIndicator(
              progress = { (modelState.progressPercent / 100f).coerceIn(0f, 1f) },
              modifier = Modifier.fillMaxWidth().testTag("piper_download_progress"),
            )
          } else {
            LinearProgressIndicator(
              modifier = Modifier.fillMaxWidth().testTag("piper_download_progress"),
            )
          }
          OutlinedButton(
            onClick = onCancelPiperDownload,
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("piper_cancel_download_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.voice_cancel_download_button))
          }
        }

        is PiperModelState.Extracting -> {
          Text(
            text = stringResource(R.string.voice_model_extracting),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().testTag("piper_extract_progress"),
          )
        }

        is PiperModelState.Ready -> {
          val sizeLabel = Formatter.formatShortFileSize(context, modelState.sizeBytes)
          Text(
            text = stringResource(R.string.voice_model_ready, sizeLabel),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Button(
              onClick = {
                if (isSpeaking) {
                  onStopSpeaking()
                } else {
                  onTestVoiceSample(samplePhrase)
                }
              },
              shape = RoundedCornerShape(16.dp),
              modifier =
                Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .testTag("piper_test_voice_button"),
            ) {
              Icon(
                imageVector = if (isSpeaking) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text =
                  if (isSpeaking) {
                    stringResource(R.string.voice_stop_sample_button)
                  } else {
                    stringResource(R.string.voice_test_sample_button)
                  },
              )
            }
            OutlinedButton(
              onClick = onDeletePiperModel,
              shape = RoundedCornerShape(16.dp),
              modifier =
                Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("piper_delete_model_button"),
            ) {
              Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.voice_delete_model_button),
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }

        is PiperModelState.Error -> {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "${stringResource(R.string.voice_model_error_prefix)} ${modelState.message}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
            }
          }
          Button(
            onClick = onStartPiperDownload,
            shape = RoundedCornerShape(16.dp),
            modifier =
              Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("piper_retry_download_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Download,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.voice_retry_download_button))
          }
        }
      }
    }
  }
}
