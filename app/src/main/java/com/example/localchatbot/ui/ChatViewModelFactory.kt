package com.example.localchatbot.ui

import android.content.Context
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.example.localchatbot.ModelRunner
import com.example.localchatbot.data.ChatHistoryRepository
import com.example.localchatbot.util.ResourceMonitor

class ChatViewModelFactory(
    private val modelRunner: ModelRunner,
    private val context: Context
) : AbstractSavedStateViewModelFactory() {

    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                modelRunner = modelRunner,
                resourceMonitor = ResourceMonitor(context),
                chatHistoryRepository = ChatHistoryRepository(context),
                savedStateHandle = handle
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
