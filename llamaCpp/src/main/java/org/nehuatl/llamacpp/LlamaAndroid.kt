package org.nehuatl.llamacpp

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

class LlamaAndroid(val resolver: ContentResolver) {

    companion object {
        private const val NAME = "RNLlama"

        init {
            Log.d(NAME, "Primary ABI: ${Build.SUPPORTED_ABIS[0]}")
            if (isArm64V8a()) {
                val cpuFeatures = getCpuFeatures()
                Log.d(NAME, "CPU features: $cpuFeatures")

                val hasFp16 = cpuFeatures.contains("fp16") || cpuFeatures.contains("fphp")
                val hasDotProd = cpuFeatures.contains("dotprod") || cpuFeatures.contains("asimddp")
                val isAtLeastArmV82 = cpuFeatures.contains("asimd") && cpuFeatures.contains("crc32") && cpuFeatures.contains("aes")
                val isAtLeastArmV84 = cpuFeatures.contains("dcpop") && cpuFeatures.contains("uscat")
                val hasInt8Matmul = cpuFeatures.contains("i8mm")

                when {
                    isAtLeastArmV84 && hasFp16 && hasDotProd && hasInt8Matmul -> {
                        Log.d(NAME, "Loading librnllama_v8_4_fp16_dotprod_i8mm.so")
                        System.loadLibrary("rnllama_v8_4_fp16_dotprod_i8mm")
                    }
                    isAtLeastArmV84 && hasFp16 && hasDotProd -> {
                        Log.d(NAME, "Loading librnllama_v8_4_fp16_dotprod.so")
                        System.loadLibrary("rnllama_v8_4_fp16_dotprod")
                    }
                    isAtLeastArmV82 && hasFp16 && hasDotProd -> {
                        Log.d(NAME, "Loading librnllama_v8_2_fp16_dotprod.so")
                        System.loadLibrary("rnllama_v8_2_fp16_dotprod")
                    }
                    isAtLeastArmV82 && hasFp16 -> {
                        Log.d(NAME, "Loading librnllama_v8_2_fp16.so")
                        System.loadLibrary("rnllama_v8_2_fp16")
                    }
                    else -> {
                        Log.d(NAME, "Loading librnllama_v8.so")
                        System.loadLibrary("rnllama_v8")
                    }
                }
            } else if (isX86_64()) {
                Log.d(NAME, "Loading librnllama_x86_64.so")
                System.loadLibrary("rnllama_x86_64")
            } else {
                Log.d(NAME, "Loading default librnllama.so")
                System.loadLibrary("rnllama")
            }
        }

        private fun isArm64V8a(): Boolean = Build.SUPPORTED_ABIS[0] == "arm64-v8a"
        private fun isX86_64(): Boolean = Build.SUPPORTED_ABIS[0] == "x86_64"

        private fun getCpuFeatures(): String {
            val file = File("/proc/cpuinfo")
            val stringBuilder = StringBuilder()
            try {
                BufferedReader(FileReader(file)).use { bufferedReader ->
                    var line: String?
                    while (bufferedReader.readLine().also { line = it } != null) {
                        if (line!!.startsWith("Features")) {
                            stringBuilder.append(line)
                            break
                        }
                    }
                }
                return stringBuilder.toString()
            } catch (e: IOException) {
                Log.w(NAME, "Couldn't read /proc/cpuinfo", e)
                return ""
            }
        }
    }

    private val contexts = ConcurrentHashMap<Int, LlamaContext>()
    private var llamaContextLimit = 1

    fun setContextLimit(limit: Int) {
        llamaContextLimit = limit
    }

