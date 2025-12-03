package com.example.localchatbot.overlay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.localchatbot.ChatApplication
import com.example.localchatbot.ui.theme.LocalChatbotTheme
import kotlinx.coroutines.launch

class ProcessTextActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: ""
        val app = application as ChatApplication
        val modelRunner = app.modelRunner

        setContent {
            LocalChatbotTheme {
                ProcessTextScreen(
                    selectedText = selectedText,
                    onClose = { finish() },
                    onProcess = { text, onToken, onComplete ->
                        kotlinx.coroutines.GlobalScope.launch {
                            if (modelRunner.isReady()) {
                                modelRunner.generateResponseStreaming(text) { token ->
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
fun ProcessTextScreen(
    selectedText: String,
    onClose: () -> Unit,
    onProcess: (String, (String) -> Unit, (String) -> Unit) -> Unit
) {
    var response by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedText) {
        if (selectedText.isNotBlank()) {
            isLoading = true
            response = ""
            onProcess(
                selectedText,
                { token -> // onToken - streaming callback
                    scope.launch {
                        response += token
                    }
                },
                { finalResult -> // onComplete
                    scope.launch {
                        response = finalResult
                        isLoading = false
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Response") },
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
                .padding(16.dp)
        ) {
            Text(
                text = "Selected text:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = selectedText,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Response:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    if (isLoading && response.isEmpty()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        Text(
                            text = response.ifEmpty { "Processing..." },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onClose) {
                    Text("Close")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Button(
                    onClick = {
                        if (response.isNotEmpty()) {
                            clipboardManager.setText(AnnotatedString(response))
                        }
                    },
                    enabled = response.isNotEmpty() && !isLoading
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy")
                }
            }
        }
    }
}
