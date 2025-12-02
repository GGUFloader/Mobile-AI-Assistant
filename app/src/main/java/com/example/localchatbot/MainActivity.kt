package com.example.localchatbot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.localchatbot.overlay.FloatingAssistantService
import com.example.localchatbot.settings.EngineSettings
import com.example.localchatbot.ui.ChatScreen
import com.example.localchatbot.ui.ChatViewModel
import com.example.localchatbot.ui.ChatViewModelFactory
import com.example.localchatbot.ui.SettingsScreen
import com.example.localchatbot.ui.theme.LocalChatbotTheme

class MainActivity : ComponentActivity() {
    
    private var pendingFloatingButtonEnable = false
    private var floatingButtonState = mutableStateOf(false)
    
    private val floatingDisabledReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FloatingAssistantService.ACTION_FLOATING_DISABLED) {
                floatingButtonState.value = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val app = application as ChatApplication
        val engineSettings = EngineSettings(this)
        val prefs = getSharedPreferences("floating_button_prefs", MODE_PRIVATE)
        
        // Initialize floating button state
        floatingButtonState.value = prefs.getBoolean("enabled", false)
        
        // Register broadcast receiver for floating button disabled event
        val filter = IntentFilter(FloatingAssistantService.ACTION_FLOATING_DISABLED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(floatingDisabledReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(floatingDisabledReceiver, filter)
        }

        setContent {
            LocalChatbotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
                    val isFloatingButtonEnabled by floatingButtonState
                    
                    val scope = rememberCoroutineScope()
                    
                    val tfliteInstalled by engineSettings.tfliteInstalled.collectAsState(initial = false)
                    val executorchInstalled by engineSettings.executorchInstalled.collectAsState(initial = false)
                    val onnxInstalled by engineSettings.onnxInstalled.collectAsState(initial = false)
                    
                    val installedEngines = remember(tfliteInstalled, executorchInstalled, onnxInstalled) {
                        buildSet {
                            if (tfliteInstalled) add("tflite")
                            if (executorchInstalled) add("executorch")
                            if (onnxInstalled) add("onnx")
                        }
                    }

                    when (currentScreen) {
                        is Screen.Chat -> {
                            val viewModel: ChatViewModel = viewModel(
                                factory = ChatViewModelFactory(
                                    app.modelRunner,
                                    applicationContext
                                )
                            )
                            
                            ChatScreen(
                                viewModel = viewModel,
                                onNavigateToSettings = { currentScreen = Screen.Settings },
                                isFloatingButtonEnabled = isFloatingButtonEnabled,
                                onFloatingButtonToggle = { enabled ->
                                    if (enabled) {
                                        if (Settings.canDrawOverlays(this@MainActivity)) {
                                            startFloatingService()
                                            prefs.edit().putBoolean("enabled", true).apply()
                                            floatingButtonState.value = true
                                        } else {
                                            pendingFloatingButtonEnable = true
                                            requestOverlayPermission()
                                        }
                                    } else {
                                        stopFloatingService()
                                        prefs.edit().putBoolean("enabled", false).apply()
                                        floatingButtonState.value = false
                                    }
                                }
                            )
                        }
                        is Screen.Settings -> {
                            SettingsScreen(
                                onBack = { currentScreen = Screen.Chat },
                                installedEngines = installedEngines
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Sync state with preferences in case it was changed externally
        val prefs = getSharedPreferences("floating_button_prefs", MODE_PRIVATE)
        floatingButtonState.value = prefs.getBoolean("enabled", false)
        
        if (pendingFloatingButtonEnable && Settings.canDrawOverlays(this)) {
            pendingFloatingButtonEnable = false
            startFloatingService()
            prefs.edit().putBoolean("enabled", true).apply()
            floatingButtonState.value = true
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(floatingDisabledReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, "Please grant overlay permission to use floating button", Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingAssistantService::class.java)
        startForegroundService(intent)
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingAssistantService::class.java)
        stopService(intent)
    }
}
