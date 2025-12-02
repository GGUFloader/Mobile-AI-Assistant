package com.example.localchatbot.inference

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import java.util.Locale

class ExecuTorchEngine : InferenceEngine {
    
    companion object {
        private const val TAG = "ExecuTorchEngine"
        private const val DEFAULT_MAX_TOKENS = 256
        private const val MAX_CONTEXT_LENGTH = 512
        private const val EOS_TOKEN = 2L
        private const val PAD_TOKEN = 0L
    }

    private val initLock = Any()
    private var isInitialized = false
    private var modelPath: String? = null
    private var module: Module? = null

    override suspend fun initialize(modelPath: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(initLock) {
            try {
                if (modelPath.isBlank()) {
                    throw IllegalArgumentException("Model path cannot be empty")
                }
                
                val modelFile = File(modelPath)
                if (!modelFile.exists()) {
                    Log.e(TAG, "Model file does not exist: $modelPath")
                    return@withContext false
                }
                
                if (!modelFile.canRead()) {
                    Log.e(TAG, "Model file is not readable: $modelPath")
                    return@withContext false
                }
                
                val extension = modelFile.extension.lowercase(Locale.ROOT)
                if (extension != "pte") {
                    Log.e(TAG, "Invalid file extension for ExecuTorch engine: $extension (expected .pte)")
                    return@withContext false
                }
                
                Log.d(TAG, "Initializing ExecuTorch engine with model: $modelPath")
                
                // Destroy previous module if exists
                module?.let {
                    Log.d(TAG, "Destroying previous module")
                    try {
                        it.destroy()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error destroying previous module", e)
                    }
                    module = null
                }
                
                // Load the new module
                val loadedModule = try {
                    Module.load(modelPath)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during module loading", e)
                    null
                }
                
                if (loadedModule == null) {
                    Log.e(TAG, "Failed to load module - returned null")
                    isInitialized = false
                    return@withContext false
                }
                
                module = loadedModule
                this@ExecuTorchEngine.modelPath = modelPath
                isInitialized = true
                Log.d(TAG, "ExecuTorch model loaded successfully")
                true
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid argument during initialization", e)
                isInitialized = false
                false
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during initialization", e)
                isInitialized = false
                false
            }
        }
    }

    override suspend fun inference(prompt: String): String = withContext(Dispatchers.IO) {
        inferenceWithParams(prompt, DEFAULT_MAX_TOKENS)
    }

    override suspend fun inferenceWithParams(prompt: String, maxTokens: Int): String = withContext(Dispatchers.IO) {
        if (!isReady()) {
            throw IllegalStateException("ExecuTorch engine not initialized")
        }
        executeInference(prompt, maxTokens)
    }

    private fun executeInference(prompt: String, maxTokens: Int): String {
        var currentIds = tokenize(prompt)
        Log.d(TAG, "Input tokens: ${currentIds.size}")
        
        val outputBuilder = StringBuilder()
        
        for (i in 0 until maxTokens) {
            val seqLen = currentIds.size
            val inputTensor = Tensor.fromBlob(currentIds, longArrayOf(1, seqLen.toLong()))
            
            // Create attention mask (all 1s)
            val attentionMask = LongArray(seqLen) { 1L }
            val attentionMaskTensor = Tensor.fromBlob(attentionMask, longArrayOf(1, seqLen.toLong()))
            
            try {
                val outputs = module!!.forward(
                    EValue.from(inputTensor),
                    EValue.from(attentionMaskTensor)
                )
                
                if (outputs.isEmpty()) {
                    Log.w(TAG, "Empty output from model at step $i")
                    break
                }
                
                val outputTensor = try {
                    outputs[0].toTensor()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to convert output to tensor at step $i", e)
                    break
                }
                
                val logits = try {
                    outputTensor.dataAsFloatArray
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to extract logits at step $i", e)
                    break
                }
                
                val nextToken = sampleNextToken(logits!!, currentIds.size)
                
                // Check for end of sequence
                if (nextToken == EOS_TOKEN || nextToken == PAD_TOKEN) {
                    Log.d(TAG, "EOS token reached at step $i")
                    break
                }
                
                val decoded = decodeToken(nextToken)
                outputBuilder.append(decoded)
                
                // Append token and manage context length
                currentIds = currentIds + nextToken
                if (currentIds.size > MAX_CONTEXT_LENGTH) {
                    currentIds = currentIds.takeLast(MAX_CONTEXT_LENGTH).toLongArray()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Forward pass failed at step $i: ${e.message}", e)
                break
            }
        }
        
        val response = outputBuilder.toString()
        return response.ifEmpty { "[No output generated]" }
    }

    private fun tokenize(text: String): LongArray {
        // Simple byte-level tokenization
        return text.toByteArray(Charsets.UTF_8).map { (it.toInt() and 0xFF).toLong() }.toLongArray()
    }

    private fun sampleNextToken(logits: FloatArray, seqLen: Int): Long {
        val vocabSize = logits.size / seqLen
        val startIdx = (seqLen - 1) * vocabSize
        
        return if (startIdx >= 0 && startIdx < logits.size) {
            // Find argmax in the last position's logits
            var maxIdx = 0
            var maxVal = Float.NEGATIVE_INFINITY
            for (i in 0 until vocabSize) {
                val idx = startIdx + i
                if (idx < logits.size && logits[idx] > maxVal) {
                    maxVal = logits[idx]
                    maxIdx = i
                }
            }
            maxIdx.toLong()
        } else {
            // Fallback: use last vocabSize elements
            val lastLogits = logits.takeLast(minOf(vocabSize, logits.size))
            lastLogits.indices.maxByOrNull { lastLogits[it] }?.toLong() ?: 0L
        }
    }

    private fun decodeToken(token: Long): String {
        return when {
            token in 32L until 127L -> token.toInt().toChar().toString()
            token == 10L -> "\n"
            else -> ""
        }
    }

    override fun isReady(): Boolean = isInitialized && module != null

    override fun shutdown() {
        synchronized(initLock) {
            Log.d(TAG, "Shutting down ExecuTorch engine")
            module?.let {
                try {
                    it.destroy()
                    Log.d(TAG, "Module destroyed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error destroying module", e)
                }
                module = null
            }
            modelPath = null
            isInitialized = false
            Log.d(TAG, "ExecuTorch engine shutdown complete")
        }
    }

    override fun getEngineType(): EngineType = EngineType.EXECUTORCH
    
    fun getEngineName(): String = getEngineType().displayName
}
