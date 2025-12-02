package com.example.localchatbot.inference

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class EngineManager(private val context: Context) {
    
    companion object {
        private const val TAG = "EngineManager"
    }

    private val engineLock = Mutex()
    private var currentEngine: InferenceEngine? = null
    private var currentModelPath: String? = null
    private var currentModelName: String? = null
    private var currentModelType: ModelType? = null
    private var ggufEngine: GGUFEngine? = null
    private var executorchEngine: ExecuTorchEngine? = null

    private fun getEngine(modelType: ModelType): InferenceEngine? {
        return when (modelType) {
            ModelType.GGUF -> ggufEngine ?: GGUFEngine().also { ggufEngine = it }
            ModelType.PTE -> executorchEngine ?: ExecuTorchEngine().also { executorchEngine = it }
            ModelType.TFLITE, ModelType.ONNX, ModelType.UNKNOWN -> null
        }
    }

    private fun getEngineByType(engineType: EngineType): InferenceEngine? {
        return when (engineType) {
            EngineType.GGUF -> ggufEngine ?: GGUFEngine().also { ggufEngine = it }
            EngineType.EXECUTORCH -> executorchEngine ?: ExecuTorchEngine().also { executorchEngine = it }
            EngineType.UNKNOWN -> null
        }
    }

    suspend fun loadModel(uri: Uri, fileName: String): Result<ModelInfo> = engineLock.withLock {
        try {
            Log.d(TAG, "Loading model: $fileName")
            
            val modelType = ModelType.fromFileName(fileName)
            val engine = getEngine(modelType)
            
            if (engine == null) {
                return@withLock Result.failure(Exception("Unsupported model type: ${modelType.displayName}"))
            }

            // Copy model to internal storage
            val modelPath = copyModelToInternal(uri, fileName)
            
            // Shutdown current engine if different
            currentEngine?.let {
                if (it.getEngineType() != engine.getEngineType()) {
                    shutdownEngine(it)
                }
            }

            // Initialize new engine
            val success = engine.initialize(modelPath)
            if (!success) {
                return@withLock Result.failure(Exception("Failed to initialize engine"))
            }

            currentEngine = engine
            currentModelPath = modelPath
            currentModelName = fileName
            currentModelType = modelType

            val modelInfo = ModelInfo(
                name = fileName,
                type = modelType,
                engineName = engine.getEngineType().displayName,
                path = modelPath
            )

            Log.d(TAG, "Model loaded successfully: $fileName")
            Result.success(modelInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            Result.failure(e)
        }
    }

    private suspend fun copyModelToInternal(uri: Uri, fileName: String): String = withContext(Dispatchers.IO) {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        
        val destFile = File(modelsDir, fileName)
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        
        destFile.absolutePath
    }

    suspend fun generate(prompt: String, maxTokens: Int = 256): Result<String> {
        val engine = currentEngine ?: return Result.failure(Exception("No model loaded"))
        
        return try {
            val response = engine.inferenceWithParams(prompt, maxTokens)
            Result.success(cleanResponse(response))
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            Result.failure(e)
        }
    }

    suspend fun generateStreaming(
        prompt: String,
        onToken: (String) -> Boolean,
        maxTokens: Int = 256
    ): Result<String> {
        val engine = currentEngine
        if (engine == null || engine !is GGUFEngine) {
            return Result.failure(Exception("Streaming not supported or no model loaded"))
        }
        
        return try {
            val response = engine.generateStreaming(prompt, maxTokens, onToken)
            Result.success(cleanResponse(response))
        } catch (e: Exception) {
            Log.e(TAG, "Error generating streaming response", e)
            Result.failure(e)
        }
    }

    private fun cleanResponse(response: String): String {
        return response
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun getCurrentModelInfo(): ModelInfo? {
        val name = currentModelName ?: return null
        val type = currentModelType ?: return null
        val path = currentModelPath ?: return null
        val engine = currentEngine ?: return null
        
        return ModelInfo(name, type, engine.getEngineType().displayName, path)
    }

    fun isModelLoaded(): Boolean = currentEngine?.isReady() == true

    fun getSupportedFormats(): List<String> = listOf("gguf", "ggml", "bin", "pte")

    fun isEngineAvailable(modelType: ModelType): Boolean {
        return when (modelType) {
            ModelType.GGUF -> true
            ModelType.PTE -> true
            else -> false
        }
    }

    private fun shutdownEngine(engine: InferenceEngine) {
        try {
            Log.d(TAG, "Shutting down ${engine.getEngineType().displayName} engine")
            engine.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error during engine shutdown", e)
        }
    }

    fun stopGeneration() {
        try {
            LlamaCpp.stopGeneration()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping generation", e)
        }
    }

    fun release() {
        currentEngine?.shutdown()
        currentEngine = null
        currentModelPath = null
        currentModelName = null
        currentModelType = null
    }
}
