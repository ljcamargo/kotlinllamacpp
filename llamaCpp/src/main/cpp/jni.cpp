#include <jni.h>
#include <unistd.h>
#include <fcntl.h>

// #include <android/asset_manager.h>
// #include <android/asset_manager_jni.h>
#include <android/log.h>
#include <cstdlib>
#include <ctime>
#include <sys/sysinfo.h>
#include <string>
#include <thread>
#include <unordered_map>
#include "llama.h"
#include "rn-llama.hpp"
#include "ggml.h"

#define UNUSED(x) (void)(x)
#define TAG "RNLLAMA_ANDROID_JNI"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,     TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,     TAG, __VA_ARGS__)

static inline int min(int a, int b) {
    return (a < b) ? a : b;
}

extern "C" {

// Helper method to create a Java HashMap
static inline jobject createHashMap(JNIEnv *env) {
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID init = env->GetMethodID(hashMapClass, "<init>", "()V");
    jobject hashMap = env->NewObject(hashMapClass, init);
    return hashMap;
}

// Helper method to put a string into a Java HashMap
static inline void putStringHashMap(JNIEnv *env, jobject hashMap, const char *key, const char *value) {
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    jstring jKey = env->NewStringUTF(key);
    jstring jValue = env->NewStringUTF(value);

    env->CallObjectMethod(hashMap, putMethod, jKey, jValue);
    // Clean up local references
    //env->DeleteLocalRef(jKey);
    //env->DeleteLocalRef(jValue);
}

// Helper method to put an int into a Java HashMap
static inline void putIntHashMap(JNIEnv *env, jobject hashMap, const char *key, int value) {
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    jstring jKey = env->NewStringUTF(key);

    // Box the int value into a java.lang.Integer object
    jclass integerClass = env->FindClass("java/lang/Integer");
    jmethodID integerConstructor = env->GetMethodID(integerClass, "<init>", "(I)V");
    jobject jValue = env->NewObject(integerClass, integerConstructor, value);

    env->CallObjectMethod(hashMap, putMethod, jKey, jValue);

    // Clean up local references
    //env->DeleteLocalRef(jKey);
    //env->DeleteLocalRef(jValue);
}

// Helper method to put a double into a Java HashMap
static inline void putDoubleHashMap(JNIEnv *env, jobject hashMap, const char *key, double value) {
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    jstring jKey = env->NewStringUTF(key);

    // Box the double value into a java.lang.Double object
    jclass doubleClass = env->FindClass("java/lang/Double");
    jmethodID doubleConstructor = env->GetMethodID(doubleClass, "<init>", "(D)V");
    jobject jValue = env->NewObject(doubleClass, doubleConstructor, value);

    env->CallObjectMethod(hashMap, putMethod, jKey, jValue);

    // Clean up local references
    //env->DeleteLocalRef(jKey);
    //env->DeleteLocalRef(jValue);
}

// Helper method to put a boolean into a Java HashMap
static inline void putBooleanHashMap(JNIEnv *env, jobject hashMap, const char *key, bool value) {
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    jstring jKey = env->NewStringUTF(key);

    // Box the boolean value into a java.lang.Boolean object
    jclass booleanClass = env->FindClass("java/lang/Boolean");
    jmethodID booleanConstructor = env->GetMethodID(booleanClass, "<init>", "(Z)V");
    jobject jValue = env->NewObject(booleanClass, booleanConstructor, value);

    env->CallObjectMethod(hashMap, putMethod, jKey, jValue);

    // Clean up local references
    //env->DeleteLocalRef(jKey);
    //env->DeleteLocalRef(jValue);
}

// Helper method to create a Java ArrayList
static inline jobject createArrayList(JNIEnv *env) {
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID init = env->GetMethodID(arrayListClass, "<init>", "()V");
    jobject arrayList = env->NewObject(arrayListClass, init);
    return arrayList;
}

// Helper method to add an int to a Java ArrayList
static inline void addIntArrayList(JNIEnv *env, jobject arrayList, int value) {
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID addMethod = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    // Box the int value into a java.lang.Integer object
    jclass integerClass = env->FindClass("java/lang/Integer");
    jmethodID integerConstructor = env->GetMethodID(integerClass, "<init>", "(I)V");
    jobject jValue = env->NewObject(integerClass, integerConstructor, value);

    env->CallBooleanMethod(arrayList, addMethod, jValue);

    // Clean up local references
    //env->DeleteLocalRef(jKey);
    //env->DeleteLocalRef(jValue);
}

// Helper method to add a double to a Java ArrayList
static inline void addDoubleArrayList(JNIEnv *env, jobject arrayList, double value) {
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID addMethod = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    // Box the double value into a java.lang.Double object
    jclass doubleClass = env->FindClass("java/lang/Double");
    jmethodID doubleConstructor = env->GetMethodID(doubleClass, "<init>", "(D)V");
    jobject jValue = env->NewObject(doubleClass, doubleConstructor, value);

    env->CallBooleanMethod(arrayList, addMethod, jValue);

    // Clean up local references
    //env->DeleteLocalRef(jKey);
    //env->DeleteLocalRef(jValue);
}

// Helper method to add a string to a Java ArrayList
static inline void addStringArrayList(JNIEnv *env, jobject arrayList, const char *value) {
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID addMethod = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    jstring jValue = env->NewStringUTF(value);

    env->CallBooleanMethod(arrayList, addMethod, jValue);
}

// Helper method to add a HashMap to a Java ArrayList
static inline void addHashMapArrayList(JNIEnv *env, jobject arrayList, jobject value) {
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID addMethod = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    env->CallBooleanMethod(arrayList, addMethod, value);
}

// Helper method to put a Java ArrayList into a Java HashMap
static inline void putArrayListHashMap(JNIEnv *env, jobject hashMap, const char *key, jobject value) {
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    jstring jKey = env->NewStringUTF(key);

    env->CallObjectMethod(hashMap, putMethod, jKey, value);
}

// Helper method to put a Java HashMap into a Java HashMap
static inline void putHashMapHashMap(JNIEnv *env, jobject hashMap, const char *key, jobject value) {
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID putMethod = env->GetMethodID(hashMapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    jstring jKey = env->NewStringUTF(key);

    env->CallObjectMethod(hashMap, putMethod, jKey, value);
}

std::unordered_map<long, rnllama::llama_rn_context *> context_map;

struct CallbackContext {
    JNIEnv * env;
    jobject  thiz;
    jmethodID sendProgressMethod;
    unsigned current;
};

JNIEXPORT jlong JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_initContext(
        JNIEnv *env,
        jobject thiz,
        jstring model_path_str,
        jboolean embedding,
        jint n_ctx,
        jint n_batch,
        jint n_threads,
        jint n_gpu_layers, // TODO: Support this
        jboolean use_mlock,
        jboolean use_mmap,
        jboolean vocab_only,
        jstring lora_str,
        jfloat lora_scaled,
        jfloat rope_freq_base,
        jfloat rope_freq_scale
) {
    UNUSED(thiz);

    gpt_params defaultParams;

    defaultParams.vocab_only = vocab_only;
    if(vocab_only) {
        defaultParams.warmup = false;
    }

    const char *model_path_chars = env->GetStringUTFChars(model_path_str, nullptr);
    defaultParams.model = model_path_chars;

    defaultParams.embedding = embedding;

    defaultParams.n_ctx = n_ctx;
    defaultParams.n_batch = n_batch;

    int max_threads = std::thread::hardware_concurrency();
    // Use 2 threads by default on 4-core devices, 4 threads on more cores
    int default_n_threads = max_threads == 4 ? 2 : min(4, max_threads);
    defaultParams.cpuparams.n_threads = n_threads > 0 ? n_threads : default_n_threads;

    defaultParams.n_gpu_layers = n_gpu_layers;

    defaultParams.use_mlock = use_mlock;
    defaultParams.use_mmap = use_mmap;

    const char *lora_chars = env->GetStringUTFChars(lora_str, nullptr);
    if (lora_chars != nullptr && lora_chars[0] != '\0') {
        defaultParams.lora_adapters.push_back({lora_chars, lora_scaled});
        defaultParams.use_mmap = false;
    }

    defaultParams.rope_freq_base = rope_freq_base;
    defaultParams.rope_freq_scale = rope_freq_scale;




    auto llama = new rnllama::llama_rn_context();
    bool is_model_loaded = llama->loadModel(defaultParams);

    LOGI("[RNLlama] is_model_loaded %s", (is_model_loaded ? "true" : "false"));
    if (is_model_loaded) {
        context_map[(long) llama->ctx] = llama;
    } else {
        llama_free(llama->ctx);
    }

    env->ReleaseStringUTFChars(model_path_str, model_path_chars);
    env->ReleaseStringUTFChars(lora_str, lora_chars);

    return reinterpret_cast<jlong>(llama->ctx);
}

// New JNI function: same args as initContext but model represented by jint fd first
JNIEXPORT jlong JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_initContextWithFd(
        JNIEnv *env,
        jobject thiz,
        jint model_fd,
        jboolean embedding,
        jint n_ctx,
        jint n_batch,
        jint n_threads,
        jint n_gpu_layers,
        jboolean use_mlock,
        jboolean use_mmap,
        jboolean vocab_only,
        jstring lora_str,
        jfloat lora_scaled,
        jfloat rope_freq_base,
        jfloat rope_freq_scale
) {
    UNUSED(thiz);

    gpt_params defaultParams;

    defaultParams.vocab_only = vocab_only;
    if (vocab_only) defaultParams.warmup = false;

    // --- Duplicate FD ----------------------------------------------------
    if (model_fd < 0) {
        LOGW("Invalid model_fd < 0");
        return 0;
    }

    int dupfd = dup(model_fd);
    if (dupfd == -1) {
        LOGW("dup(model_fd=%d) failed errno=%d (%s)",
             model_fd, errno, strerror(errno));
        return 0;
    }

    // --- IMPORTANT PART: Pass ONLY the number as string ------------------
    // rnllama expects a pure numeric string, NOT a path.
    char *fdString = (char*) malloc(16);
    snprintf(fdString, 16, "%d", dupfd);
    defaultParams.model = fdString;

    // ---------------------------------------------------------------------

    defaultParams.embedding = embedding;
    defaultParams.n_ctx = n_ctx;
    defaultParams.n_batch = n_batch;

    int max_threads = std::thread::hardware_concurrency();
    int auto_threads = max_threads == 4 ? 2 : std::min(4, max_threads);
    defaultParams.cpuparams.n_threads =
            n_threads > 0 ? n_threads : auto_threads;

    defaultParams.n_gpu_layers = n_gpu_layers;
    defaultParams.use_mlock = false;   // safer on Android
    defaultParams.use_mmap = false;    // required for FD loading

    const char *lora_chars = env->GetStringUTFChars(lora_str, nullptr);
    if (lora_chars && lora_chars[0] != '\0') {
        defaultParams.lora_adapters.push_back({lora_chars, lora_scaled});
        defaultParams.use_mmap = false;
    }

    defaultParams.rope_freq_base = rope_freq_base;
    defaultParams.rope_freq_scale = rope_freq_scale;

    auto llama = new rnllama::llama_rn_context();
    bool ok = llama->loadModel(defaultParams);

    LOGI("[RNLlama] is_model_loaded = %s", ok ? "true" : "false");

    if (!ok) {
        close(dupfd);
        free(fdString);
        llama_free(llama->ctx);
        return 0;
    }

    // model loaded — context is valid
    context_map[(long) llama->ctx] = llama;

    env->ReleaseStringUTFChars(lora_str, lora_chars);
    return reinterpret_cast<jlong>(llama->ctx);
}

JNIEXPORT jobject JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_loadModelDetails(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr
) {
    UNUSED(thiz);
    auto llama = context_map[(long) context_ptr];

    int count = llama_model_meta_count(llama->model);
    auto meta = createHashMap(env);
    for (int i = 0; i < count; i++) {
        char key[256];
        llama_model_meta_key_by_index(llama->model, i, key, sizeof(key));
        char val[2048];
        llama_model_meta_val_str_by_index(llama->model, i, val, sizeof(val));

        putStringHashMap(env, meta, key, val);
    }

    auto result = createHashMap(env);

    char desc[1024];
    llama_model_desc(llama->model, desc, sizeof(desc));
    putStringHashMap(env, result, "desc", desc);
    putDoubleHashMap(env, result, "size", llama_model_size(llama->model));
    putDoubleHashMap(env, result, "nParams", llama_model_n_params(llama->model));
    putBooleanHashMap(env, result, "isChatTemplateSupported", llama->validateModelChatTemplate());
    putHashMapHashMap(env, result, "metadata", meta);

    return reinterpret_cast<jobject>(result);
}

JNIEXPORT jstring JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_getFormattedChat(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr,
        jobjectArray messages,
        jstring chat_template
) {
    UNUSED(thiz);
    auto llama = context_map[(long) context_ptr];

    std::vector<llama_chat_msg> chat;

    int messages_len = env->GetArrayLength(messages);
    for (int i = 0; i < messages_len; i++) {
        jobject msg = env->GetObjectArrayElement(messages, i);
        jclass msgClass = env->GetObjectClass(msg);

        // Use "get" instead of "getString" for a Map
        jmethodID getMethod = env->GetMethodID(msgClass, "get", "(Ljava/lang/Object;)Ljava/lang/Object;");

        // Create Java strings for the keys
        jstring roleKey = env->NewStringUTF("role");
        jstring contentKey = env->NewStringUTF("content");

        // Retrieve the role and content from the map using "get"
        jobject roleObj = env->CallObjectMethod(msg, getMethod, roleKey);
        jobject contentObj = env->CallObjectMethod(msg, getMethod, contentKey);

        // Cast the returned objects to jstring
        jstring role_str = (jstring) roleObj;
        jstring content_str = (jstring) contentObj;

        // Convert jstring to C-style strings
        const char *role = env->GetStringUTFChars(role_str, nullptr);
        const char *content = env->GetStringUTFChars(content_str, nullptr);

        // Add the role and content to the chat vector
        chat.push_back({ role, content });

        // Release memory for the jstrings
        env->ReleaseStringUTFChars(role_str, role);
        env->ReleaseStringUTFChars(content_str, content);
    }

    // Get the chat template as a C-style string
    const char *tmpl_chars = env->GetStringUTFChars(chat_template, nullptr);
    std::string formatted_chat = llama_chat_apply_template(llama->model, tmpl_chars, chat, true);

    // Release the chat template string
    env->ReleaseStringUTFChars(chat_template, tmpl_chars);

    // Return the formatted chat as a jstring
    return env->NewStringUTF(formatted_chat.c_str());
}

JNIEXPORT jobject JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_loadSession(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr,
        jstring path
) {
    UNUSED(thiz);
    auto llama = context_map[(long) context_ptr];
    const char *path_chars = env->GetStringUTFChars(path, nullptr);

    auto result = createHashMap(env);
    size_t n_token_count_out = 0;
    llama->embd.resize(llama->params.n_ctx);
    if (!llama_state_load_file(llama->ctx, path_chars, llama->embd.data(), llama->embd.capacity(), &n_token_count_out)) {
        env->ReleaseStringUTFChars(path, path_chars);

        putStringHashMap(env, result, "error", "Failed to load session");
        return reinterpret_cast<jobject>(result);
    }
    llama->embd.resize(n_token_count_out);
    env->ReleaseStringUTFChars(path, path_chars);

    const std::string text = rnllama::tokens_to_str(llama->ctx, llama->embd.cbegin(), llama->embd.cend());
    putIntHashMap(env, result, "tokens_loaded", n_token_count_out);
    putStringHashMap(env, result, "prompt", text.c_str());
    return reinterpret_cast<jobject>(result);
}

JNIEXPORT jint JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_saveSession(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr,
        jstring path,
        jint size
) {
    UNUSED(thiz);
    auto llama = context_map[(long) context_ptr];

    const char *path_chars = env->GetStringUTFChars(path, nullptr);

    std::vector<llama_token> session_tokens = llama->embd;
    int default_size = session_tokens.size();
    int save_size = size > 0 && size <= default_size ? size : default_size;
    if (!llama_state_save_file(llama->ctx, path_chars, session_tokens.data(), save_size)) {
        env->ReleaseStringUTFChars(path, path_chars);
        return -1;
    }

    env->ReleaseStringUTFChars(path, path_chars);
    return session_tokens.size();
}

static inline jobject tokenProbsToMap(
        JNIEnv *env,
        rnllama::llama_rn_context *llama,
        std::vector<rnllama::completion_token_output> probs
) {
    auto result = createArrayList(env);
    for (const auto &prob : probs) {
        auto probsForToken = createArrayList(env);
        for (const auto &p : prob.probs) {
            std::string tokStr = rnllama::tokens_to_output_formatted_string(llama->ctx, p.tok);
            auto probResult = createHashMap(env);
            putStringHashMap(env, probResult, "tok_str", tokStr.c_str());
            putDoubleHashMap(env, probResult, "prob", p.prob);
            addHashMapArrayList(env, probsForToken, probResult);
        }
        std::string tokStr = rnllama::tokens_to_output_formatted_string(llama->ctx, prob.tok);
        auto tokenResult = createHashMap(env);
        putStringHashMap(env, tokenResult, "content", tokStr.c_str());
        putArrayListHashMap(env, tokenResult, "probs", probsForToken);
        addHashMapArrayList(env, result, tokenResult);
    }
    return result;
}

JNIEXPORT jobject JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_doCompletion(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr,
        jstring prompt,
        jstring grammar,
        jfloat temperature,
        jint n_threads,
        jint n_predict,
        jint n_probs,
        jint penalty_last_n,
        jfloat penalty_repeat,
        jfloat penalty_freq,
        jfloat penalty_present,
        jfloat mirostat,
        jfloat mirostat_tau,
        jfloat mirostat_eta,
        jboolean penalize_nl,
        jint top_k,
        jfloat top_p,
        jfloat min_p,
        jfloat xtc_t,
        jfloat xtc_p,
        jfloat tfs_z,
        jfloat typical_p,
        jint seed,
        jobjectArray stop,
        jboolean ignore_eos,
        jobjectArray logit_bias,
        jobject partialCompletionCallback // The Java class for callbacks
) {
    UNUSED(thiz);
    auto llama = context_map[(long) context_ptr];

    llama->rewind();

    //llama_reset_timings(llama->ctx);

    llama->params.prompt = env->GetStringUTFChars(prompt, nullptr);
    llama->params.sparams.seed = (seed == -1) ? time(NULL) : seed;

    int max_threads = std::thread::hardware_concurrency();
    // Use 2 threads by default on 4-core devices, 4 threads on more cores
    int default_n_threads = max_threads == 4 ? 2 : min(4, max_threads);
    llama->params.cpuparams.n_threads = n_threads > 0 ? n_threads : default_n_threads;

    llama->params.n_predict = n_predict;
    llama->params.sparams.ignore_eos = ignore_eos;

    auto & sparams = llama->params.sparams;
    sparams.temp = temperature;
    sparams.penalty_last_n = penalty_last_n;
    sparams.penalty_repeat = penalty_repeat;
    sparams.penalty_freq = penalty_freq;
    sparams.penalty_present = penalty_present;
    sparams.mirostat = mirostat;
    sparams.mirostat_tau = mirostat_tau;
    sparams.mirostat_eta = mirostat_eta;
    sparams.penalize_nl = penalize_nl;
    sparams.top_k = top_k;
    sparams.top_p = top_p;
    sparams.min_p = min_p;
    sparams.tfs_z = tfs_z;
    sparams.typ_p = typical_p;
    sparams.n_probs = n_probs;
    sparams.grammar = env->GetStringUTFChars(grammar, nullptr);
    sparams.xtc_t = xtc_t;
    sparams.xtc_p = xtc_p;

    sparams.logit_bias.clear();
    if (ignore_eos) {
        sparams.logit_bias[llama_token_eos(llama->model)].bias = -INFINITY;
    }

    const int n_vocab = llama_n_vocab(llama_get_model(llama->ctx));
    jsize logit_bias_len = env->GetArrayLength(logit_bias);

    for (jsize i = 0; i < logit_bias_len; i++) {
        jdoubleArray el = (jdoubleArray) env->GetObjectArrayElement(logit_bias, i);
        if (el && env->GetArrayLength(el) == 2) {
            jdouble* doubleArray = env->GetDoubleArrayElements(el, 0);

            llama_token tok = static_cast<llama_token>(doubleArray[0]);
            if (tok >= 0 && tok < n_vocab) {
                if (doubleArray[1] != 0) {  // If the second element is not false (0)
                    sparams.logit_bias[tok].bias = doubleArray[1];
                } else {
                    sparams.logit_bias[tok].bias = -INFINITY;
                }
            }

            env->ReleaseDoubleArrayElements(el, doubleArray, 0);
        }
        env->DeleteLocalRef(el);
    }

    llama->params.antiprompt.clear();
    int stop_len = env->GetArrayLength(stop);
    for (int i = 0; i < stop_len; i++) {
        jstring stop_str = (jstring) env->GetObjectArrayElement(stop, i);
        const char *stop_chars = env->GetStringUTFChars(stop_str, nullptr);
        llama->params.antiprompt.push_back(stop_chars);
        env->ReleaseStringUTFChars(stop_str, stop_chars);
    }

    if (!llama->initSampling()) {
        auto result = createHashMap(env);
        putStringHashMap(env, result, "error", "Failed to initialize sampling");
        return reinterpret_cast<jobject>(result);
    }
    llama->beginCompletion();
    llama->loadPrompt();

    size_t sent_count = 0;
    size_t sent_token_probs_index = 0;

    while (llama->has_next_token && !llama->is_interrupted) {
        const rnllama::completion_token_output token_with_probs = llama->doCompletion();
        if (token_with_probs.tok == -1 || llama->incomplete) {
            continue;
        }
        const std::string token_text = llama_token_to_piece(llama->ctx, token_with_probs.tok);

        size_t pos = std::min(sent_count, llama->generated_text.size());

        const std::string str_test = llama->generated_text.substr(pos);
        bool is_stop_full = false;
        size_t stop_pos =
                llama->findStoppingStrings(str_test, token_text.size(), rnllama::STOP_FULL);
        if (stop_pos != std::string::npos) {
            is_stop_full = true;
            llama->generated_text.erase(
                    llama->generated_text.begin() + pos + stop_pos,
                    llama->generated_text.end());
            pos = std::min(sent_count, llama->generated_text.size());
        } else {
            is_stop_full = false;
            stop_pos = llama->findStoppingStrings(str_test, token_text.size(),
                                                  rnllama::STOP_PARTIAL);
        }

        if (
                stop_pos == std::string::npos ||
                // Send rest of the text if we are at the end of the generation
                (!llama->has_next_token && !is_stop_full && stop_pos > 0)
                ) {
            const std::string to_send = llama->generated_text.substr(pos, std::string::npos);

            sent_count += to_send.size();

            std::vector<rnllama::completion_token_output> probs_output = {};

            auto tokenResult = createHashMap(env);
            putStringHashMap(env, tokenResult, "token", to_send.c_str());

            if (llama->params.sparams.n_probs > 0) {
                const std::vector<llama_token> to_send_toks = llama_tokenize(llama->ctx, to_send, false);
                size_t probs_pos = std::min(sent_token_probs_index, llama->generated_token_probs.size());
                size_t probs_stop_pos = std::min(sent_token_probs_index + to_send_toks.size(), llama->generated_token_probs.size());
                if (probs_pos < probs_stop_pos) {
                    probs_output = std::vector<rnllama::completion_token_output>(llama->generated_token_probs.begin() + probs_pos, llama->generated_token_probs.begin() + probs_stop_pos);
                }
                sent_token_probs_index = probs_stop_pos;

                putArrayListHashMap(env, tokenResult, "completion_probabilities", tokenProbsToMap(env, llama, probs_output));
            }

            jclass cb_class = env->GetObjectClass(partialCompletionCallback); // Get class of callback object
            jmethodID onPartialCompletion = env->GetMethodID(cb_class, "onPartialCompletion", "(Ljava/util/Map;)V"); // Find method ID
            env->CallVoidMethod(partialCompletionCallback, onPartialCompletion, tokenResult); // Call method
        }
    }

    llama_perf_context_print(llama->ctx);
    llama->is_predicting = false;

    auto result = createHashMap(env);
    putStringHashMap(env, result, "text", llama->generated_text.c_str());
    putArrayListHashMap(env, result, "completion_probabilities", tokenProbsToMap(env, llama, llama->generated_token_probs));
    putIntHashMap(env, result, "tokens_predicted", llama->num_tokens_predicted);
    putIntHashMap(env, result, "tokens_evaluated", llama->num_prompt_tokens);
    putIntHashMap(env, result, "truncated", llama->truncated);
    putIntHashMap(env, result, "stopped_eos", llama->stopped_eos);
    putIntHashMap(env, result, "stopped_word", llama->stopped_word);
    putIntHashMap(env, result, "stopped_limit", llama->stopped_limit);
    putStringHashMap(env, result, "stopping_word", llama->stopping_word.c_str());
    putIntHashMap(env, result, "tokens_cached", llama->n_past);

    const auto timings_token = llama_perf_context(llama -> ctx);

    auto timingsResult = createHashMap(env);
    putIntHashMap(env, timingsResult, "prompt_n", timings_token.n_p_eval);
    putIntHashMap(env, timingsResult, "prompt_ms", timings_token.t_p_eval_ms);
    putIntHashMap(env, timingsResult, "prompt_per_token_ms", timings_token.t_p_eval_ms / timings_token.n_p_eval);
    putDoubleHashMap(env, timingsResult, "prompt_per_second", 1e3 / timings_token.t_p_eval_ms * timings_token.n_p_eval);
    putIntHashMap(env, timingsResult, "predicted_n", timings_token.n_eval);
    putIntHashMap(env, timingsResult, "predicted_ms", timings_token.t_eval_ms);
    putIntHashMap(env, timingsResult, "predicted_per_token_ms", timings_token.t_eval_ms / timings_token.n_eval);
    putDoubleHashMap(env, timingsResult, "predicted_per_second", 1e3 / timings_token.t_eval_ms * timings_token.n_eval);

    putHashMapHashMap(env, result, "timings", timingsResult);

    return reinterpret_cast<jobject>(result);
}

JNIEXPORT void JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_stopCompletion(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    auto llama = context_map[(long) context_ptr];
    llama->is_interrupted = true;
}

JNIEXPORT jboolean JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_isPredicting(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    auto llama = context_map[(long) context_ptr];
    return llama->is_predicting;
}

JNIEXPORT jobject JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_tokenize(
        JNIEnv *env, jobject thiz, jlong context_ptr, jstring text) {
    UNUSED(thiz);
    auto llama = context_map[(long) context_ptr];

    const char *text_chars = env->GetStringUTFChars(text, nullptr);

    const std::vector<llama_token> toks = llama_tokenize(
            llama->ctx,
            text_chars,
            false
    );

    jobject result = createArrayList(env);
    for (const auto &tok : toks) {
        addIntArrayList(env, result, tok);
    }

    env->ReleaseStringUTFChars(text, text_chars);
    return result;
}

JNIEXPORT jstring JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_detokenize(
        JNIEnv *env, jobject thiz, jlong context_ptr, jintArray tokens) {
    UNUSED(thiz);
    auto llama = context_map[(long) context_ptr];

    jsize tokens_len = env->GetArrayLength(tokens);
    jint *tokens_ptr = env->GetIntArrayElements(tokens, 0);
    std::vector<llama_token> toks;
    for (int i = 0; i < tokens_len; i++) {
        toks.push_back(tokens_ptr[i]);
    }

    auto text = rnllama::tokens_to_str(llama->ctx, toks.cbegin(), toks.cend());

    env->ReleaseIntArrayElements(tokens, tokens_ptr, 0);

    return env->NewStringUTF(text.c_str());
}

JNIEXPORT jboolean JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_isEmbeddingEnabled(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    auto llama = context_map[(long) context_ptr];
    return llama->params.embedding;
}

JNIEXPORT jobject JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_embedding(
        JNIEnv *env, jobject thiz, jlong context_ptr, jstring text) {
    UNUSED(thiz);
    auto llama = context_map[(long) context_ptr];

    const char *text_chars = env->GetStringUTFChars(text, nullptr);

    llama->rewind();

    llama_perf_context_reset(llama->ctx);

    llama->params.prompt = text_chars;

    llama->params.n_predict = 0;

    auto result = createHashMap(env);
    if (!llama->initSampling()) {
        putStringHashMap(env, result, "error", "Failed to initialize sampling");
        return reinterpret_cast<jobject>(result);
    }

    llama->beginCompletion();
    llama->loadPrompt();
    llama->doCompletion();

    std::vector<float> embedding = llama->getEmbedding();

    auto embeddings = createArrayList(env);
    for (const auto &val : embedding) {
        addDoubleArrayList(env, embeddings, (double) val);
    }
    putArrayListHashMap(env, result, "embedding", embeddings);

    env->ReleaseStringUTFChars(text, text_chars);
    return result;
}

JNIEXPORT jstring JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_bench(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr,
        jint pp,
        jint tg,
        jint pl,
        jint nr
) {
    UNUSED(thiz);
    auto llama = context_map[(long) context_ptr];
    std::string result = llama->bench(pp, tg, pl, nr);
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_org_nehuatl_llamacpp_LlamaContext_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    auto llama = context_map[(long) context_ptr];
    if (llama->model) {
        llama_free_model(llama->model);
    }
    if (llama->ctx) {
        llama_free(llama->ctx);
    }
    if (llama->ctx_sampling != nullptr)
    {
        gpt_sampler_free(llama->ctx_sampling);
    }
    context_map.erase((long) llama->ctx);
}

} // extern "C"