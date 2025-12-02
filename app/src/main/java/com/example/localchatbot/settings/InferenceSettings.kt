package com.example.localchatbot.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.inferenceDataStore: DataStore<Preferences> by preferencesDataStore(name = "inference_settings")

data class InferenceParams(
    val contextSize: Int,
    val maxTokens: Int,
    val temperature: Float,
    val topK: Int,
    val topP: Float
)

enum class ResourceImpact {
    LOW, MEDIUM, HIGH
}

data class ValidationResult(
    val isValid: Boolean,
    val message: String? = null
)

class InferenceSettings(private val context: Context) {
    
    companion object {
        private val CONTEXT_SIZE = intPreferencesKey("context_size")
        private val MAX_TOKENS = intPreferencesKey("max_tokens")
        private val TEMPERATURE = floatPreferencesKey("temperature")
        private val TOP_K = intPreferencesKey("top_k")
        private val TOP_P = floatPreferencesKey("top_p")
        
        const val DEFAULT_CONTEXT_SIZE = 512
        const val DEFAULT_MAX_TOKENS = 256
        const val DEFAULT_TEMPERATURE = 0.7f
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_TOP_P = 0.9f
    }

    val contextSize: Flow<Int> = context.inferenceDataStore.data.map { preferences ->
        preferences[CONTEXT_SIZE] ?: DEFAULT_CONTEXT_SIZE
    }

    val maxTokens: Flow<Int> = context.inferenceDataStore.data.map { preferences ->
        preferences[MAX_TOKENS] ?: DEFAULT_MAX_TOKENS
    }

    val temperature: Flow<Float> = context.inferenceDataStore.data.map { preferences ->
        preferences[TEMPERATURE] ?: DEFAULT_TEMPERATURE
    }

    val topK: Flow<Int> = context.inferenceDataStore.data.map { preferences ->
        preferences[TOP_K] ?: DEFAULT_TOP_K
    }

    val topP: Flow<Float> = context.inferenceDataStore.data.map { preferences ->
        preferences[TOP_P] ?: DEFAULT_TOP_P
    }

    suspend fun setContextSize(size: Int) {
        context.inferenceDataStore.edit { preferences ->
            preferences[CONTEXT_SIZE] = size
        }
    }

    suspend fun setMaxTokens(tokens: Int) {
        context.inferenceDataStore.edit { preferences ->
            preferences[MAX_TOKENS] = tokens
        }
    }

    suspend fun setTemperature(temp: Float) {
        context.inferenceDataStore.edit { preferences ->
            preferences[TEMPERATURE] = temp
        }
    }

    suspend fun setTopK(k: Int) {
        context.inferenceDataStore.edit { preferences ->
            preferences[TOP_K] = k
        }
    }

    suspend fun setTopP(p: Float) {
        context.inferenceDataStore.edit { preferences ->
            preferences[TOP_P] = p
        }
    }

    suspend fun applyPreset(preset: String) {
        when (preset) {
            "fast" -> {
                setContextSize(256)
                setMaxTokens(128)
                setTemperature(0.5f)
            }
            "balanced" -> {
                setContextSize(512)
                setMaxTokens(256)
                setTemperature(0.7f)
            }
            "quality" -> {
                setContextSize(1024)
                setMaxTokens(512)
                setTemperature(0.8f)
            }
        }
    }
}
