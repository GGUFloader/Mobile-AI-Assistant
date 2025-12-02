#include <jni.h>
#include <android/log.h>
#include <string>
#include <thread>
#include <atomic>
#include <vector>
#include <unistd.h>

#include "llama.h"
#include "common.h"

#define TAG "LlamaCpp"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// Global stop flag for generation
static std::atomic<bool> g_stop_generation{false};

// Structure to hold model and context together
struct LlamaModel {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    llama_sampler* sampler = nullptr;
    int n_ctx = 2048;
};

static void log_callback(ggml_log_level level, const char* fmt, void* data) {
    if (level == GGML_LOG_LEVEL_ERROR) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", fmt);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "%s", fmt);
    } else if (level == GGML_LOG_LEVEL_INFO) {
        __android_log_print(ANDROID_LOG_INFO, TAG, "%s", fmt);
    }
}

// Check if string is valid UTF-8
static bool is_valid_utf8(const char* string) {
    if (!string) return true;
    
    const unsigned char* bytes = (const unsigned char*)string;
    while (*bytes != 0x00) {
        int num;
        if ((*bytes & 0x80) == 0x00) {
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            num = 4;
        } else {
            return false;
        }
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) return false;
            bytes += 1;
        }
    }
    return true;
}

// Check if the generated text contains stop sequences (model trying to continue conversation)
static bool contains_stop_sequence(const std::string& text) {
    // Common stop sequences for various model formats (Alpaca, ChatML, Vicuna, etc.)
    static const char* stop_sequences[] = {
        "### Instruction:",
        "### Input:",
        "### Response:",
        "### Human:",
        "### Assistant:",
        "<|im_end|>",
        "<|im_start|>",
        "<|user|>",
        "<|assistant|>",
        "<|endoftext|>",
        "</s>",
        "\nUser:",
        "\nHuman:",
        "\nAssistant:",
        "\n\nUser:",
        "\n\nHuman:",
        nullptr
    };
    
    for (int i = 0; stop_sequences[i] != nullptr; i++) {
        if (text.find(stop_sequences[i]) != std::string::npos) {
            return true;
        }
    }
    return false;
}

// Trim stop sequences from the end of generated text
static std::string trim_at_stop_sequence(const std::string& text) {
    static const char* stop_sequences[] = {
        "### Instruction:",
        "### Input:",
        "### Response:",
        "### Human:",
        "### Assistant:",
        "<|im_end|>",
        "<|im_start|>",
        "<|user|>",
        "<|assistant|>",
        "<|endoftext|>",
        "</s>",
        "\nUser:",
        "\nHuman:",
        "\nAssistant:",
        "\n\nUser:",
        "\n\nHuman:",
        nullptr
    };
    
    std::string result = text;
    size_t earliest_pos = std::string::npos;
    
    for (int i = 0; stop_sequences[i] != nullptr; i++) {
        size_t pos = result.find(stop_sequences[i]);
        if (pos != std::string::npos && (earliest_pos == std::string::npos || pos < earliest_pos)) {
            earliest_pos = pos;
        }
    }
    
    if (earliest_pos != std::string::npos) {
        result = result.substr(0, earliest_pos);
    }
    
    return result;
}


extern "C" {

JNIEXPORT void JNICALL
Java_com_example_localchatbot_inference_LlamaCpp_backendInit(JNIEnv* env, jobject thiz) {
    LOGI("Initializing llama backend");
    llama_backend_init();
    llama_log_set(log_callback, nullptr);
    LOGI("Backend initialized successfully");
}

JNIEXPORT void JNICALL
Java_com_example_localchatbot_inference_LlamaCpp_backendFree(JNIEnv* env, jobject thiz) {
    LOGI("Freeing llama backend");
    llama_backend_free();
}

JNIEXPORT jlong JNICALL
Java_com_example_localchatbot_inference_LlamaCpp_loadModel(
    JNIEnv* env,
    jobject thiz,
    jstring model_path,
    jint n_ctx,
    jint n_gpu_layers
) {
    const char* path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading model from: %s", path);
    LOGI("Context size: %d, GPU layers: %d", n_ctx, n_gpu_layers);

    // Create model params
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = n_gpu_layers;

    // Load the model
    llama_model* model = llama_model_load_from_file(path, model_params);
    env->ReleaseStringUTFChars(model_path, path);

    if (!model) {
        LOGE("Failed to load model");
        return 0;
    }

    // Create context params
    int n_threads = std::max(1, std::min(8, (int)sysconf(_SC_NPROCESSORS_ONLN) - 2));
    LOGI("Using %d threads", n_threads);

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_ctx > 0 ? n_ctx : 2048;
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;

    // Create context
    llama_context* ctx = llama_new_context_with_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_model_free(model);
        return 0;
    }

    // Create sampler
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler* sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    // Create wrapper structure
    LlamaModel* wrapper = new LlamaModel();
    wrapper->model = model;
    wrapper->ctx = ctx;
    wrapper->sampler = sampler;
    wrapper->n_ctx = ctx_params.n_ctx;

    LOGI("Model loaded successfully, ptr: %p", wrapper);
    return reinterpret_cast<jlong>(wrapper);
}

