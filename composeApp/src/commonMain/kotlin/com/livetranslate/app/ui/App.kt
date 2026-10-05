package com.livetranslate.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetranslate.app.model.LiveTranslateUiState
import com.livetranslate.app.model.SupportedLanguage
import com.livetranslate.app.model.TranslationSegment
import com.livetranslate.app.util.formatTimestamp
import com.livetranslate.app.viewmodel.LiveTranslateViewModel

@Composable
fun App(viewModel: LiveTranslateViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to the bottom on every new translation or live speech update
    LaunchedEffect(uiState.segments.size, uiState.currentSpeechText) {
        val totalCount = uiState.segments.size + if (uiState.currentSpeechText.isNotEmpty()) 1 else 0
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF8F9FA)
        ) {
            Scaffold(
                topBar = {
                    AppTopBar(
                        hasSegments = uiState.segments.isNotEmpty(),
                        onClearHistory = { viewModel.clearHistory() }
                    )
                },
                bottomBar = {
                    BottomControlBar(
                        isListening = uiState.isListening,
                        sourceLanguage = uiState.selectedSourceLanguage,
                        onToggleListening = { viewModel.toggleListening() }
                    )
                },
                containerColor = Color(0xFFF8F9FA)
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Language Selection Row
                    LanguageSelectionRow(
                        selectedSourceLanguage = uiState.selectedSourceLanguage,
                        targetLanguage = uiState.targetLanguage,
                        onSourceLanguageSelected = { viewModel.selectSourceLanguage(it) }
                    )

                    // Status and Error Banner
                    StatusAndErrorBanner(
                        statusMessage = uiState.statusMessage,
                        errorMessage = uiState.errorMessage,
                        isListening = uiState.isListening,
                        isTranslating = uiState.isTranslating
                    )

                    // Optional Manual Text Translation Collapsible Panel
                    ManualTextTranslationPanel(
                        isExpanded = uiState.isManualInputExpanded,
                        inputText = uiState.inputText,
                        sourceLanguage = uiState.selectedSourceLanguage,
                        isTranslating = uiState.isTranslating,
                        onToggleExpanded = { viewModel.toggleManualInputExpanded() },
                        onInputTextChanged = { viewModel.onInputTextChanged(it) },
                        onTranslate = { viewModel.translateManualText() },
                        onSelectPreset = { lang, text ->
                            viewModel.selectSourceLanguage(lang)
                            viewModel.onInputTextChanged(text)
                        }
                    )

                    // Conversation Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Conversation",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )

                        if (uiState.isTranslating) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF2563EB)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Translating...",
                                    fontSize = 11.sp,
                                    color = Color(0xFF2563EB)
                                )
                            }
                        }
                    }

                    // Conversation Stream (Earliest at top -> Latest at the bottom)
                    if (uiState.segments.isNotEmpty() || uiState.currentSpeechText.isNotEmpty()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Completed historical segments (chronological order)
                            items(uiState.segments, key = { it.id }) { segment ->
                                ConversationSegmentCard(segment)
                            }

                            // Live currently recognized speech item placed at the very bottom
                            if (uiState.currentSpeechText.isNotEmpty()) {
                                item(key = "live_partial_speech") {
                                    LivePartialSpeechCard(
                                        partialSpeech = uiState.currentSpeechText,
                                        sourceLanguage = uiState.selectedSourceLanguage
                                    )
                                }
                            }
                        }
                    } else {
                        EmptyConversationPlaceholder(
                            isListening = uiState.isListening,
                            sourceLanguage = uiState.selectedSourceLanguage,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTopBar(
    hasSegments: Boolean,
    onClearHistory: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "LiveTranslate",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFFECFDF5), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFFA7F3D0), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "ML Kit Local",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF059669)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEFF6FF), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFFBFDBFE), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "7d Auto-Purge",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2563EB)
                    )
                }
            }

            if (hasSegments) {
                Text(
                    text = "Clear History",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFEF4444),
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onClearHistory() }
                        .padding(4.dp)
                )
            }
        }
    }
}

@Composable
private fun LanguageSelectionRow(
    selectedSourceLanguage: SupportedLanguage,
    targetLanguage: SupportedLanguage,
    onSourceLanguageSelected: (SupportedLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Source Language Dropdown Card
        OutlinedCard(
            modifier = Modifier
                .weight(1f)
                .clickable { expanded = true },
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.outlinedCardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "Source Language",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedSourceLanguage.flagEmoji} ${selectedSourceLanguage.displayName}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                    Text(text = "▼", fontSize = 10.sp, color = Color(0xFF64748B))
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    SupportedLanguage.SUPPORTED_SOURCE_LANGUAGES.forEach { lang ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${lang.flagEmoji} ${lang.displayName} (${lang.nativeName})",
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                onSourceLanguageSelected(lang)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Target Language Fixed Card
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "Target Language",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "${targetLanguage.flagEmoji} ${targetLanguage.displayName}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
            }
        }
    }
}

