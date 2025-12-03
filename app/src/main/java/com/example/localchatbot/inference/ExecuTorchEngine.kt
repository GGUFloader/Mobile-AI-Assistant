package com.example.localchatbot.inference

import android.util.Log

/**
 * Stub ExecuTorch engine - ExecuTorch AAR classes not available.
 * 
 * To enable ExecuTorch support, you need to build the AAR from source:
 * https://github.com/pytorch/executorch/tree/main/examples/demo-apps/android/LlamaDemo
 * 
 * The AAR must contain both:
 * - Java classes (org.pytorch.executorch.Module, EValue, Tensor)
 * - Native libraries (libexecutorch_jni.so, libfbjni.so, etc.)
 */
class ExecuTorchEngine : InferenceEngine {
    
    companion object {
        private const val TAG = "ExecuTorchEngine"
        const val IS_AVAILABLE = false
    }

    override suspend fun initialize(modelPath: String): Boolean {
        Log.e(TAG, "ExecuTorch is not available - AAR missing Java classes")
        return false
    }

    override suspend fun inference(prompt: String): String {
        throw UnsupportedOperationException("ExecuTorch is not available - use GGUF models instead")
    }

    override suspend fun inferenceWithParams(prompt: String, maxTokens: Int): String {
        throw UnsupportedOperationException("ExecuTorch is not available - use GGUF models instead")
    }

    override fun isReady(): Boolean = false

    override fun shutdown() {
        // No-op
    }

    override fun getEngineType(): EngineType = EngineType.EXECUTORCH
}
