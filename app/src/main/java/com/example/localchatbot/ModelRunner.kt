package com.example.localchatbot

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.localchatbot.inference.EngineManager
import com.example.localchatbot.inference.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class ModelRunner(context: Context) {
    
    companion object {
        private const val TAG = "ModelRunner"
    }
    
    private val engineManager = EngineManager(context)
    private var currentModelInfo: ModelInfo? = null

    suspend fun loadFromUri(uri: Uri, fileName: String): Result<ModelInfo> = withContext(Dispatchers.IO) {
        val modelType = com.example.localchatbot.inference.ModelType.fromFileName(fileName)
        val result = engineManager.loadModel(uri, fileName)
        
        result.onSuccess { modelInfo ->
            currentModelInfo = modelInfo
        }
        
        result
    }

    suspend fun generateResponse(inputText: String): Result<String> = withContext(Dispatchers.IO) {
        val prompt = formatPrompt(inputText)
        engineManager.generate(prompt)
    }

    suspend fun generateResponseStreaming(
        inputText: String,
        onToken: (String) -> Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = formatPrompt(inputText)
        engineManager.generateStreaming(prompt, onToken)
    }

    fun stopGeneration() {
        engineManager.stopGeneration()
    }

    private fun formatPrompt(userInput: String): String {
        val input = userInput.trim()
        
        // Short, efficient prompt for summarization
        return "Summarize briefly: $input\n\nSummary:"
    }

    fun isReady(): Boolean = engineManager.isModelLoaded()

    fun getModelInfo(): ModelInfo? = currentModelInfo

    fun getEngineName(): String = currentModelInfo?.engineName ?: "None"

    fun release() {
        Log.d(TAG, "Releasing ModelRunner")
        engineManager.release()
        currentModelInfo = null
    }

    suspend fun initialize(): Result<Unit> {
        return Result.success(Unit)
    }
}
