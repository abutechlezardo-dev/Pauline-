package com.example.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.ai.PaulineAiService
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.PaulineRepository
import com.example.data.UiState
import com.example.voice.PaulineTextToSpeechController
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

enum class PaulineDestination(
  @StringRes val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  CHAT(R.string.nav_chat, Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline),
  AVATAR(R.string.nav_avatar, Icons.Rounded.Face, Icons.Outlined.Face),
  MEMORIES(R.string.nav_memories, Icons.Rounded.Psychology, Icons.Outlined.Psychology),
}

@Composable
fun PaulineAppRoot(auth: FirebaseAuth? = null) {
  var resolvedAuth by remember(auth) { mutableStateOf(auth) }

  LaunchedEffect(auth) {
    if (resolvedAuth == null) {
      withFrameNanos {}
      resolvedAuth = withContext(Dispatchers.IO) { Firebase.auth }
    }
  }

  val activeAuth = resolvedAuth
  if (activeAuth == null) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
      ) {
        CircularProgressIndicator()
      }
    }
    return
  }

  PaulineAuthAwareRoot(auth = activeAuth)
}

@Composable
private fun PaulineAuthAwareRoot(auth: FirebaseAuth) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  var autoSignInAttempted by rememberSaveable { mutableStateOf(false) }

  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

  LaunchedEffect(auth, autoSignInAttempted) {
    if (!autoSignInAttempted && auth.currentUser == null) {
      autoSignInAttempted = true
      withFrameNanos {}
      delay(200)
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = {},
        onUnauthenticated = {},
        scope = scope,
      )
    }
  }

  val user = currentUser
  if (user == null) {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
      AuthScreen(
        onAuthSuccess = {},
        modifier = Modifier.padding(innerPadding),
      )
    }
  } else {
    key(user.uid) {
      AuthenticatedPaulineShell(
        currentUserId = user.uid,
        userDisplayName = user.displayName,
        onSignOut = {
          signOut(credentialManager = credentialManager, scope = scope)
        },
      )
    }
  }
}

