package com.example.localchatbot.inference

import android.util.Log

object LlamaCpp {
    private const val TAG = "LlamaCpp"
    private var isLoaded = false

    interface StreamCallback {
        fun onToken(token: String): Boolean
    }

    fun loadLibrary(): Boolean {
        if (isLoaded) return true
        return try {
            System.loadLibrary("llama-android")
            isLoaded = true
            Log.d(TAG, "llama-android library loaded successfully")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load llama-android library", e)
            false
        }
    }

    fun isLibraryLoaded(): Boolean = isLoaded

    external fun backendInit()
    external fun backendFree()
    external fun loadModel(modelPath: String, nCtx: Int = 2048, nGpuLayers: Int = 0): Long
    external fun freeModel(modelPtr: Long)
    external fun getModelInfo(modelPtr: Long): String
    external fun generate(
        modelPtr: Long,
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40,
        repeatPenalty: Float = 1.1f
    ): String
    external fun generateStreaming(
        modelPtr: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: StreamCallback
    )
    external fun stopGeneration()
}
