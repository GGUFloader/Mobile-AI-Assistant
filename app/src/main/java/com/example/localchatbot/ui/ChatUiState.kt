package com.example.localchatbot.ui

import com.example.localchatbot.data.ChatMessage

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingModel: Boolean = false,
    val error: String? = null,
    val isModelReady: Boolean = false,
    val modelName: String? = null,
    val engineName: String? = null,
    val showStats: Boolean = true
)
