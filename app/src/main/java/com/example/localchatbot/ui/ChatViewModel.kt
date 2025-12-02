package com.example.localchatbot.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.localchatbot.ModelRunner
import com.example.localchatbot.data.ChatHistoryRepository
import com.example.localchatbot.data.ChatMessage
import com.example.localchatbot.util.LiveResourceStats
import com.example.localchatbot.util.ResourceMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val modelRunner: ModelRunner,
    private val resourceMonitor: ResourceMonitor,
    private val chatHistoryRepository: ChatHistoryRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_IS_MODEL_READY = "is_model_ready"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_ENGINE_NAME = "engine_name"
        private const val KEY_SHOW_STATS = "show_stats"
        private const val KEY_ERROR = "error"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val liveStats: StateFlow<LiveResourceStats> = resourceMonitor.liveStats

    init {
        restoreStateFromSavedState()
        initializeModel()
        restoreChatHistory()
    }

    private fun restoreStateFromSavedState() {
        val isModelReady = savedStateHandle.get<Boolean>(KEY_IS_MODEL_READY) ?: false
        val modelName = savedStateHandle.get<String>(KEY_MODEL_NAME)
        val engineName = savedStateHandle.get<String>(KEY_ENGINE_NAME)
        val showStats = savedStateHandle.get<Boolean>(KEY_SHOW_STATS) ?: true
        val error = savedStateHandle.get<String>(KEY_ERROR)

        _uiState.update {
            it.copy(
                isModelReady = isModelReady,
                modelName = modelName,
                engineName = engineName,
                showStats = showStats,
                error = error
            )
        }
    }

    private fun saveStateToSavedState() {
        val state = _uiState.value
        savedStateHandle[KEY_IS_MODEL_READY] = state.isModelReady
        savedStateHandle[KEY_MODEL_NAME] = state.modelName
        savedStateHandle[KEY_ENGINE_NAME] = state.engineName
        savedStateHandle[KEY_SHOW_STATS] = state.showStats
        savedStateHandle[KEY_ERROR] = state.error
    }

    private fun initializeModel() {
        _uiState.update {
            it.copy(isModelReady = false, modelName = null, engineName = null, error = null)
        }
    }

    private fun restoreChatHistory() {
        viewModelScope.launch {
            try {
                val messages = chatHistoryRepository.retrieveAllMessages()
                _uiState.update { it.copy(messages = messages) }
            } catch (e: Exception) {
                // Ignore errors loading history
            }
        }
    }

    fun toggleStats() {
        _uiState.update { it.copy(showStats = !it.showStats) }
        saveStateToSavedState()
    }

    fun loadModelFromUri(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModel = true, error = null) }
            
            modelRunner.loadFromUri(uri, fileName)
                .onSuccess { modelInfo ->
                    _uiState.update {
                        it.copy(
                            isLoadingModel = false,
                            isModelReady = true,
                            modelName = modelInfo.name,
                            engineName = modelInfo.engineName
                        )
                    }
                    saveStateToSavedState()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingModel = false,
                            error = error.message ?: "Failed to load model"
                        )
                    }
                    saveStateToSavedState()
                }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            val userMessage = ChatMessage(content = text.trim(), isFromUser = true)
            val loadingMessage = ChatMessage(content = "", isFromUser = false, isLoading = true)

            _uiState.update {
                it.copy(
                    messages = it.messages + userMessage + loadingMessage,
                    isLoading = true
                )
            }

            chatHistoryRepository.storeMessage(userMessage)

            val response = StringBuilder()
            
            modelRunner.generateResponseStreaming(text) { token ->
                response.append(token)
                _uiState.update { state ->
                    val messages = state.messages.toMutableList()
                    val lastIndex = messages.lastIndex
                    if (lastIndex >= 0 && !messages[lastIndex].isFromUser) {
                        messages[lastIndex] = messages[lastIndex].copy(
                            content = response.toString(),
                            isLoading = true
                        )
                    }
                    state.copy(messages = messages)
                }
                true // Continue streaming
            }.onSuccess { finalResponse ->
                val assistantMessage = ChatMessage(content = finalResponse, isFromUser = false)
                _uiState.update { state ->
                    val messages = state.messages.dropLast(1) + assistantMessage
                    state.copy(messages = messages, isLoading = false)
                }
                chatHistoryRepository.storeMessage(assistantMessage)
            }.onFailure { error ->
                val errorMessage = ChatMessage(
                    content = "Error: ${error.message}",
                    isFromUser = false
                )
                _uiState.update { state ->
                    val messages = state.messages.dropLast(1) + errorMessage
                    state.copy(messages = messages, isLoading = false, error = error.message)
                }
            }
        }
    }

    fun stopGeneration() {
        modelRunner.stopGeneration()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
        saveStateToSavedState()
    }

    fun clearChat() {
        viewModelScope.launch {
            chatHistoryRepository.clearAllMessages()
            _uiState.update { it.copy(messages = emptyList()) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        modelRunner.release()
    }
}
