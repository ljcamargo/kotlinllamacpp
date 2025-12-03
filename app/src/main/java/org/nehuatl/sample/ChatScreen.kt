package org.nehuatl.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    currentModelPath: String?,
    onPickModel: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val generatedText by viewModel.generatedText.collectAsStateWithLifecycle()

    var promptInput by remember { mutableStateOf("") }
    var showModelDialog by remember { mutableStateOf(currentModelPath == null) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Load model when path is available
    LaunchedEffect(currentModelPath) {
        if (currentModelPath != null && state is GenerationState.Idle) {
            viewModel.loadModel(currentModelPath)
        }
    }

    // Show keyboard only when model is fully loaded and ready
    LaunchedEffect(state) {
        if (state is GenerationState.ModelLoaded) {
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (e: Exception) {
                // Focus request might fail if UI isn't ready yet
            }
        }
    }

    if (showModelDialog) {
        ModelPickerDialog(
            onPickFile = {
                showModelDialog = false
                onPickModel()
            },
            onDismiss = if (currentModelPath != null) {
                { showModelDialog = false }
            } else null
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding() // This ensures content resizes with keyboard
    ) {
        // Status bar stays at top
        StatusBar(
            state = state,
            currentModel = currentModelPath,
            onChangeModel = { showModelDialog = true },
            modifier = Modifier.padding(16.dp)
        )

        // Text display takes remaining space
        TextDisplay(
            text = generatedText,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        // Prompt input stays at bottom
        PromptInput(
            prompt = promptInput,
            onPromptChange = { promptInput = it },
            onGenerate = {
                if (state.canGenerate() && promptInput.isNotBlank()) {
                    keyboardController?.hide()
                    viewModel.generate(promptInput)
                    promptInput = ""
                }
            },
            onAbort = {
                keyboardController?.hide()
                viewModel.abort()
            },
            enabled = state.canGenerate(),
            isGenerating = state.isGenerating(),
            focusRequester = focusRequester,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ModelPickerDialog(
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
    currentModel: String?,
    onChangeModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                        Text(if (currentModel == null) "Select a model" else "Ready")
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
                            "✓ Done (${state.tokenCount} tokens, ${state.durationMs}ms)",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is GenerationState.Error -> {
                        Text("⚠ ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (!state.isActive()) {
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
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when new text arrives
    LaunchedEffect(text) {
        if (text.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

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
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
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
    isGenerating: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            enabled = enabled && !isGenerating,
            placeholder = { Text("Enter your prompt...") },
            maxLines = 3,
            singleLine = false
        )

        if (isGenerating) {
            Button(
                onClick = onAbort,
                enabled = true  // Always enabled when generating
            ) {
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