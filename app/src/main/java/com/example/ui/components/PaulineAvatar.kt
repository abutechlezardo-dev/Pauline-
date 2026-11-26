package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.AvatarBackdrop
import com.example.ui.AvatarMood

@Composable
fun PaulineAvatar(
  mood: AvatarMood,
  backdrop: AvatarBackdrop,
  waveCount: Int,
  onAvatarClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val description = stringResource(R.string.avatar_content_description)
  val animatedRingColor by
    animateColorAsState(
      targetValue = backdrop.primaryColor,
      animationSpec = tween(durationMillis = 300),
      label = "avatar_ring_color",
    )
  val animatedSoftAccent by
    animateColorAsState(
      targetValue = backdrop.softAccent,
      animationSpec = tween(durationMillis = 300),
      label = "avatar_soft_accent",
    )

  val bounceScale = remember { Animatable(1f) }
  LaunchedEffect(waveCount, mood) {
    if (waveCount > 0) {
      bounceScale.animateTo(
        targetValue = 1.08f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f),
      )
      bounceScale.animateTo(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = 300f),
      )
    }
  }

  val infiniteTransition = rememberInfiniteTransition(label = "avatar_idle")
  val floatOffset by
    infiniteTransition.animateFloat(
      initialValue = -2f,
      targetValue = 2f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "avatar_float",
    )

  val interactionSource = remember { MutableInteractionSource() }

  Box(
    modifier =
      modifier
        .aspectRatio(1f)
        .graphicsLayer {
          scaleX = bounceScale.value
          scaleY = bounceScale.value
          translationY = floatOffset.dp.toPx() * 0.45f
        }
        .clip(CircleShape)
        .background(animatedSoftAccent.copy(alpha = 0.25f))
        .border(width = 3.dp, color = animatedRingColor, shape = CircleShape)
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(bounded = true),
          role = Role.Button,
          onClick = onAvatarClick,
        )
        .semantics { contentDescription = description }
        .testTag("pauline_avatar")
        .padding(3.dp),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = painterResource(id = R.drawable.pauline_avatar),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      alignment = Alignment.Center,
      modifier = Modifier.fillMaxSize().clip(CircleShape),
    )
  }
}
