package com.example.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.MemoryItem
import com.example.data.UiState

@Composable
fun MemoriesScreen(
  memoriesState: UiState<List<MemoryItem>>,
  onAddMemory: (title: String, detail: String) -> Unit,
  onUpdateMemory: (memoryId: String, title: String, detail: String) -> Unit,
  onDeleteMemory: (memoryId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var editingMemory by remember { mutableStateOf<MemoryItem?>(null) }
  var showAddDialog by remember { mutableStateOf(false) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = { showAddDialog = true },
        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        text = { Text(stringResource(R.string.memories_add_button)) },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("add_memory_fab"),
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .padding(innerPadding)
    ) {
      Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
        Text(
          text = stringResource(R.string.memories_title),
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = stringResource(R.string.memories_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      when (memoriesState) {
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
                text = memoriesState.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp),
              )
            }
          }
        }
        is UiState.Success -> {
          val memories = memoriesState.data
          if (memories.isEmpty()) {
            Box(
              modifier = Modifier.fillMaxSize().padding(28.dp),
              contentAlignment = Alignment.Center,
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 340.dp),
              ) {
                Surface(
                  shape = RoundedCornerShape(24.dp),
                  color = MaterialTheme.colorScheme.secondaryContainer,
                  modifier = Modifier.size(72.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Rounded.Psychology,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.size(36.dp),
                    )
                  }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                  text = stringResource(R.string.memories_empty_title),
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onBackground,
                  textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = stringResource(R.string.memories_empty_subtitle),
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                )
              }
            }
          } else {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              items(items = memories, key = { it.id.ifEmpty { it.title } }) { memory ->
                MemoryCard(
                  memory = memory,
                  onEdit = { editingMemory = memory },
                  onDelete = { onDeleteMemory(memory.id) },
                )
              }
            }
          }
        }
      }
    }
  }

  if (showAddDialog) {
    MemoryEditorDialog(
      dialogTitle = stringResource(R.string.memories_dialog_add_title),
      initialTitle = "",
      initialDetail = "",
      onDismiss = { showAddDialog = false },
      onSave = { title, detail ->
        showAddDialog = false
        onAddMemory(title, detail)
      },
    )
  }

  val targetMemory = editingMemory
  if (targetMemory != null) {
    MemoryEditorDialog(
      dialogTitle = stringResource(R.string.memories_dialog_edit_title),
      initialTitle = targetMemory.title,
      initialDetail = targetMemory.detail,
      onDismiss = { editingMemory = null },
      onSave = { title, detail ->
        editingMemory = null
        onUpdateMemory(targetMemory.id, title, detail)
      },
    )
  }
}

@Composable
private fun MemoryCard(
  memory: MemoryItem,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    modifier = Modifier.fillMaxWidth().testTag("memory_card_${memory.id}"),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color =
            if (memory.isAutoLearned()) {
              MaterialTheme.colorScheme.secondaryContainer
            } else {
              MaterialTheme.colorScheme.primaryContainer
            },
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector =
                if (memory.isAutoLearned()) Icons.Rounded.AutoAwesome else Icons.Rounded.Bookmark,
              contentDescription = null,
              tint =
                if (memory.isAutoLearned()) {
                  MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                  MaterialTheme.colorScheme.onPrimaryContainer
                },
              modifier = Modifier.size(14.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text =
                if (memory.isAutoLearned()) {
                  stringResource(R.string.memories_badge_auto)
                } else {
                  stringResource(R.string.memories_badge_manual)
                },
              style = MaterialTheme.typography.labelMedium,
              color =
                if (memory.isAutoLearned()) {
                  MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                  MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
          }
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
          onClick = onEdit,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_memory_${memory.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = stringResource(R.string.memories_edit_cd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        IconButton(
          onClick = onDelete,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_memory_${memory.id}"),
        ) {
          Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = stringResource(R.string.memories_delete_cd),
            tint = MaterialTheme.colorScheme.error,
          )
        }
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = memory.title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = memory.detail,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun MemoryEditorDialog(
  dialogTitle: String,
  initialTitle: String,
  initialDetail: String,
  onDismiss: () -> Unit,
  onSave: (title: String, detail: String) -> Unit,
) {
  var title by remember { mutableStateOf(initialTitle) }
  var detail by remember { mutableStateOf(initialDetail) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(dialogTitle) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { if (it.length <= 120) title = it },
          label = { Text(stringResource(R.string.memories_field_title)) },
          placeholder = { Text(stringResource(R.string.memories_field_title_hint)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("memory_title_input"),
        )
        OutlinedTextField(
          value = detail,
          onValueChange = { if (it.length <= 1000) detail = it },
          label = { Text(stringResource(R.string.memories_field_detail)) },
          placeholder = { Text(stringResource(R.string.memories_field_detail_hint)) },
          minLines = 2,
          maxLines = 5,
          modifier = Modifier.fillMaxWidth().testTag("memory_detail_input"),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onSave(title.trim(), detail.trim()) },
        enabled = title.isNotBlank() && detail.isNotBlank(),
        modifier = Modifier.testTag("save_memory_button"),
      ) {
        Text(stringResource(R.string.action_save))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.action_cancel))
      }
    },
  )
}
