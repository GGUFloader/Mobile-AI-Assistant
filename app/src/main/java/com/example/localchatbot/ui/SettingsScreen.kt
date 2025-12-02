package com.example.localchatbot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    installedEngines: Set<String>
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Inference Engines",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                EngineCard(
                    name = "GGUF (llama.cpp)",
                    description = "Best for most LLMs • Supports .gguf, .ggml, .bin models • Optimized for CPU inference",
                    isInstalled = true,
                    isBuiltIn = true
                )
            }

            item {
                EngineCard(
                    name = "ExecuTorch",
                    description = "Meta's mobile runtime • Supports .pte models • Good for on-device ML",
                    isInstalled = installedEngines.contains("executorch"),
                    isBuiltIn = false
                )
            }

            item {
                EngineCard(
                    name = "TFLite",
                    description = "Google's mobile runtime • Supports .tflite models • Wide model compatibility",
                    isInstalled = installedEngines.contains("tflite"),
                    isBuiltIn = false
                )
            }

            item {
                EngineCard(
                    name = "ONNX Runtime",
                    description = "Cross-platform runtime • Supports .onnx models • Good for converted models",
                    isInstalled = installedEngines.contains("onnx"),
                    isBuiltIn = false
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Local Chatbot",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Run AI models locally on your device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Version 1.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EngineCard(
    name: String,
    description: String,
    isInstalled: Boolean,
    isBuiltIn: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall
                    )
                    // Status badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isInstalled || isBuiltIn) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = when {
                                isBuiltIn -> "Built-in"
                                isInstalled -> "Available"
                                else -> "Not installed"
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isInstalled || isBuiltIn) 
                                MaterialTheme.colorScheme.onPrimaryContainer 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isInstalled || isBuiltIn) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Engine is ready to use",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
