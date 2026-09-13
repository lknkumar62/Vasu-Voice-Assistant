package com.vasu.assistant.ui.chat

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vasu.assistant.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var speakingId by remember { mutableStateOf<String?>(null) }
    var isNearBottom by remember { mutableStateOf(true) }

    // Copy helper
    fun copyText(id: String, text: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    // Smart scroll: track if user is near bottom
    LaunchedEffect(listState) {
        // Observe scroll via snapshotFlow implicitly by tracking firstVisibleItemIndex changes
        // Simple heuristic: we update isNearBottom whenever layout changes
    }

    // Auto-scroll on new message only if user was near bottom
    LaunchedEffect(uiState.messages.size, uiState.isLoading, uiState.partialTranscript) {
        if (isNearBottom && uiState.messages.isNotEmpty()) {
            scope.launch {
                // small delay to let layout settle
                kotlinx.coroutines.delay(80)
                if (isNearBottom) {
                    try {
                        listState.animateScrollToItem(maxOf(0, uiState.messages.size - 1))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    // Derived near-bottom detection via layoutInfo
    LaunchedEffect(listState.firstVisibleItemIndex, listState.layoutInfo.totalItemsCount) {
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        val total = info.totalItemsCount
        isNearBottom = if (total == 0) true else (total - lastVisible <= 2)
    }

    // Filter messages by sidebar search
    val filteredMessages = remember(uiState.messages, searchQuery) {
        if (searchQuery.isBlank()) uiState.messages
        else uiState.messages.filter { it.content.contains(searchQuery, ignoreCase = true) }
    }

    val showSuggestions = filteredMessages.size <= 1 && !uiState.isLoading && uiState.partialTranscript.isBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Bot avatar with Online dot
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(VasuCyan.copy(alpha = 0.15f))
                                .border(1.dp, VasuCyan.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SmartToy,
                                contentDescription = "VASU",
                                tint = VasuCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            // Online dot at bottom-end
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .offset(x = 1.dp, y = 1.dp)
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(VasuSuccess)
                                    .border(2.dp, VasuDarkBg, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "VASU",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = VasuTextPrimary
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "● Online",
                                    fontSize = 11.sp,
                                    color = VasuSuccess,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = when {
                                    uiState.isListening -> "Listening..."
                                    uiState.isLoading -> "Thinking..."
                                    speakingId != null -> "Speaking..."
                                    else -> "Always ready to help"
                                },
                                fontSize = 10.sp,
                                color = VasuTextMuted
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VasuTextSecondary
                        )
                    }
                },
                actions = {
                    if (uiState.isListening) {
                        IconButton(onClick = viewModel::toggleListening) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = VasuError)
                        }
                    } else {
                        IconButton(onClick = {
                            if (speakingId != null) {
                                viewModel.stopSpeaking()
                                speakingId = null
                            } else {
                                Toast.makeText(context, "Voice output ${if (speakingId != null) "off" else "ready"}", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(
                                imageVector = if (speakingId != null) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Volume",
                                tint = VasuTextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VasuDarkBg)
            )
        },
        containerColor = VasuDarkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header subtitle — hyper professional 16.dp spacing, typography polished
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Chat",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = VasuTextPrimary,
                    letterSpacing = 0.3.sp
                )
                Text(
                    text = "Talk. Ask. Explore. VASU is always with you.",
                    fontSize = 12.sp,
                    color = VasuTextSecondary,
                    lineHeight = 16.sp
                )
            }

            // Sidebar search — hyper professional 16.dp rounded, proper elevation
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search conversations...", color = VasuTextMuted, fontSize = 12.sp) },
                leadingIcon = {
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(VasuCyan.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Search, contentDescription = null, tint = VasuCyan, modifier = Modifier.size(14.dp)) }
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear", tint = VasuTextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VasuCyan,
                    unfocusedBorderColor = VasuCyan.copy(alpha = 0.15f),
                    focusedTextColor = VasuTextPrimary,
                    unfocusedTextColor = VasuTextPrimary,
                    cursorColor = VasuCyan,
                    focusedContainerColor = VasuDarkCard,
                    unfocusedContainerColor = VasuDarkCard
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
            )

            // Messages — hyper professional spacing 16.dp, padding 16.dp
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                if (showSuggestions) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(VasuCyan.copy(alpha = 0.1f))
                                    .border(1.dp, VasuCyan.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SmartToy,
                                    contentDescription = null,
                                    tint = VasuCyan,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Hello, User 👋",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = VasuTextPrimary
                            )
                            Text(
                                text = "I'm VASU, your AI companion.",
                                fontSize = 12.sp,
                                color = VasuTextMuted
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            SuggestionGrid(onSuggestionClick = { cmd ->
                                viewModel.updateInput(cmd)
                                viewModel.sendMessage()
                                // scroll to bottom
                                scope.launch {
                                    kotlinx.coroutines.delay(100)
                                    try { listState.animateScrollToItem(filteredMessages.size) } catch (_: Exception) {}
                                }
                            })
                        }
                    }
                }

                items(filteredMessages, key = { it.id }) { message ->
                    // System message detection: role system via toolName == "system" or content prefix
                    val isSystem = message.toolName == "system" || message.isToolExecution && message.toolName == null && message.content.startsWith("System:", ignoreCase = true)
                    if (isSystem) {
                        SystemMessageBubble(message)
                    } else {
                        val isSpeaking = speakingId == message.id
                        ChatBubble(
                            message = message,
                            isSpeaking = isSpeaking,
                            onCopy = { copyText(message.id, message.content) },
                            onSpeak = {
                                if (isSpeaking) {
                                    viewModel.stopSpeaking()
                                    speakingId = null
                                } else {
                                    speakingId = message.id
                                    viewModel.replayMessage(message.content)
                                    // Reset speakingId after delay approximate? ViewModel will stop callback not wired,
                                    // so clear after 8 sec fallback
                                    scope.launch {
                                        kotlinx.coroutines.delay(8000)
                                        if (speakingId == message.id) speakingId = null
                                    }
                                }
                            }
                        )
                    }
                }

                // Live voice transcript bubble (partialTranscript)
                if (uiState.partialTranscript.isNotBlank()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = VasuCyan.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, VasuCyan.copy(alpha = 0.3f)),
                                modifier = Modifier.widthIn(max = 300.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(VasuCyan)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Filled.Mic, contentDescription = null, tint = VasuCyan, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "Listening...", fontSize = 11.sp, color = VasuCyan, fontWeight = FontWeight.Medium)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "\"${uiState.partialTranscript}\"",
                                        color = VasuTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Thinking indicator
                if (uiState.isLoading) {
                    item {
                        ThinkingBubble()
                    }
                }
            }

            // Pill row above input (Maya parity)
            PillRow(
                isListening = uiState.isListening,
                onSendCommand = { cmd ->
                    viewModel.updateInput(cmd)
                    viewModel.sendMessage()
                    scope.launch {
                        kotlinx.coroutines.delay(100)
                        try { listState.animateScrollToItem(filteredMessages.size) } catch (_: Exception) {}
                    }
                },
                onVoiceToggle = { viewModel.toggleListening() }
            )

// Input area — hyper professional 16.dp rounded, elevation 4.dp, crisp icons
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .imePadding(),
                colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VasuCyan.copy(alpha = 0.25f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = {
                        Toast.makeText(context, "Attachment coming soon", Toast.LENGTH_SHORT).show()
                    }) {
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(VasuTextMuted.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.AttachFile,
                                contentDescription = "Attach",
                                tint = VasuTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = uiState.inputText,
                        onValueChange = viewModel::updateInput,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask VASU anything...", color = VasuTextMuted, fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VasuCyan,
                            unfocusedBorderColor = VasuCyan.copy(alpha = 0.15f),
                            focusedTextColor = VasuTextPrimary,
                            unfocusedTextColor = VasuTextPrimary,
                            cursorColor = VasuCyan,
                            focusedContainerColor = VasuDarkElevated,
                            unfocusedContainerColor = VasuDarkElevated
                        ),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = false,
                        maxLines = 3,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
                    )

                    IconButton(onClick = { viewModel.toggleListening() }) {
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(if (uiState.isListening) VasuError.copy(alpha = 0.15f) else VasuCyan.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (uiState.isListening) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (uiState.isListening) "Stop" else "Mic",
                                tint = if (uiState.isListening) VasuError else VasuCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.sendMessage() },
                        enabled = uiState.inputText.isNotBlank() && !uiState.isLoading
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(if (uiState.inputText.isNotBlank()) VasuCyan else VasuTextMuted.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (uiState.inputText.isNotBlank()) VasuDarkBg else VasuTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Clear chats / sidebar action footer for small screens
            if (filteredMessages.isNotEmpty()) {
                TextButton(
                    onClick = {
                        // Clear via ViewModel - we add clearConversation in ViewModel; for now clear UI state
                        // Use viewModel.clearChat if available, else just toast
                        try {
                            viewModel.clearChat()
                        } catch (_: Exception) {
                            Toast.makeText(context, "Clear coming soon", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = VasuError.copy(alpha = 0.9f))
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear All Chats", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ThinkingBubble() {
    val infinite = rememberInfiniteTransition(label = "thinking")
    val alpha by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(700, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "pulse"
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VasuCyan.copy(alpha = 0.1f)),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(VasuCyan.copy(alpha = alpha))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VASU is thinking...",
                    color = VasuCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PillRow(
    isListening: Boolean,
    onSendCommand: (String) -> Unit,
    onVoiceToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PillChip(
            label = "Take a photo",
            icon = Icons.Filled.CameraAlt,
            onClick = { onSendCommand("Camera kholo") }
        )
        PillChip(
            label = "Open YouTube",
            icon = Icons.Filled.OndemandVideo,
            onClick = { onSendCommand("YouTube kholo") }
        )
        PillChip(
            label = "Turn on torch",
            icon = Icons.Filled.FlashOn,
            onClick = { onSendCommand("Torch on karo") }
        )
        PillChip(
            label = if (isListening) "Listening..." else "Voice mode",
            icon = Icons.Filled.Mic,
            isActive = isListening,
            onClick = onVoiceToggle
        )
    }
}

@Composable
private fun PillChip(
    label: String,
    icon: ImageVector,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) VasuCyan else VasuDarkCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isActive) VasuCyan else VasuCyan.copy(alpha = 0.15f)),
        shadowElevation = if (isActive) 6.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) VasuDarkBg else VasuCyan,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) VasuDarkBg else VasuTextSecondary
            )
        }
    }
}