@Composable
private fun AuthenticatedPaulineShell(
  currentUserId: String,
  userDisplayName: String?,
  onSignOut: () -> Unit,
) {
  val viewModel: PaulineViewModel =
    viewModel(
      key = currentUserId,
      factory =
        viewModelFactory {
          initializer {
            val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
            val repository = PaulineRepository(app.applicationContext)
            val aiService = PaulineAiService()
            PaulineViewModel(
              repository = repository,
              aiService = aiService,
              currentUserId = currentUserId,
              userDisplayName = userDisplayName,
            )
          }
        },
    )

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
  val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
  val memoriesCount = (memoriesState as? UiState.Success)?.data?.size ?: 0

  var selectedTab by rememberSaveable { mutableStateOf(PaulineDestination.CHAT) }
  var isVoiceOutputEnabled by rememberSaveable { mutableStateOf(true) }
  val latestVoiceOutputEnabled by rememberUpdatedState(isVoiceOutputEnabled)
  var isPaulineSpeaking by remember { mutableStateOf(false) }
  val snackbarHostState = remember { SnackbarHostState() }
  val shellContext = LocalContext.current

  val ttsController =
    remember(shellContext) {
      PaulineTextToSpeechController(
        context = shellContext,
        onSpeakingChanged = { speaking -> isPaulineSpeaking = speaking },
      )
    }

  DisposableEffect(ttsController) {
    onDispose { ttsController.shutdown() }
  }

  LaunchedEffect(viewModel, ttsController) {
    viewModel.completedReplies.collect { reply ->
      if (latestVoiceOutputEnabled) {
        ttsController.speak(reply)
      }
    }
  }

  LaunchedEffect(uiState.actionError) {
    val err = uiState.actionError
    if (err != null) {
      snackbarHostState.showSnackbar(err)
      viewModel.dismissActionError()
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val showBottomBar = !useRail && !(selectedTab == PaulineDestination.CHAT && isImeVisible)

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        if (showBottomBar) {
          NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            PaulineDestination.entries.forEach { dest ->
              val selected = dest == selectedTab
              NavigationBarItem(
                selected = selected,
                onClick = { selectedTab = dest },
                icon = {
                  DestinationIcon(
                    destination = dest,
                    selected = selected,
                    memoriesCount = memoriesCount,
                  )
                },
                label = { Text(stringResource(dest.labelRes)) },
                modifier = Modifier.testTag("nav_${dest.name.lowercase()}"),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
      ) {
        if (useRail) {
          NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = MaterialTheme.colorScheme.surface,
          ) {
            PaulineDestination.entries.forEach { dest ->
              val selected = dest == selectedTab
              NavigationRailItem(
                selected = selected,
                onClick = { selectedTab = dest },
                icon = {
                  DestinationIcon(
                    destination = dest,
                    selected = selected,
                    memoriesCount = memoriesCount,
                  )
                },
                label = { Text(stringResource(dest.labelRes)) },
                modifier = Modifier.testTag("nav_${dest.name.lowercase()}"),
              )
            }
          }
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          PaulineDestinationContent(
            selectedTab = selectedTab,
            uiState = uiState,
            messagesState = messagesState,
            memoriesState = memoriesState,
            viewModel = viewModel,
            isVoiceEnabled = isVoiceOutputEnabled,
            isSpeaking = isPaulineSpeaking,
            onToggleVoice = {
              val nextEnabled = !isVoiceOutputEnabled
              isVoiceOutputEnabled = nextEnabled
              if (!nextEnabled) {
                ttsController.stop()
              }
            },
            onStopSpeaking = { ttsController.stop() },
            onNavigateToMemories = { selectedTab = PaulineDestination.MEMORIES },
            onSignOut = {
              ttsController.stop()
              onSignOut()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun DestinationIcon(
  destination: PaulineDestination,
  selected: Boolean,
  memoriesCount: Int,
) {
  if (destination == PaulineDestination.AVATAR) {
    Image(
      painter = painterResource(id = R.drawable.pauline_avatar),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      alignment = Alignment.Center,
      modifier =
        Modifier
          .size(24.dp)
          .clip(CircleShape)
          .then(
            if (selected) {
              Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            } else {
              Modifier
            }
          ),
    )
    return
  }

  val iconVector = if (selected) destination.selectedIcon else destination.unselectedIcon
  if (destination == PaulineDestination.MEMORIES && memoriesCount > 0) {
    BadgedBox(badge = { Badge { Text(memoriesCount.toString()) } }) {
      Icon(imageVector = iconVector, contentDescription = null)
    }
  } else {
    Icon(imageVector = iconVector, contentDescription = null)
  }
}

@Composable
private fun PaulineDestinationContent(
  selectedTab: PaulineDestination,
  uiState: AvatarUiState,
  messagesState: UiState<List<com.example.data.ChatMessage>>,
  memoriesState: UiState<List<com.example.data.MemoryItem>>,
  viewModel: PaulineViewModel,
  isVoiceEnabled: Boolean,
  isSpeaking: Boolean,
  onToggleVoice: () -> Unit,
  onStopSpeaking: () -> Unit,
  onNavigateToMemories: () -> Unit,
  onSignOut: () -> Unit,
) {
  when (selectedTab) {
    PaulineDestination.CHAT -> {
      ChatScreen(
        uiState = uiState,
        messagesState = messagesState,
        isVoiceEnabled = isVoiceEnabled,
        isSpeaking = isSpeaking,
        onToggleVoice = onToggleVoice,
        onStopSpeaking = onStopSpeaking,
        onSendMessage = viewModel::sendMessage,
        onClearChat = viewModel::clearMessages,
        onAvatarClick = viewModel::sayHello,
        onOpenMemories = onNavigateToMemories,
        onSignOut = onSignOut,
        onDismissError = viewModel::dismissChatError,
        onRetryLastMessage = viewModel::retryLastMessage,
        onDismissMemoryNotice = viewModel::dismissMemoryNotice,
      )
    }
    PaulineDestination.AVATAR -> {
      PaulineScreen(
        uiState = uiState,
        onMoodSelected = viewModel::selectMood,
        onBackdropSelected = viewModel::selectBackdrop,
        onSayHello = viewModel::sayHello,
      )
    }
    PaulineDestination.MEMORIES -> {
      MemoriesScreen(
        memoriesState = memoriesState,
        onAddMemory = viewModel::addMemory,
        onUpdateMemory = viewModel::updateMemory,
        onDeleteMemory = viewModel::deleteMemory,
      )
    }
  }
}
