package com.example.localchatbot.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.chatDataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_history")

class ChatHistoryRepository(private val context: Context) {
    
    companion object {
        private val MESSAGES_KEY = stringPreferencesKey("messages")
    }

    suspend fun storeMessage(message: ChatMessage) {
        val messages = retrieveAllMessages().toMutableList()
        messages.add(message)
        storeMessages(messages)
    }

    suspend fun storeMessages(messages: List<ChatMessage>) {
        context.chatDataStore.edit { preferences ->
            val serialized = messages.map { msg ->
                "${msg.id}|${msg.content}|${msg.isFromUser}|${msg.timestamp}|${msg.isLoading}"
            }.joinToString("|||")
            preferences[MESSAGES_KEY] = serialized
        }
    }

    suspend fun retrieveAllMessages(): List<ChatMessage> {
        val preferences = context.chatDataStore.data.first()
        val serialized = preferences[MESSAGES_KEY] ?: return emptyList()
        
        if (serialized.isEmpty()) return emptyList()
        
        return serialized.split("|||").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 5) {
                ChatMessage(
                    id = parts[0],
                    content = parts[1],
                    isFromUser = parts[2].toBoolean(),
                    timestamp = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                    isLoading = parts[4].toBoolean()
                )
            } else null
        }
    }

    suspend fun clearAllMessages() {
        context.chatDataStore.edit { preferences ->
            preferences.remove(MESSAGES_KEY)
        }
    }

    suspend fun hasMessages(): Boolean = retrieveAllMessages().isNotEmpty()

    suspend fun getMessageCount(): Int = retrieveAllMessages().size
}
