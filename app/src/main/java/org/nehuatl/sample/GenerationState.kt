package org.nehuatl.sample

sealed class GenerationState {
    data object Idle : GenerationState()
    data object LoadingModel : GenerationState()
    data class ModelLoaded(val modelPath: String) : GenerationState()
    data class Generating(
        val prompt: String,
        val startTime: Long = System.currentTimeMillis(),
        val tokensGenerated: Int = 0
    ) : GenerationState()
    data class Completed(
        val prompt: String,
        val tokenCount: Int,
        val durationMs: Long
    ) : GenerationState()
    data class Error(val message: String, val cause: Throwable? = null) : GenerationState()

    // Add this method that was missing
    fun isGenerating(): Boolean = this is Generating
    fun isActive(): Boolean = this is LoadingModel || this is Generating
    fun canGenerate(): Boolean = this is ModelLoaded || this is Completed
}