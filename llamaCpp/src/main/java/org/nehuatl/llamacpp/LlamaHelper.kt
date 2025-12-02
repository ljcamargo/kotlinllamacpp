package org.nehuatl.llamacpp

import android.content.ContentResolver
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LlamaHelper(
    val contentResolver: ContentResolver,
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val llama by lazy { LlamaAndroid(contentResolver) }
    private var loadJob: Job? = null
    private var contextId: Int? = null

    suspend fun load(path: String, contextLength: Int) = suspendCoroutine { continuation ->
        loadJob = scope.launch {
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
            val result = llama.initContext(config)

            if (result == null) {
                throw Exception("initContext returned null - model initialization failed")
            }

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
            pfd.close()
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