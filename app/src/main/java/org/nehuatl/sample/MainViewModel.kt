package org.nehuatl.sample

import android.content.ContentResolver
import android.util.Log
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.nehuatl.llamacpp.LlamaHelper

class MainViewModel(val contentResolver: ContentResolver): ViewModel() {

    private val viewModelJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + viewModelJob)
    private val llamaHelper by lazy { LlamaHelper(
        contentResolver = contentResolver,
        scope = scope
    )}

    private val _state = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val state = _state.asStateFlow()

    private val _generatedText = MutableStateFlow("")
    val generatedText = _generatedText.asStateFlow()

    fun loadModel(path: String) {
        if (_state.value is GenerationState.Generating) {
            Log.w("MainViewModel", "Cannot load model while generating")
            return
        }

        scope.launch {
            _state.value = GenerationState.LoadingModel
            try {
                val actualPath = if (path.startsWith("content://")) {
                    path
                } else {
                    path.removePrefix("file://")
                }
                llamaHelper.load(path = actualPath, contextLength = 2048)
                _state.value = GenerationState.ModelLoaded(actualPath)
            } catch (e: Exception) {
                _state.value = GenerationState.Error("Failed to load model: ${e.message}", e)
                Log.e(">>> ERR ", "Model load failed", e)
            }
        }
    }

    fun generate(prompt: String) {
        if (!_state.value.canGenerate()) {
            Log.w("MainViewModel", "Cannot generate in current state: ${_state.value}")
            return
        }

        scope.launch {
            val startTime = System.currentTimeMillis()
            var tokenCount = 0

            llamaHelper.predict(prompt, partialCompletion = true)
                .onStart {
                    _state.value = GenerationState.Generating(
                        prompt = prompt,
                        startTime = startTime
                    )
                    _generatedText.value = ""
                    Log.i("MainViewModel", "Generation started")
                }
                .onCompletion { cause ->
                    when {
                        cause != null -> {
                            _state.value = GenerationState.Error("Generation interrupted", cause)
                            Log.e("MainViewModel", "Generation interrupted", cause)
                        }
                        else -> {
                            val duration = System.currentTimeMillis() - startTime
                            _state.value = GenerationState.Completed(
                                prompt = prompt,
                                tokenCount = tokenCount,
                                durationMs = duration
                            )
                            Log.i("MainViewModel", "Generation completed: $tokenCount tokens in ${duration}ms")
                        }
                    }
                    llamaHelper.stopPrediction()
                }
                .catch { e ->
                    _state.value = GenerationState.Error("Generation failed", e)
                    _generatedText.value = ""
                    Log.e("MainViewModel", "Generation error", e)
                }
                .collect { token ->
                    _generatedText.value += token
                    tokenCount++

                    val currentState = _state.value
                    if (currentState is GenerationState.Generating) {
                        _state.value = currentState.copy(tokensGenerated = tokenCount)
                    }
                }
        }
    }

    fun abort() {
        if (_state.value.isActive()) {
            Log.i("MainViewModel", "Aborting generation")
            llamaHelper.abort()

            val currentState = _state.value
            if (currentState is GenerationState.Generating) {
                val duration = System.currentTimeMillis() - currentState.startTime
                _state.value = GenerationState.Completed(
                    prompt = currentState.prompt,
                    tokenCount = currentState.tokensGenerated,
                    durationMs = duration
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        llamaHelper.abort()
        llamaHelper.release()
        viewModelJob.cancel()
    }
}