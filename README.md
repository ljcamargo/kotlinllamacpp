# Kotlin-LlamaCpp

### Implementing GGUF Local Inference into Android Devices with EASE

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](https://opensource.org/licenses/MIT)

**Native AI inference for Android devices**

Run GGUF models directly on your Android device with optimized performance and zero cloud dependency!

This library provides Kotlin bindings for [llama.cpp](https://github.com/ggerganov/llama.cpp), designed specifically for native Android applications. It leverages modern hardware capabilities to bring efficient large language model inference and multimodal support to mobile devices.

[![ko-fi](https://www.ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/P5P6149YRQ)

## Changelog
### v0.4.0 (Latest)
- **Modernized Core**: Native codebase synchronized with the latest `llama.cpp` upstream (via `cui-llama.rn`).
- **Multimodal Support**: Full support for vision models (e.g., LLaVA) using `mmproj` files.
- **Improved File Handling**: Migrated to a robust File Descriptor (FD) passing mechanism, bypassing Android's scoped storage restrictions.
- **Architecture Support**: Optimized for 64-bit platforms (`arm64-v8a` and `x86_64`).
- **Real-time Streaming**: Enhanced JNI logic with robust UTF-8 buffering to prevent crashes during token generation.
- **UI State Feedback**: Improved `LlamaHelper` to provide immediate feedback during image analysis phases.

---

## Why On-Device AI?

Modern Android devices possess the power to run sophisticated AI models locally. Kotlin-LlamaCpp enables:

- **True On-Device AI**: Complete privacy, no internet required.
- **Hardware Acceleration**: Automatic utilization of CPU features (i8mm, dotprod) on ARM and x86.
- **Multimodal Capabilities**: Analyze images locally using multimodal projectors (`mmproj`).

---

## Getting Started

### 1. Installation

Add the dependency to your project's `build.gradle`:
```gradle
dependencies {
    implementation 'io.github.ljcamargo:llamacpp-kotlin:0.4.0'
}
```

### 2. Architecture: The `LlamaHelper` Pattern

For most use cases, it is recommended to manage the library within an Android **ViewModel**. This ensures that the engine's lifecycle is correctly tied to your UI while keeping heavy computations off the main thread.

The `LlamaHelper` class requires three main components to initialize:
1.  **`ContentResolver`**: Required to open local files via File Descriptors.
2.  **`CoroutineScope`**: The scope in which inference tasks will run.
3.  **`MutableSharedFlow<LLMEvent>`**: A reactive stream that emits status updates and generated tokens.

#### Recommended Setup in `MainViewModel.kt`
```kotlin
class MainViewModel(val contentResolver: ContentResolver) : ViewModel() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 1. Flow to collect events from the engine
    private val _llmFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    // 2. StateFlow to hold the accumulated text for the UI
    private val _generatedText = MutableStateFlow("")
    val generatedText = _generatedText.asStateFlow()

    private val llamaHelper by lazy {
        LlamaHelper(contentResolver, scope, _llmFlow)
    }

    fun generate(prompt: String) {
        scope.launch {
            _generatedText.value = "" // Reset text
            llamaHelper.predict(prompt)
            
            // 3. Collect events and accumulate text
            _llmFlow.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Ongoing -> {
                        _generatedText.value += event.word
                    }
                    is LlamaHelper.LLMEvent.Done -> { /* Stop loading indicators */ }
                    is LlamaHelper.LLMEvent.Error -> { /* Handle error */ }
                    else -> {}
                }
            }
        }
    }
}
```

---

## Handling Local Files (The URI Caveat)

On modern Android (11+), you cannot pass traditional file paths to native libraries due to Scoped Storage. You **must** use `content://` URIs and ensure you have persistent read access.

### Scenario A: User selects a model via File Picker
Use `registerForActivityResult` and explicitly request persistable permissions. Without this, the native engine will lose access to the file once the app restarts or the URI context changes.

**In your Activity/Fragment:**
```kotlin
private val modelPickerLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocument()
) { uri ->
    uri?.let {
        // CRITICAL: Gain long-term access to the file
        contentResolver.takePersistableUriPermission(
            it, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        // Now you can pass uri.toString() to LlamaHelper.load()
        viewModel.loadModel(it.toString())
    }
}
```

### Scenario B: Loading a fixed model from App Storage
If your model is stored in your app's internal files directory, you can resolve its URI directly:
```kotlin
val file = File(context.filesDir, "my_model.gguf")
val modelUri = Uri.fromFile(file).toString()
llamaHelper.load(path = modelUri, contextLength = 2048) { id -> /* Loaded */ }
```

---

## Usage Examples

### Basic Text Completion
```kotlin
// In your ViewModel
fun generateResponse(userPrompt: String) {
    llamaHelper.predict(userPrompt)
}

// In your UI (Jetpack Compose)
@Composable
fun SimpleChat(viewModel: MainViewModel) {
    // 4. Listen to the StateFlow (lifecycle-aware)
    val text by viewModel.generatedText.collectAsStateWithLifecycle()

    Column {
        Text(text = text) // Automatically updates as tokens arrive!
        Button(onClick = { viewModel.generate("Hello!") }) {
            Text("Generate")
        }
    }
}
```

### Multimodal (Image Analysis)
To analyze images, you must load a base model AND an `mmproj` projector file.
```kotlin
// 1. Initialization
llamaHelper.load(
    path = baseModelUri,
    contextLength = 4096,
    mmprojPath = mmprojUri // Provide the projector file here
) { id -> /* Multimodal Ready */ }

// 2. Inference with image
// Note: Per-prompt image injection. The helper automatically 
// handles File Descriptors for the image.
llamaHelper.predict(
    prompt = "What objects are in this photo?",
    imagePath = selectedImageUri 
)
```

---

## Deep Dive into the Demo App
For a complete working implementation, explore the following files in the [Demo App](app/src/main/java/org/nehuatl/sample):

1.  [`MainActivity.kt`](app/src/main/java/org/nehuatl/sample/MainActivity.kt): Shows how to implement the file pickers and handle persistable permissions.
2.  [`MainViewModel.kt`](app/src/main/java/org/nehuatl/sample/MainViewModel.kt): Demonstrates the clean integration of `LlamaHelper` with a reactive `StateFlow` UI.
3.  [`ChatScreen.kt`](app/src/main/java/org/nehuatl/sample/ChatScreen.kt): A full Jetpack Compose UI showing how to display inference progress, block inputs during analysis, and handle multimodal results.

## Native Code Maintenance
The native C++ core is synchronized with modernized `llama.cpp` forks. For instructions on how to update or rebuild the native components, see the [llamaCpp Library README](llamaCpp/README.md).

## Contributing & License
Contributions are welcome! Please feel free to submit a Pull Request. This project is licensed under the **MIT License**.

## Acknowledgments
Built upon the excellence of:
- [llama.cpp](https://github.com/ggerganov/llama.cpp) (Georgi Gerganov)
- [cui-llama.rn](https://github.com/Vali-98/cui-llama.rn) (Vali-98)
- [llama.rn](https://github.com/mybigday/llama.rn)
