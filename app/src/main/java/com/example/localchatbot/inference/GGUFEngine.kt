package com.example.localchatbot.inference

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GGUFEngine : InferenceEngine {
    
    companion object {
        private const val TAG = "GGUFEngine"
        private const val DEFAULT_CONTEXT_SIZE = 512
        private const val DEFAULT_GPU_LAYERS = 0
        private const val DEFAULT_MAX_TOKENS = 256
        private const val DEFAULT_TEMPERATURE = 0.7f
        private const val DEFAULT_TOP_P = 0.9f
        private const val DEFAULT_TOP_K = 40
        private const val DEFAULT_REPEAT_PENALTY = 1.1f
    }

    private val initLock = Any()
    private var isInitialized = false
    private var isLibraryLoaded = false
    private var modelPath: String? = null
    private var modelPtr: Long = 0

    override suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(initLock) {
            try {
                if (!isLibraryLoaded) {
                    if (!LlamaCpp.loadLibrary()) {
                        Log.e(TAG, "Failed to load llama library")
                        return@withContext false
                    }
                    LlamaCpp.backendInit()
                    isLibraryLoaded = true
                }

                if (this@GGUFEngine.modelPtr != 0L) {
                    LlamaCpp.freeModel(this@GGUFEngine.modelPtr)
                    this@GGUFEngine.modelPtr = 0
                }

                Log.d(TAG, "Loading model from: $modelPath")
                val ptr = LlamaCpp.loadModel(modelPath, DEFAULT_CONTEXT_SIZE, DEFAULT_GPU_LAYERS)
                
                if (ptr == 0L) {
                    Log.e(TAG, "Failed to load model")
                    return@withContext false
                }

                this@GGUFEngine.modelPtr = ptr
                this@GGUFEngine.modelPath = modelPath
                isInitialized = true
                Log.d(TAG, "Model loaded successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing engine", e)
                false
            }
        }
    }

    override suspend fun inference(prompt: String): String = withContext(Dispatchers.IO) {
        if (!isReady()) {
            throw IllegalStateException("Engine not initialized")
        }
        
        try {
            LlamaCpp.generate(
                modelPtr,
                prompt,
                DEFAULT_MAX_TOKENS,
                DEFAULT_TEMPERATURE,
                DEFAULT_TOP_P,
                DEFAULT_TOP_K,
                DEFAULT_REPEAT_PENALTY
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            throw e
        }
    }

    override suspend fun inferenceWithParams(prompt: String, maxTokens: Int): String = withContext(Dispatchers.IO) {
        if (!isReady()) {
            throw IllegalStateException("Engine not initialized")
        }
        
        try {
            LlamaCpp.generate(
                modelPtr,
                prompt,
                maxTokens,
                DEFAULT_TEMPERATURE,
                DEFAULT_TOP_P,
                DEFAULT_TOP_K,
                DEFAULT_REPEAT_PENALTY
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during inference", e)
            throw e
        }
    }

    suspend fun generateStreaming(
        prompt: String,
        maxTokens: Int,
        onToken: (String) -> Boolean
    ): String = withContext(Dispatchers.IO) {
        if (!isReady()) {
            throw IllegalStateException("Engine not initialized")
        }
        
        val result = StringBuilder()
        val callback = object : LlamaCpp.StreamCallback {
            override fun onToken(token: String): Boolean {
                result.append(token)
                return onToken(token)
            }
        }
        
        LlamaCpp.generateStreaming(modelPtr, prompt, maxTokens, DEFAULT_TEMPERATURE, callback)
        result.toString()
    }

    override fun isReady(): Boolean = isInitialized && modelPtr != 0L

    override fun shutdown() {
        synchronized(initLock) {
            Log.d(TAG, "Shutting down GGUF engine")
            if (modelPtr != 0L) {
                try {
                    LlamaCpp.freeModel(modelPtr)
                    Log.d(TAG, "Model freed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error freeing model", e)
                }
                modelPtr = 0
            }
            modelPath = null
            isInitialized = false
            Log.d(TAG, "GGUF engine shutdown complete")
        }
    }

    override fun getEngineType(): EngineType = EngineType.GGUF

    fun getEngineName(): String = getEngineType().displayName
}
