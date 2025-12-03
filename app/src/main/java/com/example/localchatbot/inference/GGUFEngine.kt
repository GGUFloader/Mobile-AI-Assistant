package com.example.localchatbot.inference

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class GGUFEngine : InferenceEngine {
    
    companion object {
        private const val TAG = "GGUFEngine"
        private const val DEFAULT_CONTEXT_SIZE = 2048  // Larger context for better performance
        private const val DEFAULT_GPU_LAYERS = 0       // CPU only (no GPU)
        private const val DEFAULT_MAX_TOKENS = 200     // Enough for good summaries
        private const val DEFAULT_TEMPERATURE = 0.0f   // Greedy sampling = fastest
        private const val DEFAULT_TOP_P = 1.0f
        private const val DEFAULT_TOP_K = 1            // Only top token = fastest
        private const val DEFAULT_REPEAT_PENALTY = 1.0f // No penalty = faster
        private const val SHUTDOWN_WAIT_MS = 5000L
    }

    private val initLock = Any()
    private var isInitialized = false
    private var isLibraryLoaded = false
    private var modelPath: String? = null
    private var modelPtr: Long = 0
    
    // Track active inference operations to prevent use-after-free
    private val activeInferenceCount = AtomicInteger(0)
    private val isShuttingDown = AtomicBoolean(false)

    override suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(initLock) {
            try {
                // Reset shutdown flag for new initialization
                isShuttingDown.set(false)
                
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
        // Check if shutting down
        if (isShuttingDown.get()) {
            throw IllegalStateException("Engine is shutting down")
        }
        
        if (!isReady()) {
            throw IllegalStateException("Engine not initialized")
        }
        
        // Track this inference operation
        activeInferenceCount.incrementAndGet()
        try {
            // Double-check after incrementing counter
            if (isShuttingDown.get() || modelPtr == 0L) {
                throw IllegalStateException("Engine is shutting down")
            }
            
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
        } finally {
            activeInferenceCount.decrementAndGet()
        }
    }

    override suspend fun inferenceWithParams(prompt: String, maxTokens: Int): String = withContext(Dispatchers.IO) {
        // Check if shutting down
        if (isShuttingDown.get()) {
            throw IllegalStateException("Engine is shutting down")
        }
        
        if (!isReady()) {
            throw IllegalStateException("Engine not initialized")
        }
        
        // Track this inference operation
        activeInferenceCount.incrementAndGet()
        try {
            // Double-check after incrementing counter
            if (isShuttingDown.get() || modelPtr == 0L) {
                throw IllegalStateException("Engine is shutting down")
            }
            
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
        } finally {
            activeInferenceCount.decrementAndGet()
        }
    }

    suspend fun generateStreaming(
        prompt: String,
        maxTokens: Int,
        onToken: (String) -> Boolean
    ): String = withContext(Dispatchers.IO) {
        // Check if shutting down
        if (isShuttingDown.get()) {
            throw IllegalStateException("Engine is shutting down")
        }
        
        if (!isReady()) {
            throw IllegalStateException("Engine not initialized")
        }
        
        // Track this inference operation
        activeInferenceCount.incrementAndGet()
        try {
            // Double-check after incrementing counter
            if (isShuttingDown.get() || modelPtr == 0L) {
                throw IllegalStateException("Engine is shutting down")
            }
            
            val result = StringBuilder()
            val callback = object : LlamaCpp.StreamCallback {
                override fun onToken(token: String): Boolean {
                    // Stop if shutting down
                    if (isShuttingDown.get()) {
                        return false
                    }
                    result.append(token)
                    return onToken(token)
                }
            }
            
            LlamaCpp.generateStreaming(modelPtr, prompt, maxTokens, DEFAULT_TEMPERATURE, callback)
            result.toString()
        } finally {
            activeInferenceCount.decrementAndGet()
        }
    }

    override fun isReady(): Boolean = isInitialized && modelPtr != 0L && !isShuttingDown.get()

    override fun shutdown() {
        Log.d(TAG, "Shutting down GGUF engine")
        
        // Signal that we're shutting down - this will stop new inference and abort streaming
        isShuttingDown.set(true)
        
        // Stop any ongoing generation
        try {
            LlamaCpp.stopGeneration()
            Log.d(TAG, "Stop generation signal sent")
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping generation", e)
        }
        
        // Wait for active inference operations to complete
        val startTime = System.currentTimeMillis()
        while (activeInferenceCount.get() > 0) {
            if (System.currentTimeMillis() - startTime > SHUTDOWN_WAIT_MS) {
                Log.w(TAG, "Timeout waiting for inference to complete, forcing shutdown")
                break
            }
            Thread.sleep(50)
        }
        
        synchronized(initLock) {
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
