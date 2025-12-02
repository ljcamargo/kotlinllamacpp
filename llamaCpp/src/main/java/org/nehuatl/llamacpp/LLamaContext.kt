package org.nehuatl.llamacpp

import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException

class LlamaContext(private val id: Int, params: Map<String, Any>) {

    val eventFlow = MutableSharedFlow<Pair<String, Any>>(replay = 0)
    var scope: CoroutineScope?= null

    companion object {
        private const val NAME = "RNLlamaContext"

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

                if (isAtLeastArmV84 && hasFp16 && hasDotProd && hasInt8Matmul) {
                    Log.d(NAME, "Loading librnllama_v8_4_fp16_dotprod_i8mm.so")
                    System.loadLibrary("rnllama_v8_4_fp16_dotprod_i8mm")
                } else if (isAtLeastArmV84 && hasFp16 && hasDotProd) {
                    Log.d(NAME, "Loading librnllama_v8_4_fp16_dotprod.so")
                    System.loadLibrary("rnllama_v8_4_fp16_dotprod")
                } else if (isAtLeastArmV82 && hasFp16 && hasDotProd) {
                    Log.d(NAME, "Loading librnllama_v8_2_fp16_dotprod.so")
                    System.loadLibrary("rnllama_v8_2_fp16_dotprod")
                } else if (isAtLeastArmV82 && hasFp16) {
                    Log.d(NAME, "Loading librnllama_v8_2_fp16.so")
                    System.loadLibrary("rnllama_v8_2_fp16")
                } else {
                    Log.d(NAME, "Loading librnllama_v8.so")
                    System.loadLibrary("rnllama_v8")
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
                val bufferedReader = BufferedReader(FileReader(file))
                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    if (line!!.startsWith("Features")) {
                        stringBuilder.append(line)
                        break
                    }
                }
                bufferedReader.close()
                return stringBuilder.toString()
            } catch (e: IOException) {
                Log.w(NAME, "Couldn't read /proc/cpuinfo", e)
                return ""
            }
        }
    }

    val context: Long
    val modelDetails: Map<String, Any>


    init {
        if (!isArm64V8a() && !isX86_64()) {
            throw IllegalStateException("Only 64-bit architectures are supported")
        }
        if (!params.containsKey("model")) {
            throw IllegalArgumentException("Missing required parameter: model")
        }

        this.context = initContextWithFd(
            // String model,
            params["model_fd"] as Int,
            //params["model"] as String,
            // boolean embedding,
            params["embedding"] as? Boolean ?: false,
            // int n_ctx,
            params["n_ctx"] as? Int ?: 512,
            // int n_batch,
            params["n_batch"] as? Int ?: 512,
            // int n_threads,
            params["n_threads"] as? Int ?: 0,
            // int n_gpu_layers, // TODO: Support this
            params["n_gpu_layers"] as? Int ?: 0,
            // boolean use_mlock,
            params["use_mlock"] as? Boolean ?: true,
            // boolean use_mmap,
            params["use_mmap"] as? Boolean ?: true,
            //boolean vocab_only,
            params["vocab_only"] as? Boolean ?: false,
            // String lora,
            params["lora"] as? String ?: "",
            // float lora_scaled,
            (params["lora_scaled"] as? Double)?.toFloat() ?: 1.0f,
            // float rope_freq_base,
            (params["rope_freq_base"] as? Double)?.toFloat() ?: 0.0f,
            // float rope_freq_scale
            (params["rope_freq_scale"] as? Double)?.toFloat() ?: 0.0f
        )
        this.modelDetails = loadModelDetails(this.context).toMutableMap()
    }

    fun getFormattedChat(messages: List<Map<String, Any>>, chatTemplate: String): String {
        val msgs = messages.toTypedArray()
        return getFormattedChat(context, msgs, chatTemplate.ifEmpty { "" })
    }

    fun emitPartialCompletion(tokenResult: Map<String, Any>) {
        //TODO: log->"emiting partial completion $tokenResult".v()
        val tokenWord = tokenResult["token"] as? String ?: ""
        scope?.launch {
            eventFlow.emit( "token" to tokenWord)
        }
    }

    private inner class PartialCompletionCallback {
        private val emitNeeded: Boolean

        constructor(emitNeeded: Boolean) {
            this.emitNeeded = emitNeeded
        }

        fun onPartialCompletion(tokenResult: Map<String, Any>) {
            if (!emitNeeded) return
            emitPartialCompletion(tokenResult)
        }
    }

    fun loadSession(path: String): Map<String, Any> {
        if (path.isEmpty()) {
            throw IllegalArgumentException("File path is empty")
        }
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("File does not exist: $path")
        }
        val result = loadSession(context, path).toMutableMap()
        if (result.containsKey("error")) {
            throw IllegalStateException(result["error"] as String)
        }
        return result
    }

    fun saveSession(path: String, size: Int): Int {
        if (path.isEmpty()) {
            throw IllegalArgumentException("File path is empty")
        }
        return saveSession(context, path, size)
    }

    fun completion(params: Map<String, Any>): Map<String, Any> {
        //TODO: log->"completion start".v()
        if (!params.containsKey("prompt")) {
            throw IllegalArgumentException("Missing required parameter: prompt")
        }

        val logitBias = params["logit_bias"] as? List<List<Double>>
        val logitBiasArray: Array<DoubleArray> = logitBias?.map { it.toDoubleArray() }?.toTypedArray() ?: emptyArray()

        //TODO: log->"willInvoke doCompletion".v()
        val result = doCompletion(
            context,
            // String prompt,
            params["prompt"] as String,
            // String grammar,
            params["grammar"] as? String ?: "",
            // float temperature,
            (params["temperature"] as? Double)?.toFloat() ?: 0.7f,
            // int n_threads,
            params["n_threads"] as? Int ?: 0,
            // int n_predict,
            params["n_predict"] as? Int ?: -1,
            // int n_probs,
            params["n_probs"] as? Int ?: 0,
            // int penalty_last_n,
            params["penalty_last_n"] as? Int ?: 64,
            // float penalty_repeat,
            (params["penalty_repeat"] as? Double)?.toFloat() ?: 1.00f,
            // float penalty_freq,
            (params["penalty_freq"] as? Double)?.toFloat() ?: 0.00f,
            // float penalty_present,
            (params["penalty_present"] as? Double)?.toFloat() ?: 0.00f,
            // float mirostat,
            (params["mirostat"] as? Double)?.toFloat() ?: 0.00f,
            // float mirostat_tau,
            (params["mirostat_tau"] as? Double)?.toFloat() ?: 5.00f,
            // float mirostat_eta,
            (params["mirostat_eta"] as? Double)?.toFloat() ?: 0.10f,
            // boolean penalize_nl,
            params["penalize_nl"] as? Boolean ?: false,
            // int top_k,
            params["top_k"] as? Int ?: 40,
            // float top_p,
            (params["top_p"] as? Double)?.toFloat() ?: 0.95f,
            // float min_p,
            (params["min_p"] as? Double)?.toFloat() ?: 0.05f,
            // float xtc_t,
            (params["xtc_t"] as? Double)?.toFloat() ?: 0.00f,
            // float xtc_p,
            (params["xtc_p"] as? Double)?.toFloat() ?: 0.00f,
            // float tfs_z,
            (params["tfs_z"] as? Double)?.toFloat() ?: 1.00f,
            // float typical_p,
            (params["typical_p"] as? Double)?.toFloat() ?: 1.00f,
            // int seed,
            params["seed"] as? Int ?: -1,
            // String[] stop,
            (params["stop"] as? List<String>)?.toTypedArray() ?: emptyArray(),
            // boolean ignore_eos,
            params["ignore_eos"] as? Boolean ?: false,
            // double[][] logit_bias,
            logitBiasArray,
            // PartialCompletionCallback partial_completion_callback
            PartialCompletionCallback(
                params["emit_partial_completion"] as? Boolean ?: false
            )
        ).toMutableMap()
        if (result.containsKey("error")) {
            throw IllegalStateException(result["error"] as String)
        }
        return result
    }

    fun stopCompletion() {
        stopCompletion(context)
    }

    fun isPredicting(): Boolean {
        return isPredicting(context)
    }

    fun tokenize(text: String): List<Int> {
        val result = tokenize(context, text)
        return result.map { it as Int }
    }

    fun detokenize(tokens: List<Int>): String {
        return detokenize(context, tokens.toIntArray())
    }

    fun getEmbedding(text: String): Map<String, Any> {
        if (!isEmbeddingEnabled(context)) {
            throw IllegalStateException("Embedding is not enabled")
        }
        val result = embedding(context, text).toMutableMap()
        if (result.containsKey("error")) {
            throw IllegalStateException(result["error"] as String)
        }
        return result
    }

    fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String {
        return bench(context, pp, tg, pl, nr)
    }

    fun release() {
        freeContext(context)
    }

    private external fun initContext(
        model: String,
        embedding: Boolean,
        n_ctx: Int,
        n_batch: Int,
        n_threads: Int,
        n_gpu_layers: Int, // TODO: Support this
        use_mlock: Boolean,
        use_mmap: Boolean,
        vocab_only: Boolean,
        lora: String,
        lora_scaled: Float,
        rope_freq_base: Float,
        rope_freq_scale: Float
    ): Long

    private external fun initContextWithFd(
        modelFd: Int,
        embedding: Boolean,
        n_ctx: Int,
        n_batch: Int,
        n_threads: Int,
        n_gpu_layers: Int,
        use_mlock: Boolean,
        use_mmap: Boolean,
        vocab_only: Boolean,
        lora: String?,
        lora_scaled: Float,
        rope_freq_base: Float,
        rope_freq_scale: Float
    ): Long

    private external fun loadModelDetails(contextPtr: Long): Map<String, Any>

    external fun getFormattedChat(contextPtr: Long, messages: Array<Map<String, Any>>, chatTemplate: String): String

    private external fun loadSession(contextPtr: Long, path: String): Map<String, Any>

    private external fun saveSession(contextPtr: Long, path: String, size: Int): Int

    private external fun doCompletion(
        contextPtr: Long,
        prompt: String,
        grammar: String,
        temperature: Float,
        n_threads: Int,
        n_predict: Int,
        n_probs: Int,
        penalty_last_n: Int,
        penalty_repeat: Float,
        penalty_freq: Float,
        penalty_present: Float,
        mirostat: Float,
        mirostat_tau: Float,
        mirostat_eta: Float,
        penalize_nl: Boolean,
        top_k: Int,
        top_p: Float,
        min_p: Float,
        xtc_t: Float,
        xtc_p: Float,
        tfs_z: Float,
        typical_p: Float,
        seed: Int,
        stop: Array<String>,
        ignore_eos: Boolean,
        logit_bias: Array<DoubleArray>,
        partial_completion_callback: PartialCompletionCallback
    ): Map<String, Any>

    private external fun stopCompletion(contextPtr: Long)

    private external fun isPredicting(contextPtr: Long): Boolean

    private external fun tokenize(contextPtr: Long, text: String): List<Any>

    private external fun detokenize(contextPtr: Long, tokens: IntArray): String

    private external fun isEmbeddingEnabled(contextPtr: Long): Boolean

    private external fun embedding(contextPtr: Long, text: String): Map<String, Any>

    private external fun bench(contextPtr: Long, pp: Int, tg: Int, pl: Int, nr: Int): String

    private external fun freeContext(contextPtr: Long)
}