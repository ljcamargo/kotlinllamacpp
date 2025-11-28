package org.nehuatl.llamacpp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LlamaHelper(val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {

    private val llama by lazy { LlamaAndroid() }
    private var loadJob: Job? = null
    private var contextId: Int? = null

    suspend fun load(path: String, contextLength: Int) = suspendCoroutine { continuation ->
        loadJob = scope.launch {
            val config = mapOf(
                "model" to path,
                "n_ctx" to contextLength,
            )
            val result = llama.initContext(config)

            if (result == null) {
                throw Exception("initContext returned null - model initialization failed")
            }

            Log.d("LlamaHelper", "initContext result: $result")

            val id = result["contextId"]
            if (id == null) {
                throw Exception("contextId not found in result map: $result")
            }

            contextId = when (id) {
                is Int -> id
                is Number -> id.toInt()
                else -> throw Exception("contextId has unexpected type: ${id::class.java.simpleName}, value: $id")
            }

            Log.d("LlamaHelper", "Context loaded successfully with ID: $contextId")
            continuation.resume(Unit)
        }
    }

    fun predict(prompt: String, partialCompletion: Boolean = true): Flow<String> {
        val context = contextId ?: throw Exception("Model was not loaded yet, load it first")

        val eventFlow = llama.setEventCollector(context, scope).mapNotNull { (message, token) ->
            if (message == "token") (token as? String) else null
        }

        llama.launchCompletion(
            id = context,
            params = mapOf(
                "prompt" to prompt,
                "emit_partial_completion" to partialCompletion,
            )
        )

        return eventFlow
    }

    fun stopPrediction() {
        contextId?.let { id ->
            llama.unsetEventCollector(id)
        }
    }

    fun release() {
        contextId?.let { id ->
            llama.releaseContext(id)
        }
    }

    fun abort() {
        loadJob?.cancel()
        stopPrediction()
    }
}