JNIEXPORT void JNICALL
Java_com_example_localchatbot_inference_LlamaCpp_freeModel(
    JNIEnv* env,
    jobject thiz,
    jlong model_ptr
) {
    if (model_ptr == 0) return;

    LlamaModel* wrapper = reinterpret_cast<LlamaModel*>(model_ptr);
    LOGI("Freeing model, ptr: %p", wrapper);

    if (wrapper->sampler) {
        llama_sampler_free(wrapper->sampler);
    }
    if (wrapper->ctx) {
        llama_free(wrapper->ctx);
    }
    if (wrapper->model) {
        llama_model_free(wrapper->model);
    }
    delete wrapper;
    LOGI("Model freed successfully");
}

JNIEXPORT jstring JNICALL
Java_com_example_localchatbot_inference_LlamaCpp_getModelInfo(
    JNIEnv* env,
    jobject thiz,
    jlong model_ptr
) {
    if (model_ptr == 0) {
        return env->NewStringUTF("No model loaded");
    }

    LlamaModel* wrapper = reinterpret_cast<LlamaModel*>(model_ptr);
    char desc[256];
    llama_model_desc(wrapper->model, desc, sizeof(desc));
    return env->NewStringUTF(desc);
}


JNIEXPORT jstring JNICALL
Java_com_example_localchatbot_inference_LlamaCpp_generate(
    JNIEnv* env,
    jobject thiz,
    jlong model_ptr,
    jstring prompt,
    jint max_tokens,
    jfloat temperature,
    jfloat top_p,
    jint top_k,
    jfloat repeat_penalty
) {
    g_stop_generation = false;

    if (model_ptr == 0) {
        LOGE("Model not loaded");
        return env->NewStringUTF("Error: Model not loaded");
    }

    LlamaModel* wrapper = reinterpret_cast<LlamaModel*>(model_ptr);
    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    LOGI("Generating response for prompt (length: %zu)", strlen(prompt_str));

    // Clear KV cache
    llama_memory_clear(llama_get_memory(wrapper->ctx), true);

    // Tokenize the prompt
    std::vector<llama_token> tokens = common_tokenize(wrapper->ctx, prompt_str, true, true);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    LOGI("Tokenized prompt: %zu tokens", tokens.size());

    if (tokens.empty()) {
        LOGE("Failed to tokenize prompt");
        return env->NewStringUTF("Error: Failed to tokenize prompt");
    }

    // Check context size
    int n_ctx = llama_n_ctx(wrapper->ctx);
    if ((int)tokens.size() + max_tokens > n_ctx) {
        LOGW("Prompt + max_tokens exceeds context size, truncating");
        max_tokens = std::max(1, n_ctx - (int)tokens.size() - 10);
    }

    // Create batch for prompt processing
    llama_batch batch = llama_batch_init(std::max((int)tokens.size(), max_tokens), 0, 1);

    // Add prompt tokens to batch
    for (size_t i = 0; i < tokens.size(); i++) {
        common_batch_add(batch, tokens[i], i, {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true;

    // Process prompt
    if (llama_decode(wrapper->ctx, batch) != 0) {
        LOGE("llama_decode failed for prompt");
        llama_batch_free(batch);
        return env->NewStringUTF("Error: Failed to process prompt");
    }

    // Create sampler with parameters
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    
    // Add samplers based on parameters
    if (temperature > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(top_k));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    }

    // Generate tokens
    std::string result;
    std::string cached_chars;
    int n_cur = tokens.size();
    const llama_vocab* vocab = llama_model_get_vocab(wrapper->model);

    for (int i = 0; i < max_tokens && !g_stop_generation; i++) {
        // Sample next token
        llama_token new_token = llama_sampler_sample(smpl, wrapper->ctx, -1);

        // Check for end of generation
        if (llama_vocab_is_eog(vocab, new_token)) {
            LOGI("End of generation token received");
            break;
        }

        // Convert token to text
        std::string token_str = common_token_to_piece(wrapper->ctx, new_token);
        cached_chars += token_str;

        // Only add valid UTF-8 to result
        if (is_valid_utf8(cached_chars.c_str())) {
            result += cached_chars;
            cached_chars.clear();
        }

        // Check for stop sequences (model trying to continue conversation)
        if (contains_stop_sequence(result)) {
            LOGI("Stop sequence detected, trimming output");
            result = trim_at_stop_sequence(result);
            break;
        }

        // Prepare next batch
        common_batch_clear(batch);
        common_batch_add(batch, new_token, n_cur, {0}, true);
        n_cur++;

        // Decode
        if (llama_decode(wrapper->ctx, batch) != 0) {
            LOGE("llama_decode failed during generation");
            break;
        }
    }

    // Add any remaining cached characters
    if (!cached_chars.empty() && is_valid_utf8(cached_chars.c_str())) {
        result += cached_chars;
    }

    // Final trim of any stop sequences
    result = trim_at_stop_sequence(result);

    llama_sampler_free(smpl);
    llama_batch_free(batch);

    LOGI("Generated %zu characters", result.size());
    return env->NewStringUTF(result.c_str());
}


JNIEXPORT void JNICALL
Java_com_example_localchatbot_inference_LlamaCpp_generateStreaming(
    JNIEnv* env,
    jobject thiz,
    jlong model_ptr,
    jstring prompt,
    jint max_tokens,
    jfloat temperature,
    jobject callback
) {
    g_stop_generation = false;

    if (model_ptr == 0) {
        LOGE("Model not loaded");
        return;
    }

    LlamaModel* wrapper = reinterpret_cast<LlamaModel*>(model_ptr);
    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    LOGI("Streaming generation for prompt (length: %zu)", strlen(prompt_str));

    // Get callback method
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;)Z");

    // Clear KV cache
    llama_memory_clear(llama_get_memory(wrapper->ctx), true);

    // Tokenize the prompt
    std::vector<llama_token> tokens = common_tokenize(wrapper->ctx, prompt_str, true, true);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    LOGI("Tokenized prompt: %zu tokens", tokens.size());

    if (tokens.empty()) {
        LOGE("Failed to tokenize prompt");
        jstring error = env->NewStringUTF("Error: Failed to tokenize");
        env->CallBooleanMethod(callback, onTokenMethod, error);
        env->DeleteLocalRef(error);
        return;
    }

    // Check context size
    int n_ctx = llama_n_ctx(wrapper->ctx);
    if ((int)tokens.size() + max_tokens > n_ctx) {
        LOGW("Prompt + max_tokens exceeds context size, truncating");
        max_tokens = std::max(1, n_ctx - (int)tokens.size() - 10);
    }

    // Create batch
    llama_batch batch = llama_batch_init(std::max((int)tokens.size(), max_tokens), 0, 1);

    // Add prompt tokens to batch
    for (size_t i = 0; i < tokens.size(); i++) {
        common_batch_add(batch, tokens[i], i, {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true;

    // Process prompt
    if (llama_decode(wrapper->ctx, batch) != 0) {
        LOGE("llama_decode failed for prompt");
        llama_batch_free(batch);
        jstring error = env->NewStringUTF("Error: Failed to process prompt");
        env->CallBooleanMethod(callback, onTokenMethod, error);
        env->DeleteLocalRef(error);
        return;
    }

    // Create sampler
    auto sparams = llama_sampler_chain_default_params();
    sparams.no_perf = true;
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    
    if (temperature > 0.0f) {
        llama_sampler_chain_add(smpl, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(smpl, llama_sampler_init_top_p(0.9f, 1));
        llama_sampler_chain_add(smpl, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(smpl, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
    } else {
        llama_sampler_chain_add(smpl, llama_sampler_init_greedy());
    }

    // Generate tokens
    std::string cached_chars;
    std::string full_response;  // Track full response for stop sequence detection
    int n_cur = tokens.size();
    const llama_vocab* vocab = llama_model_get_vocab(wrapper->model);

    for (int i = 0; i < max_tokens && !g_stop_generation; i++) {
        // Sample next token
        llama_token new_token = llama_sampler_sample(smpl, wrapper->ctx, -1);

        // Check for end of generation
        if (llama_vocab_is_eog(vocab, new_token)) {
            LOGI("End of generation token received");
            break;
        }

        // Convert token to text
        std::string token_str = common_token_to_piece(wrapper->ctx, new_token);
        cached_chars += token_str;

        // Send valid UTF-8 to callback
        if (is_valid_utf8(cached_chars.c_str())) {
            full_response += cached_chars;
            
            // Check for stop sequences before sending
            if (contains_stop_sequence(full_response)) {
                LOGI("Stop sequence detected in streaming, stopping");
                break;
            }
            
            jstring token = env->NewStringUTF(cached_chars.c_str());
            jboolean shouldContinue = env->CallBooleanMethod(callback, onTokenMethod, token);
            env->DeleteLocalRef(token);
            cached_chars.clear();

            if (!shouldContinue) {
                LOGI("Callback requested stop");
                break;
            }
        }

        // Prepare next batch
        common_batch_clear(batch);
        common_batch_add(batch, new_token, n_cur, {0}, true);
        n_cur++;

        // Decode
        if (llama_decode(wrapper->ctx, batch) != 0) {
            LOGE("llama_decode failed during generation");
            break;
        }
    }

    // Send any remaining cached characters (only if no stop sequence)
    if (!cached_chars.empty() && is_valid_utf8(cached_chars.c_str()) && !contains_stop_sequence(cached_chars)) {
        jstring token = env->NewStringUTF(cached_chars.c_str());
        env->CallBooleanMethod(callback, onTokenMethod, token);
        env->DeleteLocalRef(token);
    }

    llama_sampler_free(smpl);
    llama_batch_free(batch);
    LOGI("Streaming generation complete");
}

JNIEXPORT void JNICALL
Java_com_example_localchatbot_inference_LlamaCpp_stopGeneration(JNIEnv* env, jobject thiz) {
    LOGI("Stop generation requested");
    g_stop_generation = true;
}

} // extern "C"
