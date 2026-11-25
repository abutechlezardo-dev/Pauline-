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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.PaulineAvatar

@Composable
fun PaulineScreen(
  uiState: AvatarUiState,
  onMoodSelected: (AvatarMood) -> Unit,
  onBackdropSelected: (AvatarBackdrop) -> Unit,
  onSayHello: () -> Unit,
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
