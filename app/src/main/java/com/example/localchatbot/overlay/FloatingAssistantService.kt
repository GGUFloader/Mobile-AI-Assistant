package com.example.localchatbot.overlay

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.localchatbot.ChatApplication
import com.example.localchatbot.MainActivity
import com.example.localchatbot.R
import com.example.localchatbot.data.ChatMessage
import com.example.localchatbot.ui.theme.LocalChatbotTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class FloatingAssistantService : Service(), LifecycleOwner, SavedStateRegistryOwner {
    
    companion object {
        private const val CHANNEL_ID = "floating_assistant_channel"
        private const val NOTIFICATION_ID = 1
        private const val CLOSE_ZONE_HEIGHT = 150  // Height of the close zone at top
        const val ACTION_FLOATING_DISABLED = "com.example.localchatbot.FLOATING_DISABLED"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: ComposeView? = null
    private var chatView: ComposeView? = null
    private var closeZoneView: ComposeView? = null
    private var isChatExpanded = mutableStateOf(false)
    private var chatMessages = mutableStateOf(listOf<ChatMessage>())
    private var isLoading = mutableStateOf(false)
    private var isDragging = mutableStateOf(false)
    private var isInCloseZone = mutableStateOf(false)
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingButton()
        
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        floatingView?.let { windowManager?.removeView(it) }
        chatView?.let { windowManager?.removeView(it) }
        closeZoneView?.let { windowManager?.removeView(it) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun disableAndStop() {
        // Save preference so it stays disabled
        getSharedPreferences("floating_button_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("enabled", false)
            .apply()
        
        // Send broadcast to notify MainActivity
        sendBroadcast(Intent(ACTION_FLOATING_DISABLED).setPackage(packageName))
        
        // Stop the service
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Floating Assistant",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows floating assistant button"
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Assistant")
            .setContentText("Tap to open")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }


    private fun showCloseZone() {
        if (closeZoneView != null) return
        
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val closeZoneParams = WindowManager.LayoutParams(
            screenWidth,
            CLOSE_ZONE_HEIGHT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 0
        }

        closeZoneView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingAssistantService)
            setViewTreeSavedStateRegistryOwner(this@FloatingAssistantService)
            
            setContent {
                LocalChatbotTheme {
                    val inCloseZone by isInCloseZone
                    
                    CloseZoneIndicator(isActive = inCloseZone)
                }
            }
        }

        windowManager?.addView(closeZoneView, closeZoneParams)
    }

    private fun hideCloseZone() {
        closeZoneView?.let { 
            windowManager?.removeView(it) 
        }
        closeZoneView = null
        isInCloseZone.value = false
    }

    private fun showFloatingButton() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val buttonParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - 200
            y = screenHeight / 3
        }

        floatingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingAssistantService)
            setViewTreeSavedStateRegistryOwner(this@FloatingAssistantService)
            
            setContent {
                LocalChatbotTheme {
                    val isExpanded by isChatExpanded
                    val inCloseZone by isInCloseZone
                    
                    AnimatedVisibility(
                        visible = !isExpanded,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        FloatingButton(
                            isInCloseZone = inCloseZone,
                            onDragStart = {
                                isDragging.value = true
                                showCloseZone()
                            },
                            onDrag = { dx, dy ->
                                buttonParams.x += dx.roundToInt()
                                buttonParams.y += dy.roundToInt()
                                windowManager?.updateViewLayout(this@apply, buttonParams)
                                
                                // Check if in close zone (top area - more generous detection)
                                val isNearTop = buttonParams.y < CLOSE_ZONE_HEIGHT + 50
                                
                                isInCloseZone.value = isNearTop
                                android.util.Log.d("FloatingButton", "y=${buttonParams.y}, inCloseZone=${isNearTop}")
                            },
                            onDragEnd = {
                                isDragging.value = false
                                
                                // Check if in close zone BEFORE hiding it (which resets the value)
                                val shouldClose = isInCloseZone.value
                                android.util.Log.d("FloatingButton", "DragEnd - shouldClose=$shouldClose")
                                hideCloseZone()
                                
                                // If released in close zone, disable and stop the service
                                if (shouldClose) {
                                    android.util.Log.d("FloatingButton", "Closing service!")
                                    disableAndStop()
                                }
                            },
                            onClick = {
                                showFloatingChat()
                            }
                        )
                    }
                }
            }
        }

        windowManager?.addView(floatingView, buttonParams)
    }

    private fun showFloatingChat() {
        if (chatView != null) {
            isChatExpanded.value = true
            return
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // Floating chat window - not full screen
        val chatWidth = (screenWidth * 0.9).toInt()
        val chatHeight = (screenHeight * 0.6).toInt()

        val chatParams = WindowManager.LayoutParams(
            chatWidth,
            chatHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        chatView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingAssistantService)
            setViewTreeSavedStateRegistryOwner(this@FloatingAssistantService)
            
            setContent {
                LocalChatbotTheme {
                    FloatingChatScreen(
                        messages = chatMessages.value,
                        isLoading = isLoading.value,
                        onSendMessage = { message ->
                            sendMessage(message)
                        },
                        onMinimize = {
                            isChatExpanded.value = false
                            chatView?.let { windowManager?.removeView(it) }
                            chatView = null
                        },
                        onClose = {
                            isChatExpanded.value = false
                            chatView?.let { windowManager?.removeView(it) }
                            chatView = null
                            chatMessages.value = emptyList()
                            // Clear conversation history to prevent hallucination
                            (application as ChatApplication).modelRunner.clearHistory()
                        },
                        onDrag = { dx, dy ->
                            chatParams.x += dx.roundToInt()
                            chatParams.y += dy.roundToInt()
                            windowManager?.updateViewLayout(this@apply, chatParams)
                        }
                    )
                }
            }
        }

        windowManager?.addView(chatView, chatParams)
        isChatExpanded.value = true
    }

    private fun sendMessage(message: String) {
        val app = application as ChatApplication
        val modelRunner = app.modelRunner

        val userMessage = ChatMessage(
            content = message,
            isFromUser = true
        )
        chatMessages.value = chatMessages.value + userMessage

        val loadingMessage = ChatMessage(
            content = "",
            isFromUser = false,
            isLoading = true
        )
        chatMessages.value = chatMessages.value + loadingMessage
        isLoading.value = true

        // Launch on Default dispatcher to avoid blocking Main
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (modelRunner.isReady()) {
                    android.util.Log.d("FloatingAssistant", "Starting streaming generation for: ${message.take(30)}...")
                    
                    val response = StringBuilder()
                    
                    val result = modelRunner.generateResponseStreaming(message) { token ->
                        response.append(token)
                        // Update UI on Main thread with streaming token
                        kotlinx.coroutines.runBlocking(Dispatchers.Main) {
                            val messages = chatMessages.value.toMutableList()
                            val lastIndex = messages.lastIndex
                            if (lastIndex >= 0 && !messages[lastIndex].isFromUser) {
                                messages[lastIndex] = messages[lastIndex].copy(
                                    content = response.toString(),
                                    isLoading = true
                                )
                                chatMessages.value = messages
                            }
                        }
                        true // Continue streaming
                    }
                    
                    // Update UI on Main thread with final response
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        result.onSuccess { finalResponse ->
                            android.util.Log.d("FloatingAssistant", "Got response: ${finalResponse.take(50)}...")
                            val assistantMessage = ChatMessage(
                                content = finalResponse,
                                isFromUser = false
                            )
                            chatMessages.value = chatMessages.value.dropLast(1) + assistantMessage
                            isLoading.value = false
                        }.onFailure { error ->
                            android.util.Log.e("FloatingAssistant", "Generation failed: ${error.message}")
                            val errorMessage = ChatMessage(
                                content = "Error: ${error.message}",
                                isFromUser = false
                            )
                            chatMessages.value = chatMessages.value.dropLast(1) + errorMessage
                            isLoading.value = false
                        }
                    }
                } else {
                    android.util.Log.w("FloatingAssistant", "Model not ready")
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        val errorMessage = ChatMessage(
                            content = "Please load a model first in the main app",
                            isFromUser = false
                        )
                        chatMessages.value = chatMessages.value.dropLast(1) + errorMessage
                        isLoading.value = false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FloatingAssistant", "Exception in sendMessage", e)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val errorMessage = ChatMessage(
                        content = "Error: ${e.message}",
                        isFromUser = false
                    )
                    chatMessages.value = chatMessages.value.dropLast(1) + errorMessage
                    isLoading.value = false
                }
            }
        }
    }
}


