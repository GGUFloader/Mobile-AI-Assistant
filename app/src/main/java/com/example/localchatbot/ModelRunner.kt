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
        private const val MAX_HISTORY_TURNS = 1  // Only last exchange for max speed
    }
    
    private val engineManager = EngineManager(context)
    private var currentModelInfo: ModelInfo? = null
    
    // Conversation history for context
    private val conversationHistory = mutableListOf<Pair<String, String>>() // (user, assistant)

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
        val result = engineManager.generate(prompt)
        
        // Store in history on success
        result.onSuccess { response ->
            val cleanResponse = cleanResponse(response)
            addToHistory(inputText, cleanResponse)
        }
        
        result.map { cleanResponse(it) }
    }

    suspend fun generateResponseStreaming(
        inputText: String,
        onToken: (String) -> Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val prompt = formatPrompt(inputText)
        val result = engineManager.generateStreaming(prompt, onToken)
        
        // Store in history on success
        result.onSuccess { response ->
            val cleanResponse = cleanResponse(response)
            addToHistory(inputText, cleanResponse)
        }
        
        result.map { cleanResponse(it) }
    }

    fun stopGeneration() {
        engineManager.stopGeneration()
    }
    
    fun clearHistory() {
        conversationHistory.clear()
    }
    
    private fun addToHistory(userMessage: String, assistantResponse: String) {
        conversationHistory.add(userMessage.trim() to assistantResponse.trim())
        
        // Trim history to prevent context overflow
        while (conversationHistory.size > MAX_HISTORY_TURNS) {
            conversationHistory.removeAt(0)
        }
    }
    
    private fun cleanResponse(response: String): String {
        var cleaned = response.trim()
        
        // Remove any self-continuation patterns (model talking to itself)
        val stopPatterns = listOf(
            "### Instruction:",
            "### Input:",
            "### Response:",
            "### Human:",
            "### Assistant:",
            "### User:",
            "### System:",
            "User:",
            "Human:",
            "Assistant:",
            "<|user|>",
            "<|assistant|>",
            "<|im_start|>",
            "<|im_end|>",
            "<|endoftext|>",
            "</s>",
        )
        
        // Find earliest stop pattern and truncate
        var earliestPos = cleaned.length
        for (pattern in stopPatterns) {
            val pos = cleaned.indexOf(pattern)
            if (pos > 0 && pos < earliestPos) {
                earliestPos = pos
            }
        }
        
        if (earliestPos < cleaned.length) {
            cleaned = cleaned.substring(0, earliestPos)
        }
        
        return cleaned.trim()
    }

    private fun formatPrompt(userInput: String): String {
        val input = userInput.trim()
        
        // Detect if this looks like text to summarize (long input)
        val isSummarizationTask = input.length > 200
        
        val systemPrompt = if (isSummarizationTask) {
            "You are a summarization assistant. Summarize the given text concisely. Extract only the key points. Output a brief summary, not the original text."
        } else {
            "You are a helpful assistant. Answer directly and concisely. Stay focused on what was asked. Do not add unrelated information."
        }
        
        val userPrompt = if (isSummarizationTask && !input.lowercase().contains("summar")) {
            "Summarize this:\n$input"
        } else {
            input
        }
        
        return """<|im_start|>system
$systemPrompt<|im_end|>
<|im_start|>user
$userPrompt<|im_end|>
<|im_start|>assistant
"""
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
