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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
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
  val animatedBackdrop by
    animateColorAsState(
      targetValue = backdrop.primaryColor,
      animationSpec = tween(durationMillis = 300),
      label = "avatar_backdrop_color",
    )
  val animatedSoftAccent by
    animateColorAsState(
      targetValue = backdrop.softAccent,
      animationSpec = tween(durationMillis = 300),
      label = "avatar_soft_accent",
    )

  val bounceScale = remember { Animatable(1f) }
  LaunchedEffect(waveCount) {
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
      initialValue = -3f,
      targetValue = 3f,
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
        .clip(CircleShape)
        .clickable(
          interactionSource = interactionSource,
          indication = ripple(bounded = true),
          role = Role.Button,
          onClick = onAvatarClick,
        )
        .semantics { contentDescription = description }
        .testTag("pauline_avatar")
        .padding(8.dp),
    contentAlignment = Alignment.Center,
  ) {
    Spacer(
      modifier =
        Modifier.fillMaxSize().drawWithCache {
          val diameter = size.minDimension
          val outerRadius = diameter / 2f
          val radius = outerRadius * 0.92f
          val center = Offset(size.width / 2f, size.height / 2f)
          val backdropBrush =
            Brush.radialGradient(
              colors = listOf(animatedBackdrop.copy(alpha = 0.88f), animatedBackdrop),
              center = Offset(center.x - outerRadius * 0.2f, center.y - outerRadius * 0.25f),
              radius = outerRadius * 1.1f,
            )
          val ringColor = animatedSoftAccent.copy(alpha = 0.7f)
          val geometry = buildPaulineGeometry(center = center, radius = radius)

          onDrawBehind {
            drawCircle(
              color = ringColor,
              radius = outerRadius,
              center = center,
            )
            drawCircle(
              brush = backdropBrush,
              radius = radius,
              center = center,
            )

            val yShift = floatOffset.dp.toPx() * 0.6f
            scale(scale = bounceScale.value, pivot = center) {
              translate(top = yShift) {
                drawCachedPaulineCharacter(
                  center = center,
                  radius = radius,
                  mood = mood,
                  geometry = geometry,
                )
              }
            }
          }
        }
    )
  }
}

private class PaulineGeometry(
  val shoulderPath: Path,
  val hairBackPath: Path,
  val bangsPath: Path,
  val happyMouthPath: Path,
  val collarStroke: Stroke,
  val eyeStroke: Stroke,
  val noseStroke: Stroke,
  val smileStroke: Stroke,
  val happyMouthStroke: Stroke,
  val calmMouthStroke: Stroke,
)

