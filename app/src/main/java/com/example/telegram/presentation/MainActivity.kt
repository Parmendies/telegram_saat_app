package com.example.telegram.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.example.telegram.presentation.theme.TelegramTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// BOT TOKEN VE CHAT ID'YI BURAYA GİRİN
private const val BOT_TOKEN = "7728309315:AAHVGbC8zNMGK3Qd5xXb9r5j5w0Qbt-PWus"
private const val TARGET_CHAT_ID = "5943374104"

data class TelegramMessage(
    val id: Long,
    val text: String,
    val from: String,
    val date: String,
    val timestamp: Long
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            TelegramWearApp()
        }
    }
}

@Composable
fun TelegramWearApp() {
    var messages by remember { mutableStateOf<List<TelegramMessage>>(emptyList()) }
    var currentIndex by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var lastUpdateId by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    fun fetchMessages() {
        scope.launch {
            loading = true
            error = ""
            try {
                val result = withContext(Dispatchers.IO) {
                    // offset parametresini kullanarak yeni mesajları al
                    val offset = if (lastUpdateId > 0) lastUpdateId + 1 else 0
                    val url = "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?limit=100&offset=$offset"
                    val response = URL(url).readText()
                    val json = JSONObject(response)

                    if (json.getBoolean("ok")) {
                        val updates = json.getJSONArray("result")
                        val newMessageList = mutableListOf<TelegramMessage>()
                        var maxUpdateId = lastUpdateId

                        for (i in 0 until updates.length()) {
                            val update = updates.getJSONObject(i)
                            val updateId = update.getLong("update_id")
                            if (updateId > maxUpdateId) {
                                maxUpdateId = updateId
                            }

                            if (update.has("message")) {
                                val message = update.getJSONObject("message")
                                val chat = message.getJSONObject("chat")
                                val chatId = chat.getString("id")

                                if (chatId == TARGET_CHAT_ID) {
                                    val from = message.getJSONObject("from")
                                    val timestamp = message.getLong("date")
                                    val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                        .format(Date(timestamp * 1000))

                                    newMessageList.add(
                                        TelegramMessage(
                                            id = updateId,
                                            text = message.optString("text", "[Metin yok]"),
                                            from = from.optString("first_name", "Bilinmeyen"),
                                            date = date,
                                            timestamp = timestamp
                                        )
                                    )
                                }
                            }
                        }

                        // Mevcut mesajlarla yeni mesajları birleştir ve sırala
                        val allMessages = (messages + newMessageList)
                            .distinctBy { it.id }
                            .sortedBy { it.timestamp }
                        allMessages
                    } else {
                        messages
                    }
                }

                val previousSize = messages.size
                messages = result

                // İlk açılışta en yeni mesajı göster
                if (previousSize == 0 && messages.isNotEmpty()) {
                    currentIndex = messages.size - 1
                }
                // Yeni mesaj geldiyse otomatik en yeniye geç
                else if (messages.size > previousSize) {
                    currentIndex = messages.size - 1
                }
                // Index kontrolü
                if (currentIndex >= messages.size) {
                    currentIndex = if (messages.isNotEmpty()) messages.size - 1 else 0
                }
            } catch (e: Exception) {
                error = "Hata: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchMessages()
        while (true) {
            kotlinx.coroutines.delay(5000)
            fetchMessages()
        }
    }

    TelegramTheme {
        MessageViewerScreen(
            messages = messages,
            currentIndex = currentIndex,
            loading = loading,
            error = error,
            onPrevious = { if (currentIndex > 0) currentIndex-- },
            onNext = { if (currentIndex < messages.size - 1) currentIndex++ },
            onRefresh = { fetchMessages() }
        )
    }
}

@Composable
fun MessageViewerScreen(
    messages: List<TelegramMessage>,
    currentIndex: Int,
    loading: Boolean,
    error: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRefresh: () -> Unit
) {
    val scrollState = rememberScrollState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            when {
                loading && messages.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Yükleniyor...", style = MaterialTheme.typography.caption1)
                    }
                }

                error.isNotEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp)
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption1,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Token veya Chat ID kontrol edin",
                            style = MaterialTheme.typography.caption3,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                messages.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Bu chat'ten mesaj yok",
                            style = MaterialTheme.typography.body2,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Chat ID: $TARGET_CHAT_ID",
                            style = MaterialTheme.typography.caption3,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Chip(
                            label = { Text("Yenile") },
                            onClick = onRefresh
                        )
                    }
                }

                else -> {
                    val currentMessage = messages.getOrNull(currentIndex)
                    currentMessage?.let { message ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 12.dp, bottom = 2.dp)
                        ) {
                            // Üst navigasyon - minimal
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Sol - Önceki buton (eski mesajlara)
                                Button(
                                    onClick = onPrevious,
                                    enabled = currentIndex > 0,
                                    modifier = Modifier.size(34.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = MaterialTheme.colors.primary,
                                        disabledBackgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Text("◄", style = MaterialTheme.typography.body2)
                                }

                                // Orta - Bilgiler
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${currentIndex + 1}/${messages.size}",
                                        style = MaterialTheme.typography.caption3,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = message.from,
                                        style = MaterialTheme.typography.caption3,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = message.date.substringAfter(" "),
                                        style = MaterialTheme.typography.caption3,
                                        color = Color.Gray
                                    )
                                }

                                // Sağ - Sonraki buton (yeni mesajlara)
                                Button(
                                    onClick = onNext,
                                    enabled = currentIndex < messages.size - 1,
                                    modifier = Modifier.size(34.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = MaterialTheme.colors.primary,
                                        disabledBackgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Text("►", style = MaterialTheme.typography.body2)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Ana mesaj içeriği - maksimum alan + alt padding
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                ) {
                                    Text(
                                        text = message.text,
                                        style = MaterialTheme.typography.body1,
                                        color = MaterialTheme.colors.onBackground
                                    )
                                    // Ekstra alt boşluk - yuvarlak ekranda alt kısmı tam okuyabilmek için
                                    Spacer(modifier = Modifier.height(60.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

