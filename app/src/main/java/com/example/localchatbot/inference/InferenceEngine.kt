package com.example.localchatbot.inference

interface InferenceEngine {
    fun getEngineType(): EngineType
    suspend fun inference(prompt: String): String
    suspend fun inferenceWithParams(prompt: String, maxTokens: Int): String = inference(prompt)
    suspend fun initialize(modelPath: String): Boolean
    fun isReady(): Boolean
    fun shutdown()
}

enum class EngineType(val displayName: String) {
    GGUF("GGUF (llama.cpp)"),
    EXECUTORCH("ExecuTorch (PTE)"),
    UNKNOWN("Unknown");

    companion object {
        fun fromFileName(fileName: String): EngineType {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "gguf", "ggml", "bin" -> GGUF
                "pte" -> EXECUTORCH
                else -> UNKNOWN
            }
        }
    }
}

enum class ModelType(val extensions: List<String>, val displayName: String) {
    GGUF(listOf("gguf", "ggml", "bin"), "GGUF (llama.cpp)"),
    TFLITE(listOf("tflite"), "TFLite (MediaPipe)"),
    PTE(listOf("pte"), "PTE (ExecuTorch)"),
    ONNX(listOf("onnx"), "ONNX Runtime"),
    UNKNOWN(emptyList(), "Unknown");

    companion object {
        fun fromFileName(fileName: String): ModelType {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return entries.find { it.extensions.contains(extension) } ?: UNKNOWN
        }
    }
}

data class ModelInfo(
    val name: String,
    val type: ModelType,
    val engineName: String,
    val path: String
)