private fun buildPaulineGeometry(center: Offset, radius: Float): PaulineGeometry {
  val shoulderPath =
    Path().apply {
      moveTo(center.x - radius * 0.56f, center.y + radius * 0.76f)
      cubicTo(
        center.x - radius * 0.50f,
        center.y + radius * 0.42f,
        center.x - radius * 0.24f,
        center.y + radius * 0.36f,
        center.x,
        center.y + radius * 0.36f,
      )
      cubicTo(
        center.x + radius * 0.24f,
        center.y + radius * 0.36f,
        center.x + radius * 0.50f,
        center.y + radius * 0.42f,
        center.x + radius * 0.56f,
        center.y + radius * 0.76f,
      )
      cubicTo(
        center.x + radius * 0.25f,
        center.y + radius * 0.92f,
        center.x - radius * 0.25f,
        center.y + radius * 0.92f,
        center.x - radius * 0.56f,
        center.y + radius * 0.76f,
      )
      close()
    }

  val hairBackPath =
    Path().apply {
      moveTo(center.x - radius * 0.42f, center.y + radius * 0.26f)
      cubicTo(
        center.x - radius * 0.47f,
        center.y - radius * 0.35f,
        center.x - radius * 0.28f,
        center.y - radius * 0.62f,
        center.x,
        center.y - radius * 0.62f,
      )
      cubicTo(
        center.x + radius * 0.28f,
        center.y - radius * 0.62f,
        center.x + radius * 0.47f,
        center.y - radius * 0.35f,
        center.x + radius * 0.42f,
        center.y + radius * 0.26f,
      )
      cubicTo(
        center.x + radius * 0.36f,
        center.y + radius * 0.38f,
        center.x - radius * 0.36f,
        center.y + radius * 0.38f,
        center.x - radius * 0.42f,
        center.y + radius * 0.26f,
      )
      close()
    }

  val bangsPath =
    Path().apply {
      moveTo(center.x - radius * 0.33f, center.y - radius * 0.20f)
      cubicTo(
        center.x - radius * 0.34f,
        center.y - radius * 0.48f,
        center.x - radius * 0.10f,
        center.y - radius * 0.56f,
        center.x + radius * 0.14f,
        center.y - radius * 0.53f,
      )
      cubicTo(
        center.x + radius * 0.30f,
        center.y - radius * 0.49f,
        center.x + radius * 0.35f,
        center.y - radius * 0.30f,
        center.x + radius * 0.31f,
        center.y - radius * 0.22f,
      )
      cubicTo(
        center.x + radius * 0.20f,
        center.y - radius * 0.32f,
        center.x + radius * 0.12f,
        center.y - radius * 0.38f,
        center.x + radius * 0.08f,
        center.y - radius * 0.42f,
      )
      cubicTo(
        center.x - radius * 0.06f,
        center.y - radius * 0.27f,
        center.x - radius * 0.24f,
        center.y - radius * 0.22f,
        center.x - radius * 0.33f,
        center.y - radius * 0.20f,
      )
      close()
    }

  val mouthTopY = center.y + radius * 0.04f
  val happyMouthPath =
    Path().apply {
      moveTo(center.x - radius * 0.095f, mouthTopY + radius * 0.03f)
      quadraticTo(
        center.x,
        mouthTopY + radius * 0.015f,
        center.x + radius * 0.095f,
        mouthTopY + radius * 0.03f,
      )
      cubicTo(
        center.x + radius * 0.085f,
        mouthTopY + radius * 0.135f,
        center.x - radius * 0.085f,
        mouthTopY + radius * 0.135f,
        center.x - radius * 0.095f,
        mouthTopY + radius * 0.03f,
      )
      close()
    }

  return PaulineGeometry(
    shoulderPath = shoulderPath,
    hairBackPath = hairBackPath,
    bangsPath = bangsPath,
    happyMouthPath = happyMouthPath,
    collarStroke = Stroke(width = radius * 0.032f, cap = StrokeCap.Round),
    eyeStroke = Stroke(width = radius * 0.032f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    noseStroke = Stroke(width = radius * 0.02f, cap = StrokeCap.Round),
    smileStroke = Stroke(width = radius * 0.03f, cap = StrokeCap.Round),
    happyMouthStroke = Stroke(width = radius * 0.024f, join = StrokeJoin.Round),
    calmMouthStroke = Stroke(width = radius * 0.026f, cap = StrokeCap.Round),
  )
}

private fun DrawScope.drawCachedPaulineCharacter(
  center: Offset,
  radius: Float,
  mood: AvatarMood,
  geometry: PaulineGeometry,
) {
  val hairColor = Color(0xFF4A312C)
  val hairHighlight = Color(0xFF63443D)
  val skinColor = Color(0xFFFAD0B5)
  val skinShadow = Color(0xFFF0B896)
  val sweaterColor = Color(0xFFFFF8F2)
  val collarAccent = Color(0xFFE9967A)
  val eyeColor = Color(0xFF322320)
  val cheekColor = Color(0xFFF39C8D)

  drawPath(path = geometry.shoulderPath, color = sweaterColor)

  drawArc(
    color = collarAccent.copy(alpha = 0.45f),
    startAngle = 15f,
    sweepAngle = 150f,
    useCenter = false,
    topLeft = Offset(center.x - radius * 0.20f, center.y + radius * 0.28f),
    size = Size(radius * 0.40f, radius * 0.22f),
    style = geometry.collarStroke,
  )

  drawPath(path = geometry.hairBackPath, color = hairColor)

  drawRoundRect(
    color = skinShadow,
    topLeft = Offset(center.x - radius * 0.11f, center.y + radius * 0.15f),
    size = Size(radius * 0.22f, radius * 0.28f),
    cornerRadius = CornerRadius(radius * 0.10f, radius * 0.10f),
  )

  val earRadius = radius * 0.068f
  drawCircle(
    color = skinShadow,
    radius = earRadius,
    center = Offset(center.x - radius * 0.30f, center.y - radius * 0.05f),
  )
  drawCircle(
    color = skinShadow,
    radius = earRadius,
    center = Offset(center.x + radius * 0.30f, center.y - radius * 0.05f),
  )

  drawOval(
    color = skinColor,
    topLeft = Offset(center.x - radius * 0.29f, center.y - radius * 0.44f),
    size = Size(radius * 0.58f, radius * 0.68f),
  )

  drawPath(path = geometry.bangsPath, color = hairHighlight)

  drawCircle(
    color = cheekColor.copy(alpha = 0.42f),
    radius = radius * 0.055f,
    center = Offset(center.x - radius * 0.17f, center.y + radius * 0.01f),
  )
  drawCircle(
    color = cheekColor.copy(alpha = 0.42f),
    radius = radius * 0.055f,
    center = Offset(center.x + radius * 0.17f, center.y + radius * 0.01f),
  )

  val eyeY = center.y - radius * 0.09f
  val leftEyeX = center.x - radius * 0.11f
  val rightEyeX = center.x + radius * 0.11f

  when (mood) {
    AvatarMood.SMILE -> {
      drawCircle(color = eyeColor, radius = radius * 0.034f, center = Offset(leftEyeX, eyeY))
      drawCircle(color = eyeColor, radius = radius * 0.034f, center = Offset(rightEyeX, eyeY))
      drawCircle(
        color = Color.White,
        radius = radius * 0.011f,
        center = Offset(leftEyeX - radius * 0.01f, eyeY - radius * 0.012f),
      )
      drawCircle(
        color = Color.White,
        radius = radius * 0.011f,
        center = Offset(rightEyeX - radius * 0.01f, eyeY - radius * 0.012f),
      )
    }
    AvatarMood.HAPPY -> {
      drawArc(
        color = eyeColor,
        startAngle = 195f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(leftEyeX - radius * 0.045f, eyeY - radius * 0.03f),
        size = Size(radius * 0.09f, radius * 0.07f),
        style = geometry.eyeStroke,
      )
      drawArc(
        color = eyeColor,
        startAngle = 195f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(rightEyeX - radius * 0.045f, eyeY - radius * 0.03f),
        size = Size(radius * 0.09f, radius * 0.07f),
        style = geometry.eyeStroke,
      )
    }
    AvatarMood.WINK -> {
      drawCircle(color = eyeColor, radius = radius * 0.034f, center = Offset(leftEyeX, eyeY))
      drawCircle(
        color = Color.White,
        radius = radius * 0.011f,
        center = Offset(leftEyeX - radius * 0.01f, eyeY - radius * 0.012f),
      )
      drawArc(
        color = eyeColor,
        startAngle = 195f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(rightEyeX - radius * 0.045f, eyeY - radius * 0.02f),
        size = Size(radius * 0.09f, radius * 0.06f),
        style = geometry.eyeStroke,
      )
    }
    AvatarMood.CALM -> {
      drawArc(
        color = eyeColor,
        startAngle = 15f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(leftEyeX - radius * 0.042f, eyeY - radius * 0.025f),
        size = Size(radius * 0.084f, radius * 0.055f),
        style = geometry.eyeStroke,
      )
      drawArc(
        color = eyeColor,
        startAngle = 15f,
        sweepAngle = 150f,
        useCenter = false,
        topLeft = Offset(rightEyeX - radius * 0.042f, eyeY - radius * 0.025f),
        size = Size(radius * 0.084f, radius * 0.055f),
        style = geometry.eyeStroke,
      )
    }
  }

  drawArc(
    color = skinShadow,
    startAngle = 20f,
    sweepAngle = 140f,
    useCenter = false,
    topLeft = Offset(center.x - radius * 0.025f, center.y - radius * 0.02f),
    size = Size(radius * 0.05f, radius * 0.04f),
    style = geometry.noseStroke,
  )

  val mouthTopY = center.y + radius * 0.04f
  when (mood) {
    AvatarMood.SMILE,
    AvatarMood.WINK -> {
      drawArc(
        color = eyeColor,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.085f, mouthTopY),
        size = Size(radius * 0.17f, radius * 0.095f),
        style = geometry.smileStroke,
      )
    }
    AvatarMood.HAPPY -> {
      drawPath(path = geometry.happyMouthPath, color = Color.White)
      drawPath(
        path = geometry.happyMouthPath,
        color = eyeColor,
        style = geometry.happyMouthStroke,
      )
    }
    AvatarMood.CALM -> {
      drawArc(
        color = eyeColor,
        startAngle = 30f,
        sweepAngle = 120f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.06f, mouthTopY + radius * 0.02f),
        size = Size(radius * 0.12f, radius * 0.055f),
        style = geometry.calmMouthStroke,
      )
    }
  }
}
