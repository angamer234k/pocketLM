package com.angamer.pocketlm

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.angamer.pocketlm.native.LlamaBridge
import com.angamer.pocketlm.server.LlmServerService
import com.angamer.pocketlm.ui.ChatScreen
import com.angamer.pocketlm.ui.ModelsScreen
import com.angamer.pocketlm.ui.SettingsScreen
import com.angamer.pocketlm.ui.theme.PocketLMTheme
import com.angamer.pocketlm.update.UpdateChecker
import com.angamer.pocketlm.update.UpdateMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private enum class Tab(val label: String) { Chat("Chat"), Models("Models"), Settings("Settings") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        File(filesDir, "models").mkdirs()

        setContent {
            PocketLMTheme {
                PocketApp(
                    onStartServer = { port ->
                        val intent = Intent(this, LlmServerService::class.java).apply {
                            action = LlmServerService.ACTION_START
                            putExtra(LlmServerService.EXTRA_PORT, port)
                        }
                        ContextCompat.startForegroundService(this, intent)
                    },
                    onStopServer = {
                        startService(
                            Intent(this, LlmServerService::class.java).apply {
                                action = LlmServerService.ACTION_STOP
                            }
                        )
                    },
                    onLoadModel = { path ->
                        startService(
                            Intent(this, LlmServerService::class.java).apply {
                                action = LlmServerService.ACTION_LOAD_MODEL
                                putExtra(LlmServerService.EXTRA_MODEL_PATH, path)
                                putExtra(LlmServerService.EXTRA_N_CTX, AppPrefs.nCtx(this@MainActivity))
                                putExtra(LlmServerService.EXTRA_N_THREADS, AppPrefs.threads(this@MainActivity))
                            }
                        )
                    },
                    onUnloadModel = {
                        LlamaBridge.unloadModel()
                        LlmServerService.setLoadedModelName("none")
                    }
                )
            }
        }
    }
}

@Composable
private fun PocketApp(
    onStartServer: (Int) -> Unit,
    onStopServer: () -> Unit,
    onLoadModel: (String) -> Unit,
    onUnloadModel: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(Tab.Chat) }
    var loaded by remember { mutableStateOf(false) }
    var modelName by remember { mutableStateOf("none") }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tick) {
        while (true) {
            loaded = LlamaBridge.isLoaded()
            modelName = if (loaded) LlmServerService.loadedModelName else "none"
            delay(800)
        }
    }

    LaunchedEffect(Unit) {
        val mode = AppPrefs.updateMode(context)
        if (mode == UpdateMode.OFF) return@LaunchedEffect
        try {
            val info = withContext(Dispatchers.IO) { UpdateChecker.check() }
            if (info.remoteCode <= BuildConfig.VERSION_CODE) return@LaunchedEffect
            when (mode) {
                UpdateMode.CHECK_ON_START -> {
                    Toast.makeText(
                        context,
                        "Update ${info.remoteName} available. Open Settings.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                UpdateMode.AUTO -> {
                    val url = info.apkUrl
                    if (url.isNullOrBlank()) {
                        Toast.makeText(
                            context,
                            "Update listed but no APK release yet",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "Downloading update…", Toast.LENGTH_SHORT).show()
                        val file = withContext(Dispatchers.IO) {
                            UpdateChecker.download(context, url) {}
                        }
                        UpdateChecker.install(context, file)
                    }
                }
                UpdateMode.OFF -> {}
            }
        } catch (_: Exception) {
            // silent on start
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.Chat,
                    onClick = { tab = Tab.Chat },
                    icon = { Icon(Icons.Outlined.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") }
                )
                NavigationBarItem(
                    selected = tab == Tab.Models,
                    onClick = { tab = Tab.Models },
                    icon = { Icon(Icons.Outlined.Memory, contentDescription = "Models") },
                    label = { Text("Models") }
                )
                NavigationBarItem(
                    selected = tab == Tab.Settings,
                    onClick = { tab = Tab.Settings },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { padding ->
        when (tab) {
            Tab.Chat -> ChatScreen(
                modifier = Modifier.padding(padding),
                modelName = modelName,
                loaded = loaded,
                onOpenModels = { tab = Tab.Models }
            )
            Tab.Models -> ModelsScreen(
                modifier = Modifier.padding(padding),
                loadedName = if (loaded) modelName else "",
                onLoad = onLoadModel,
                onUnload = {
                    onUnloadModel()
                    tick++
                }
            )
            Tab.Settings -> SettingsScreen(
                modifier = Modifier.padding(padding),
                onStartServer = onStartServer,
                onStopServer = onStopServer
            )
        }
    }
}
