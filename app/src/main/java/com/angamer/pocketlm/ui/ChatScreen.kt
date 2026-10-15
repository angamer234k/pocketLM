package com.angamer.pocketlm.ui

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.angamer.pocketlm.AppPrefs
import com.angamer.pocketlm.chat.ChatHistory
import com.angamer.pocketlm.chat.ChatLine
import com.angamer.pocketlm.native.EngineRouter
import com.angamer.pocketlm.native.TokenCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    modelName: String,
    loaded: Boolean,
    onOpenModels: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lines by ChatHistory.lines.collectAsState()
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val main = remember { Handler(Looper.getMainLooper()) }

    LaunchedEffect(Unit) { ChatHistory.load(context) }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || busy) return
        if (!loaded) {
            Toast.makeText(context, "Load a model first", Toast.LENGTH_SHORT).show()
            onOpenModels()
            return
        }
        val user = ChatLine(System.nanoTime(), "user", text)
        val replyId = System.nanoTime() + 1
        val history = lines + user
        ChatHistory.set(context, history + ChatLine(replyId, "assistant", ""))
        draft = ""
        busy = true

        val prompt = buildString {
            for (line in history) {
                append(line.role).append(": ").append(line.text).append('\n')
            }
            append("assistant: ")
        }
        val maxTokens = AppPrefs.maxTokens(context)
        val temp = AppPrefs.temperature(context)

        scope.launch(Dispatchers.IO) {
            try {
                EngineRouter.completeStream(prompt, maxTokens, temp, object : TokenCallback {
                    override fun onToken(token: String) {
                        main.post {
                            val updated = ChatHistory.lines.value.map { line ->
                                if (line.id == replyId) line.copy(text = line.text + token) else line
                            }
                            ChatHistory.set(context, updated)
                        }
                    }

                    override fun onDone() {
                        main.post { busy = false }
                    }

                    override fun onError(message: String) {
                        main.post {
                            val updated = ChatHistory.lines.value.map { line ->
                                if (line.id == replyId) {
                                    line.copy(text = line.text.ifBlank { "Error: $message" })
                                } else line
                            }
                            ChatHistory.set(context, updated)
                            busy = false
                        }
                    }
                })
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    busy = false
                    Toast.makeText(context, e.message ?: "chat failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Chat", style = MaterialTheme.typography.headlineSmall)
                if (lines.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        ChatHistory.clear(context)
                    }) { Text("Clear") }
                }
            }
            Text(
                text = if (loaded) {
                    "On-device · $modelName · ${EngineRouter.backendInfo()}"
                } else {
                    "No model loaded"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            if (!loaded) {
                OutlinedButton(onClick = onOpenModels, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Pick a model")
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lines, key = { it.id }) { line ->
                val mine = line.role == "user"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(0.88f)
                    ) {
                        Text(
                            text = line.text.ifBlank { "…" },
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                maxLines = 4
            )
            if (busy) {
                OutlinedButton(onClick = { EngineRouter.abort() }) { Text("Stop") }
            } else {
                Button(onClick = { send() }, enabled = draft.isNotBlank()) { Text("Send") }
            }
        }
    }
}