@Composable
private fun SuggestionGrid(onSuggestionClick: (String) -> Unit) {
    val suggestions = listOf(
        Triple("Ask a Question", "Explain quantum computing simply", Icons.Filled.Search),
        Triple("Generate Image", "Create a futuristic city image", Icons.Filled.Image),
        Triple("Write Code", "Write a Python sorting function", Icons.Filled.Code),
        Triple("Summarize", "Summarize this document", Icons.Filled.Description),
        Triple("Control Device", "Turn on torch", Icons.Filled.Settings),
        Triple("Give Ideas", "Give me 5 business ideas", Icons.Filled.Lightbulb)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in suggestions.chunked(2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((label, command, icon) in row) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp)
                            .clickable { onSuggestionClick(command) },
                        colors = CardDefaults.cardColors(containerColor = VasuDarkCard),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VasuCyan.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(icon, contentDescription = null, tint = VasuCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = VasuTextPrimary)
                            Text(text = command, fontSize = 9.sp, color = VasuTextMuted, maxLines = 1)
                        }
                    }
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SystemMessageBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(VasuDarkCard.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = VasuTextMuted,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = message.content.removePrefix("System:").trim(),
                color = VasuTextMuted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    isSpeaking: Boolean = false,
    onCopy: () -> Unit = {},
    onSpeak: () -> Unit = {}
) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) VasuCyan.copy(alpha = 0.15f) else VasuDarkCard
    val textColor = if (isUser) VasuCyan else VasuTextPrimary
    val hasTool = message.isToolExecution || message.toolName != null

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bgColor),
            shape = if (isUser) RoundedCornerShape(16.dp).copy(topEnd = RoundedCornerShape(4.dp))
            else RoundedCornerShape(16.dp).copy(topStart = RoundedCornerShape(4.dp)),
            border = if (isUser) androidx.compose.foundation.BorderStroke(1.dp, VasuCyan.copy(alpha = 0.3f))
            else androidx.compose.foundation.BorderStroke(1.dp, VasuCyan.copy(alpha = 0.12f)),
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (hasTool) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Terminal,
                            contentDescription = null,
                            tint = VasuCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tool: ${message.toolName ?: "tool"} · ${if (message.isToolExecution) "executing" else message.toolResult ?: "success"}",
                            fontSize = 10.sp,
                            color = VasuTextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = message.content,
                    color = textColor,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                if (!isUser) {
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy",
                                tint = VasuTextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        IconButton(
                            onClick = onSpeak,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isSpeaking) "Stop" else "Speak",
                                tint = if (isSpeaking) VasuCyan else VasuTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (hasTool && message.toolResult != null) {
                            Text(
                                text = message.toolResult.take(40),
                                fontSize = 9.sp,
                                color = VasuTextMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
