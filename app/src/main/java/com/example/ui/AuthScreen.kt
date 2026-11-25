package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.auth.GoogleSignInButton
import com.example.ui.components.PaulineAvatar

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var authError by remember { mutableStateOf<String?>(null) }
  var previewWaveCount by remember { mutableStateOf(0) }
  val auraColor = AvatarBackdrop.CORAL.primaryColor.copy(alpha = 0.16f)

  Box(
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
        .padding(horizontal = 24.dp, vertical = 28.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .widthIn(max = 420.dp)
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      PaulineAvatar(
        mood = AvatarMood.HAPPY,
        backdrop = AvatarBackdrop.CORAL,
        waveCount = previewWaveCount,
        onAvatarClick = { previewWaveCount += 1 },
        modifier = Modifier.size(200.dp),
      )

      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = stringResource(R.string.auth_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = stringResource(R.string.auth_subtitle),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(modifier = Modifier.height(28.dp))

      if (authError != null) {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.errorContainer,
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
          Text(
            text = authError.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(14.dp),
          )
        }
      }

      GoogleSignInButton(
        onAuthSuccess = {
          authError = null
          onAuthSuccess()
        },
        onAuthError = { message -> authError = message },
      )
    }
  }
}
