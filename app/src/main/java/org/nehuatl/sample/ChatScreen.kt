package org.nehuatl.sample

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    currentModelUri: Uri?,
    onPickModel: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val generatedText by viewModel.generatedText.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var promptInput by remember { mutableStateOf("") }
    var showModelDialog by remember { mutableStateOf(currentModelUri == null) }

    LaunchedEffect(currentModelUri) {
        if (currentModelUri != null && state is GenerationState.Idle) {
            val path = currentModelUri.path ?: return@LaunchedEffect
            viewModel.loadModel(path)
        }
    }

    if (showModelDialog) {
        ModelPickerDialog(
            currentModel = currentModelUri,
            onPickFile = {
                showModelDialog = false
                onPickModel()
            },
            onDismiss = if (currentModelUri != null) {
                { showModelDialog = false }
            } else null
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusBar(
            state = state,
            currentModel = currentModelUri,
            onChangeModel = { showModelDialog = true }
        )

        TextDisplay(
            text = generatedText,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        PromptInput(
            prompt = promptInput,
            onPromptChange = { promptInput = it },
            onGenerate = {
                viewModel.generate(promptInput)
                promptInput = ""
            },
            onAbort = { viewModel.abort() },
            enabled = state.canGenerate(),
            isGenerating = state.isActive()
        )
    }
}

@Composable
private fun ModelPickerDialog(
    currentModel: Uri?,
    onPickFile: () -> Unit,
    onDismiss: (() -> Unit)?
) {
    Dialog(
        onDismissRequest = { onDismiss?.invoke() }
    ) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Select GGUF Model",
                    style = MaterialTheme.typography.headlineSmall
                )

                Text(
                    "Choose a .gguf model file from your device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onPickFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Browse Files")
                }

                if (onDismiss != null) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBar(
    state: GenerationState,
    currentModel: Uri?,
    onChangeModel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is GenerationState.Error -> MaterialTheme.colorScheme.errorContainer
                is GenerationState.Generating -> MaterialTheme.colorScheme.primaryContainer
                is GenerationState.LoadingModel -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                when (state) {
                    is GenerationState.Idle -> {
                        Text(if (currentModel == null) "Select a model" else "Loading...")
                    }
                    is GenerationState.LoadingModel -> {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text("Loading model...")
                    }
                    is GenerationState.ModelLoaded -> {
                        Text("✓ Ready", color = MaterialTheme.colorScheme.primary)
                    }
                    is GenerationState.Generating -> {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        Text("Generating... (${state.tokensGenerated} tokens)")
                    }
                    is GenerationState.Completed -> {
                        Text(
                            "✓ Completed (${state.tokenCount} tokens in ${state.durationMs}ms)",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is GenerationState.Error -> {
                        Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (state !is GenerationState.LoadingModel && state !is GenerationState.Generating) {
                TextButton(onClick = onChangeModel) {
                    Text("Change")
                }
            }
        }
    }
}

@Composable
private fun TextDisplay(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (text.isEmpty()) {
                Text(
                    "Generated text will appear here...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Text(
                    text = text,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun PromptInput(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onAbort: () -> Unit,
    enabled: Boolean,
    isGenerating: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.weight(1f),
            enabled = enabled && !isGenerating,
            placeholder = { Text("Enter your prompt...") },
            maxLines = 3
        )

        if (isGenerating) {
            Button(onClick = onAbort) {
                Text("Stop")
            }
        } else {
            Button(
                onClick = onGenerate,
                enabled = enabled && prompt.isNotBlank()
            ) {
                Text("Send")
            }
        }
    }
}