    fun startEngine(
        params: Map<String, Any>,
        tokenCallback: ((String) -> Unit)
    ): Map<String, Any>? {
        return try {
            if (contexts.size >= llamaContextLimit) {
                throw Exception("Context limit reached")
            }
            val id = Random().nextInt().absoluteValue
            if (!isGGUF((params["model"] as String).toUri())) {
                throw IllegalArgumentException("File is not in GGUF format")
            }
            val llamaContext = LlamaContext(id, params)
            if (llamaContext.context == 0L) {
                throw Exception("Failed to initialize context")
            }
            llamaContext.setTokenCallback(tokenCallback)
            contexts[id] = llamaContext
            mapOf(
                "contextId" to id,
                "gpu" to false,
                "reasonNoGPU" to "Currently not supported",
                "model" to llamaContext.modelDetails
            )
        } catch (e: Exception) {
            Log.e(NAME, "Error initializing context", e)
            null
        }
    }

    fun releaseContext(id: Int) {
        contexts[id]!!.release()
        contexts.remove(id)
    }

    fun isGGUF(uri: Uri): Boolean {
        val ggufHeader = byteArrayOf(0x47, 0x47, 0x55, 0x46)
        return try {
            resolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(4)
                if (input.read(header) != 4) return false
                header.contentEquals(ggufHeader)
            } ?: false
        } catch (err: Exception) {
            Log.v(">>> GUF ERR", ">>> gguf err $err")
            false
        }
    }

    fun getFormattedChat(
        id: Int, messages: List<Map<String, Any>>, chatTemplate: String
    ): Flow<String> = flow {
        try {
            val context = contexts[id] ?: throw Exception("Context not found")
            val result = context.getFormattedChat(messages, chatTemplate)
            emit(result)
        } catch (e: Exception) {
            Log.e(NAME, "Error formatting chat", e)
        }
    }.flowOn(Dispatchers.IO)

    fun loadSession(id: Int, path: String): Flow<Map<String, Any>> = flow {
        try {
            val context = contexts[id] ?: throw Exception("Context not found")
            emit(context.loadSession(path))
        } catch (e: Exception) {
            Log.e(NAME, "Error loading session", e)
        }
    }.flowOn(Dispatchers.IO)

    fun saveSession(id: Int, path: String, size: Int): Flow<Int> = flow {
        try {
            val context = contexts[id] ?: throw Exception("Context not found")
            emit(context.saveSession(path, size))
        } catch (e: Exception) {
            Log.e(NAME, "Error saving session", e)
            emit(-1)
        }
    }.flowOn(Dispatchers.IO)

    fun launchCompletion(id: Int, params: Map<String, Any>): Map<String, Any>?  {
        Log.i(NAME, "completion $id of $params")
        return try {
            val context = contexts[id] ?: throw Exception("Context not found")
            if (context.isPredicting()) throw Exception("Context is busy")
            context.completion(params)
        } catch (e: Exception) {
            Log.e(NAME, "Error during completion", e)
            null
        }
    }

    suspend fun stopCompletion(id: Int) = withContext(Dispatchers.IO) {
        val context = contexts[id] ?: throw Exception("Context not found")
        context.stopCompletion()
    }

    fun tokenize(id: Int, text: String): Flow<Map<String, Any>> = flow {
        try {
            val context = contexts[id] ?: throw Exception("Context not found")
            emit(mapOf("tokens" to context.tokenize(text)))
        } catch (e: Exception) {
            Log.e(NAME, "Error tokenizing text", e)
        }
    }.flowOn(Dispatchers.IO)

    fun detokenize(id: Int, tokens: List<Int>): Flow<String> = flow {
        try {
            val context = contexts[id] ?: throw Exception("Context not found")
            emit(context.detokenize(tokens))
        } catch (e: Exception) {
            Log.e(NAME, "Error detokenizing tokens", e)
        }
    }.flowOn(Dispatchers.IO)

    fun embedding(id: Int, text: String): Flow<Map<String, Any>> = flow {
        try {
            val context = contexts[id] ?: throw Exception("Context not found")
            emit(context.getEmbedding(text))
        } catch (e: Exception) {
            Log.e(NAME, "Error getting embedding", e)
        }
    }.flowOn(Dispatchers.IO)
}