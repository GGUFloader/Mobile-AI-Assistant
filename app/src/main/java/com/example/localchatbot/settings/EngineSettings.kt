package com.example.localchatbot.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.engineDataStore: DataStore<Preferences> by preferencesDataStore(name = "engine_settings")

class EngineSettings(private val context: Context) {
    
    companion object {
        const val DEFAULT_ENGINE = "GGUF"
        private val SELECTED_ENGINE = stringPreferencesKey("selected_engine")
        private val MODEL_PATH = stringPreferencesKey("model_path")
        private val TFLITE_INSTALLED = booleanPreferencesKey("tflite_installed")
        private val EXECUTORCH_INSTALLED = booleanPreferencesKey("executorch_installed")
        private val ONNX_INSTALLED = booleanPreferencesKey("onnx_installed")
    }

    val selectedEngine: Flow<String> = context.engineDataStore.data.map { preferences ->
        preferences[SELECTED_ENGINE] ?: DEFAULT_ENGINE
    }

    val modelPath: Flow<String?> = context.engineDataStore.data.map { preferences ->
        preferences[MODEL_PATH]
    }

    val tfliteInstalled: Flow<Boolean> = context.engineDataStore.data.map { preferences ->
        preferences[TFLITE_INSTALLED] ?: false
    }

    val executorchInstalled: Flow<Boolean> = context.engineDataStore.data.map { preferences ->
        preferences[EXECUTORCH_INSTALLED] ?: false
    }

    val onnxInstalled: Flow<Boolean> = context.engineDataStore.data.map { preferences ->
        preferences[ONNX_INSTALLED] ?: false
    }

    suspend fun setSelectedEngine(engine: String) {
        context.engineDataStore.edit { preferences ->
            preferences[SELECTED_ENGINE] = engine
        }
    }

    suspend fun setModelPath(path: String) {
        context.engineDataStore.edit { preferences ->
            preferences[MODEL_PATH] = path
        }
    }

    suspend fun clearModelPath() {
        context.engineDataStore.edit { preferences ->
            preferences.remove(MODEL_PATH)
        }
    }

    suspend fun setTfliteInstalled(installed: Boolean) {
        context.engineDataStore.edit { preferences ->
            preferences[TFLITE_INSTALLED] = installed
        }
    }

    suspend fun setExecutorchInstalled(installed: Boolean) {
        context.engineDataStore.edit { preferences ->
            preferences[EXECUTORCH_INSTALLED] = installed
        }
    }

    suspend fun setOnnxInstalled(installed: Boolean) {
        context.engineDataStore.edit { preferences ->
            preferences[ONNX_INSTALLED] = installed
        }
    }
}
