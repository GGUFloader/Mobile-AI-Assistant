package com.example.localchatbot.overlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.localchatbot.ChatApplication
import com.example.localchatbot.ui.MessageBubble
import com.example.localchatbot.ui.theme.LocalChatbotTheme
import com.example.localchatbot.data.ChatMessage
import kotlinx.coroutines.launch

data class AssistantMessage(
    val content: String,
    val isFromUser: Boolean,
    val isLoading: Boolean = false
)

class AssistantChatActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as ChatApplication
        val modelRunner = app.modelRunner

        setContent {
            LocalChatbotTheme {
                AssistantChatScreen(
                    onClose = { finish() },
                    onSendMessage = { message, onToken, onComplete ->
                        kotlinx.coroutines.GlobalScope.launch {
                            if (modelRunner.isReady()) {
                                modelRunner.generateResponseStreaming(message) { token ->
                                    onToken(token)
                                    true
                                }.onSuccess { response ->
                                    onComplete(response)
                                }.onFailure { error ->
                                    onComplete("Error: ${error.message}")
                                }
                            } else {
                                onComplete("Please load a model first in the main app")
                            }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantChatScreen(
    onClose: () -> Unit,
    onSendMessage: (String, (String) -> Unit, (String) -> Unit) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var isLoading by remember { mutableStateOf(false) }
    var streamingContent by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size, streamingContent) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Assistant") },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask something...") },
                        enabled = !isLoading,
                        maxLines = 3
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                val userMessage = ChatMessage(
                                    content = inputText.trim(),
                                    isFromUser = true
                                )
                                messages = messages + userMessage
                                val query = inputText
                                inputText = ""
                                isLoading = true
                                
                                val loadingMessage = ChatMessage(
                                    content = "",
                                    isFromUser = false,
                                    isLoading = true
                                )
                                messages = messages + loadingMessage
                                
                                streamingContent = ""
                                onSendMessage(
                                    query,
                                    { token -> // onToken - streaming callback
                                        scope.launch {
                                            streamingContent += token
                                            // Update the loading message with streaming content
                                            val updatedMessages = messages.toMutableList()
                                            val lastIndex = updatedMessages.lastIndex
                                            if (lastIndex >= 0 && !updatedMessages[lastIndex].isFromUser) {
                                                updatedMessages[lastIndex] = updatedMessages[lastIndex].copy(
                                                    content = streamingContent,
                                                    isLoading = true
                                                )
                                                messages = updatedMessages
                                            }
                                        }
                                    },
                                    { finalResponse -> // onComplete
                                        scope.launch {
                                            val assistantMessage = ChatMessage(
                                                content = finalResponse,
                                                isFromUser = false
                                            )
                                            messages = messages.dropLast(1) + assistantMessage
                                            isLoading = false
                                            streamingContent = ""
                                        }
                                    }
                                )
                            }
                        },
                        enabled = inputText.isNotBlank() && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}
