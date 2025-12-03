# Kotlin-LlamaCpp

### Implementing GGUF Local Inference into Android ARM Devices with EASE

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Optimized for Arm](https://img.shields.io/badge/Optimized_for-Arm-0091BD?logo=arm&logoColor=white)](https://www.arm.com/)

**Native AI inference for Arm-based Android devices**

Run GGUF models directly on your Arm-powered Android device with optimized performance and zero cloud dependency!

This is an Android binding for [llama.cpp](https://github.com/ggerganov/llama.cpp) written in Kotlin, designed specifically for native Android applications running on Arm architecture. Built from the ground up to leverage Arm CPU capabilities, this library brings efficient large language model inference to mobile devices. The project is inspired by [cui-llama.rn](https://github.com/Vali-98/cui-llama.rn) and [llama.cpp](https://github.com/ggerganov/llama.cpp): Inference of [LLaMA](https://arxiv.org/abs/2302.13971) model in pure C/C++, specifically tailored for Arm-based Android development in Kotlin.

This is a very early alpha version and API may change in the future.

[![ko-fi](https://www.ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/P5P6149YRQ)

## News
- Content Resolver has been implemented for new versions of Android to allow local file access
- Library has been updated to comply with 16kb pagination now enforced

## Why On-Device AI on Arm?

Most modern Android devices run on Arm processors, making Arm the dominant architecture for mobile AI applications. Kotlin-LlamaCpp is built specifically for this ecosystem, enabling:

- **True On-Device AI**: Run large language models entirely on your Arm-based phone or tablet—no internet required, complete privacy
- **Arm-Optimized Performance**: Automatic detection and utilization of Arm CPU features (i8mm, dotprod) for hardware-accelerated inference
- **Mobile-First Design**: Built from the ground up for Arm's power-efficient architecture, balancing performance with battery life
- **Real-World Usability**: Context management and batch interruption designed for the constraints of mobile Arm processors

The vast majority of Android devices today are powered by Arm processors (Snapdragon, MediaTek, Exynos, Tensor). This library is optimized specifically for this architecture, bringing desktop-class AI capabilities to the devices already in your users' pockets.

## Features

- **Native Arm Architecture Support**: Built for arm64-v8a with automatic CPU feature detection (i8mm and dotprod flags)
- **Hardware-Accelerated Inference**: Leverages Arm-specific instruction sets for optimized matrix operations
- **Efficient Mobile Inference**: Context Shift support from [kobold.cpp](https://github.com/LostRuins/koboldcpp) enables longer conversations without memory overflow
- **Kotlin-First Design**: Helper class to handle initialization and context management seamlessly
- **Flexible Control**: Support for stopping prompt processing between batches, crucial for responsive mobile UIs
- **Progress Monitoring**: Real-time callback support for tracking inference progress
- **Tokenizer Support**: Vocabulary-only mode with synchronous tokenizer functions
- **Seamless Android Integration**: Works naturally with Android development workflows and lifecycle management

## Demo App
You can find a complete, ready-to-build demo application in the [`/app`](https://github.com/ljcamargo/kotlinllamacpp/tree/master/app) directory of this repository.
The demo showcases how to integrate the library into a standard Android app, including model loading from local storage, handling inference in a ViewModel, and displaying generated text in a Jetpack Compose UI.

<img src="https://github.com/ljcamargo/kotlinllamacpp/raw/master/app/src/main/ic_launcher-playstore.png" alt="Demo App Icon" width="128"/>


## Installation

Add the following to your project's `build.gradle`:
```gradle
dependencies {
    implementation 'io.github.ljcamargo:llamacpp-kotlin:0.2.0'
}
```

## Model Requirements

You'll need a GGUF model file to use this library. You can:

- Download pre-converted GGUF models from [HuggingFace](https://huggingface.co/search/full-text?q=GGUF&type=model)
- Convert your own models following the [llama.cpp quantization guide](https://github.com/ggerganov/llama.cpp#prepare-and-quantize)

Quantized models (Q4, Q5, Q8) work particularly well on Arm mobile processors, providing an excellent balance between model quality and inference speed.

## Usage

Check this example ViewModel using LlamaHelper class for basic usage:
```kotlin
class MainViewModel: ViewModel(val contentResolver: ContentResolver) {

    private val viewModelJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + viewModelJob)
    
    private val _llmFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val llmFlow: SharedFlow<LlamaHelper.LLMEvent> = _llmFlow.asSharedFlow()
    
    private val _generatedText = MutableStateFlow("")
    val generatedText = _generatedText.asStateFlow()

    private val llamaHelper by lazy {
        LlamaHelper(
            contentResolver = contentResolver, // <-- Recent android versions now require resolver to access local files
            scope = scope,
            sharedFlow = _llmFlow,
        )
    }

    // load gguf model into memory
    fun loadModel() {
        llamaHelper.load(
            path = "/sdcard/Download/llama.ggmlv3.q4_0.bin",
            contextLength = 2048,
        ) {
            // MODEL SUCCESSFULLY LOADED (it: context id)
            // TODO: Update your UI to allow prompts
        }
    }

    // model should be loaded before submitting or an exception will be thrown
    fun generate(prompt: String) {
        scope.launch {
            llamaHelper.predict(prompt)
            llmFlow.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Started -> {
                        // Update your UI to show the gen started
                    }
                    is LlamaHelper.LLMEvent.Ongoing -> {
                        // A new token has been generated, update your UI accordingly
                        // vb.g. _generatedText.value += event.word
                    }
                    is LlamaHelper.LLMEvent.Done -> {
                        // Update your UI to show the gen completed
                        llamaHelper.stopPrediction()
                    }
                    is LlamaHelper.LLMEvent.Error -> {
                        // Update your UI to show the gen error
                        llamaHelper.stopPrediction()
                    }
                    else -> {}
                }
            }
        }
    }

    // you can abort the model load or prediction in progress
    fun abort() {
        llamaHelper.abort()
    }

    // don't forget to release resources when your viewmodel is destroyed
    override fun onCleared() {
        super.onCleared()
        llamaHelper.abort()
        llamaHelper.release()
    }
}
```

You can also use LlamaContext.kt directly to handle several contexts or other complex features.

## Performance on Arm Architecture

Kotlin-LlamaCpp is optimized specifically for arm64-v8a, the architecture powering the vast majority of modern Android devices:

- **Arm CPU Extensions**: Automatic detection and utilization of i8mm (integer matrix multiplication) and dotprod instructions provides significant performance improvements for AI workloads
- **Memory Efficiency**: Designed to work within mobile device constraints while maintaining responsive performance
- **Batch Interruption**: Critical for Arm mobile processors, allowing the UI to remain responsive during inference
- **Power Efficiency**: Native Arm optimizations help balance inference performance with battery life—essential for mobile use cases
- **64-bit Optimization**: Recommended arm64-v8a platform for better memory allocation and performance

The library currently supports arm64-v8a android devices and platforms.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

MIT

## Acknowledgments

This project builds upon the work of several excellent projects:
- [llama.cpp](https://github.com/ggerganov/llama.cpp) by Georgi Gerganov
- [cui-llama.rn](https://github.com/Vali-98/cui-llama.rn)
- [llama.rn](https://github.com/mybigday/llama.rn)