@Composable
private fun BottomControlBar(
    isListening: Boolean,
    sourceLanguage: SupportedLanguage,
    onToggleListening: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Button(
                onClick = onToggleListening,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) Color(0xFFDC2626) else Color(0xFF2563EB)
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isListening) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⏹ Stop Listening (${sourceLanguage.displayName})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "🎙 Start Live Voice Translation",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LivePartialSpeechCard(
    partialSpeech: String,
    sourceLanguage: SupportedLanguage
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF9C3)),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFACC15)))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFFCA8A04), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Current speech (${sourceLanguage.displayName}):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF854D0E)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = partialSpeech,
                fontSize = 15.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF713F12)
            )
        }
    }
}

@Composable
private fun ConversationSegmentCard(segment: TranslationSegment) {
    val sourceLanguageModel = segment.sourceLanguageModel
    val sourceLanguageName = sourceLanguageModel?.displayName ?: segment.sourceLanguage.uppercase()
    val sourceLanguageEmoji = sourceLanguageModel?.flagEmoji ?: "🌐"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Source Language Line with Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$sourceLanguageEmoji $sourceLanguageName:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )
                if (segment.timestamp > 0L) {
                    Text(
                        text = formatTimestamp(segment.timestamp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = segment.sourceText,
                fontSize = 15.sp,
                color = Color(0xFF1E293B)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // English Translation Line
            Text(
                text = "🇺🇸 English:",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2563EB)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = segment.translatedText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (segment.translatedText.startsWith("[Translation error")) Color(0xFFDC2626) else Color(0xFF0F172A)
            )
        }
    }
}

@Composable
private fun EmptyConversationPlaceholder(
    isListening: Boolean,
    sourceLanguage: SupportedLanguage,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = if (isListening) "🎙" else "💬",
                fontSize = 36.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isListening) {
                    "Listening in ${sourceLanguage.displayName}..."
                } else {
                    "No conversation yet"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF334155)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isListening) {
                    "Speak a sentence in ${sourceLanguage.displayName}. When you pause, LiveTranslate will translate it to English and place it here."
                } else {
                    "Tap 'Start Live Voice Translation' below to start listening."
                },
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ManualTextTranslationPanel(
    isExpanded: Boolean,
    inputText: String,
    sourceLanguage: SupportedLanguage,
    isTranslating: Boolean,
    onToggleExpanded: () -> Unit,
    onInputTextChanged: (String) -> Unit,
    onTranslate: () -> Unit,
    onSelectPreset: (SupportedLanguage, String) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⌨ Type text manually (Optional)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF475569)
                )
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    // Quick Preset Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        items(SupportedLanguage.SUPPORTED_SOURCE_LANGUAGES) { lang ->
                            val sample = when (lang.code) {
                                "ja" -> "今日はいい天気ですね。"
                                "zh" -> "今天天气真好。"
                                "ko" -> "오늘 날씨가 정말 좋네요."
                                "ru" -> "Сегодня хорошая погода."
                                "hi" -> "आज मौसम बहुत अच्छा है।"
                                "fr" -> "Il fait beau aujourd'hui."
                                "de" -> "Heute ist schönes Wetter."
                                "es" -> "Hoy hace buen tiempo."
                                else -> ""
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFEEF2FF),
                                modifier = Modifier.clickable { onSelectPreset(lang, sample) }
                            ) {
                                Text(
                                    text = "${lang.flagEmoji} ${lang.displayName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF4338CA),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputTextChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("Type sentence in ${sourceLanguage.displayName}...")
                        },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = onTranslate,
                        enabled = !isTranslating && inputText.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Text("Translate Typed Text", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusAndErrorBanner(
    statusMessage: String,
    errorMessage: String?,
    isListening: Boolean,
    isTranslating: Boolean
) {
    val isError = errorMessage != null
    val backgroundColor = when {
        isError -> Color(0xFFFEE2E2)
        isListening -> Color(0xFFDCFCE7)
        isTranslating -> Color(0xFFFEF3C7)
        else -> Color(0xFFF1F5F9)
    }

    val dotColor = when {
        isError -> Color(0xFFEF4444)
        isListening -> Color(0xFF16A34A)
        isTranslating -> Color(0xFFF59E0B)
        else -> Color(0xFF94A3B8)
    }

    val textColor = when {
        isError -> Color(0xFFB91C1C)
        isListening -> Color(0xFF15803D)
        isTranslating -> Color(0xFF92400E)
        else -> Color(0xFF475569)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = errorMessage ?: statusMessage,
            fontSize = 12.sp,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}