@Composable
fun CloseZoneIndicator(isActive: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                if (isActive) 
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                else 
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(if (isActive) 56.dp else 48.dp),
                        shape = CircleShape,
                        color = if (isActive) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = 4.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Drop here to close",
                                modifier = Modifier.size(if (isActive) 28.dp else 24.dp),
                                tint = if (isActive) 
                                    MaterialTheme.colorScheme.onError 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isActive) "Release to close" else "Drag here to close",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) 
                            MaterialTheme.colorScheme.onError 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingButton(
    isInCloseZone: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit = {},
    onClick: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var showLabel by remember { mutableStateOf(true) }
    
    // Auto-hide label after 3 seconds
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        showLabel = false
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label badge that shows on first appearance (hide when dragging or in close zone)
        AnimatedVisibility(
            visible = showLabel && !isDragging && !isInCloseZone,
            enter = fadeIn() + slideInHorizontally(),
            exit = fadeOut() + slideOutHorizontally()
        ) {
            Surface(
                modifier = Modifier.padding(end = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = "AI Chat",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
        
        // Animate size and color when in close zone
        val buttonSize by animateDpAsState(
            targetValue = if (isInCloseZone) 48.dp else 56.dp,
            label = "buttonSize"
        )
        
        Surface(
            modifier = Modifier
                .size(buttonSize)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { 
                            isDragging = true
                            onDragStart()
                        },
                        onDragEnd = { 
                            isDragging = false
                            onDragEnd()
                        },
                        onDragCancel = {
                            isDragging = false
                            onDragEnd()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    )
                },
            shape = CircleShape,
            color = if (isInCloseZone) 
                MaterialTheme.colorScheme.error 
            else 
                MaterialTheme.colorScheme.primary,
            shadowElevation = if (isDragging) 16.dp else 8.dp,
            onClick = { if (!isDragging) onClick() }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isInCloseZone) Icons.Default.Close else Icons.Default.Chat,
                    contentDescription = "Open AI Chat Assistant",
                    tint = if (isInCloseZone) 
                        MaterialTheme.colorScheme.onError 
                    else 
                        MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingChatScreen(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    onSendMessage: (String) -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 16.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Draggable header
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = "Drag",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Minimize button with tooltip-style label
                        Surface(
                            onClick = onMinimize,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Minimize to floating button",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Hide",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        // Close button with tooltip-style label
                        Surface(
                            onClick = onClose,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close and clear chat",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Close",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "AI Assistant Ready",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ask me anything! I can help with questions,\nexplanations, writing, and more.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "💡 Tip: Drag the header to move this window",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                items(messages, key = { it.id }) { message ->
                    FloatingMessageBubble(message = message)
                }
            }

            // Input area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
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
                        placeholder = { Text("Ask something...", fontSize = 14.sp) },
                        enabled = !isLoading,
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        shape = RoundedCornerShape(24.dp)
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = if (inputText.isNotBlank() && !isLoading) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant,
                        onClick = {
                            if (inputText.isNotBlank() && !isLoading) {
                                onSendMessage(inputText.trim())
                                inputText = ""
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (inputText.isNotBlank()) 
                                        MaterialTheme.colorScheme.onPrimary 
                                    else 
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingMessageBubble(message: ChatMessage) {
    val isUser = message.isFromUser
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isUser) 12.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 12.dp
                    )
                )
                .background(
                    if (isUser) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.secondaryContainer
                )
                .padding(10.dp)
        ) {
            if (message.isLoading && message.content.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Thinking...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            } else {
                Text(
                    text = message.content,
                    fontSize = 14.sp,
                    color = if (isUser) 
                        MaterialTheme.colorScheme.onPrimary 
                    else 
                        MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
