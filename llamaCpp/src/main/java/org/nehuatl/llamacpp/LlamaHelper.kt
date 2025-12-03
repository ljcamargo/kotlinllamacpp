package org.nehuatl.llamacpp

import android.content.ContentResolver
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class LlamaHelper(
    val contentResolver: ContentResolver,
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    val sharedFlow: MutableSharedFlow<LLMEvent>
) {

    private val llama by lazy { LlamaAndroid(contentResolver) }
    private var loadJob: Job? = null
    private var completionJob: Job? = null
    private var currentContext: Int? = null
    private var tokenCount = 0
    private var allText = ""

    fun load(path: String, contextLength: Int, loaded: (Long) -> Unit) {
        currentContext?.let { id -> llama.releaseContext(id) }
        val uri = path.toUri()
        val useMMap = uri.scheme != "content"
        val pfd = contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open URI")
        val fd = pfd.detachFd()
        val config = mapOf(
            "model" to path,
            "model_fd" to fd,
            "use_mmap" to false,
            "use_mlock" to false,
            "n_ctx" to contextLength,
        )
        loadJob = scope.launch {
            Log.d("LlamaHelper", ">>> will start llama context with config: $config")
            val result = llama.startEngine(config) {
                allText += it
                tokenCount++
                sharedFlow.tryEmit(LLMEvent.Ongoing(it, tokenCount))
            }

            if (result == null) {
                throw Exception("initContext returned null - model initialization failed")
            }

            val id = result["contextId"] ?: throw Exception("contextId not found in result map: $result")

            currentContext = when (id) {
                is Int -> id
                is Number -> id.toInt()
                else -> {
                    throw Exception("contextId has unexpected type: ${id::class.java.simpleName}, value: $id")
                }
            }

            Log.d("LlamaHelper", ">>> Context loaded successfully with ID: $currentContext")
            pfd.close()
            sharedFlow.tryEmit(LLMEvent.Loaded(path))
            loaded(currentContext!!.toLong())
        }
    }

    fun predict(prompt: String, partialCompletion: Boolean = true) {
        val context = currentContext ?: throw Exception("Model was not loaded yet, load it first")
        val startTime = System.currentTimeMillis()
        tokenCount = 0
        completionJob = scope.launch {
            llama.launchCompletion(
                id = context,
                params = mapOf(
                    "prompt" to prompt,
                    "emit_partial_completion" to partialCompletion,
                )
            )
            val duration = System.currentTimeMillis() - startTime
            sharedFlow.tryEmit(LLMEvent.Done(allText, tokenCount, duration))
        }
    }

    fun stopPrediction() {
        if (currentContext != null) return
        scope.launch {
            llama.stopCompletion(currentContext!!)
        }
        completionJob?.cancel()
    }

    fun release() {
        currentContext?.let { id ->
            llama.releaseContext(id)
        }
    }

    fun abort() {
        loadJob?.cancel()
        stopPrediction()
    }

    sealed class LLMEvent {
        data class Loaded(val path: String) : LLMEvent()
        data class Started(val prompt: String) : LLMEvent()
        data class Ongoing(val word: String, val tokenCount: Int) : LLMEvent()
        data class Done(val fullText: String, val tokenCount: Int, val duration: Long) : LLMEvent()
        data class Error(val message: String) : LLMEvent()
    }
}