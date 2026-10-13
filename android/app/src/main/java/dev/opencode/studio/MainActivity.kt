package dev.opencode.studio

import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import id.or.oo.pr.engine.ProotLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private val Ink = Color(0xFF111214)
private val Surface = Color(0xFF191A1E)
private val Surface2 = Color(0xFF202126)
private val Line = Color(0xFF303137)
private val Muted = Color(0xFF92949B)
private val Lime = Color(0xFFB8F477)
private val Lilac = Color(0xFFBBA9FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(17, 18, 20)
        window.navigationBarColor = android.graphics.Color.rgb(17, 18, 20)
        setContent { StudioApp() }
    }
}

private enum class Page(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Chat("Chat", Icons.Default.ChatBubbleOutline), Terminal("Terminal", Icons.Default.Code),
    Files("Files", Icons.Default.FolderOpen), Browser("Browser", Icons.Default.Language), Mcp("MCPs", Icons.Default.Extension)
}

@Composable
private fun StudioApp() {
    val app = LocalContext.current.applicationContext as StudioApplication
    val scope = rememberCoroutineScope()
    val api = remember { OpenCodeApi() }
    var page by remember { mutableStateOf(Page.Chat) }
    var draft by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (app.hasOpenCode()) "Starting Debian · OpenCode…" else "Debian Trixie is not installed") }
    var runtimeBusy by remember { mutableStateOf(false) }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var sessionEpoch by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf("") }
    val transcript = remember { mutableStateListOf<ChatEntry>() }
    val serverLog = remember { mutableStateListOf<String>() }
    fun notify(text: String) { toast = text }

    LaunchedEffect(app.hasOpenCode(), sessionEpoch) {
        if (app.hasOpenCode()) {
            app.startOpenCodeServer({ line -> if (line.isNotBlank()) serverLog.add(line) }, { })
            repeat(90) {
                if (api.health()) {
                    status = "OpenCode ready · Debian Trixie"
                    if (sessionId == null) sessionId = api.newSession()?.also { id ->
                        transcript.clear(); transcript.addAll(api.messages(id))
                    }
                    return@LaunchedEffect
                }
                delay(1000)
            }
            status = "OpenCode server is not responding"
        }
    }

    fun installAndConfigure() {
        if (runtimeBusy) return
        runtimeBusy = true
        if (!app.hasDebian()) {
            status = "Downloading Debian Trixie root filesystem…"
            serverLog.clear()
            app.installDebian({ line -> serverLog.add(line) }) { installed ->
                if (installed) {
                    status = "Debian installed · installing OpenCode and MCPs…"
                    app.configureOpenCode({ line -> serverLog.add(line) }) { ready ->
                        runtimeBusy = false
                        status = if (ready) "OpenCode ready · Debian Trixie" else "Debian is installed; OpenCode setup needs attention"
                        if (ready) { sessionEpoch++; notify("OpenCode and starter MCP tools are ready") }
                    }
                } else {
                    runtimeBusy = false
                    status = "Debian install failed — check runtime log"
                }
            }
        } else {
            status = "Installing OpenCode and five MCP servers…"
            app.configureOpenCode({ line -> serverLog.add(line) }) { ready ->
                runtimeBusy = false
                status = if (ready) "OpenCode ready · Debian Trixie" else "OpenCode setup failed — check runtime log"
                if (ready) { sessionEpoch++; notify("OpenCode tools are ready") }
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(background = Ink, surface = Surface, primary = Lime)) {
        Scaffold(containerColor = Ink, bottomBar = {
            NavigationBar(containerColor = Color(0xFF151619), contentColor = Muted, tonalElevation = 0.dp) {
                Page.entries.forEach { p ->
                    NavigationBarItem(selected = page == p, onClick = { page = p }, icon = { Icon(p.icon, null, Modifier.size(20.dp)) },
                        label = { Text(p.label, fontSize = 10.sp) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Lime, selectedTextColor = Lime, indicatorColor = Surface2, unselectedIconColor = Muted, unselectedTextColor = Muted))
                }
            }
        }) { inset ->
            Column(Modifier.fillMaxSize().padding(inset).background(Ink)) {
                TopBar(page, status)
                when (page) {
                    Page.Chat -> ChatPage(status, runtimeBusy, draft, transcript.toList(), sending, serverLog.toList(), ::installAndConfigure,
                        onSend = { text ->
                            val id = sessionId
                            if (id == null) notify("Start Debian and OpenCode first")
                            else if (text.isNotBlank() && !sending) {
                                transcript.add(ChatEntry("user", text)); draft = ""; sending = true
                                scope.launch {
                                    try { transcript.clear(); transcript.addAll(api.send(id, text)) }
                                    catch (e: Exception) { notify("OpenCode request failed: ${e.message}") }
                                    finally { sending = false }
                                }
                            }
                        }) { draft = it }
                    Page.Terminal -> TerminalPage(app)
                    Page.Files -> FilesPage(app, ::notify)
                    Page.Browser -> BrowserPage()
                    Page.Mcp -> McpPage(app, api)
                }
            }
        }
        if (toast.isNotBlank()) {
            LaunchedEffect(toast) { delay(2600); toast = "" }
            Surface(Modifier.padding(bottom = 86.dp).wrapContentSize().clickable { toast = "" }, color = Surface2, shape = RoundedCornerShape(8.dp), shadowElevation = 10.dp) {
                Text(toast, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun TopBar(page: Page, status: String) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF151619)).padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).background(Lime, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Code, null, tint = Ink, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("OpenCode Studio", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text("DEBIAN TRIXIE  ·  ${page.label.uppercase()}", fontSize = 9.sp, color = Muted, letterSpacing = 1.sp)
        }
        Icon(Icons.Default.Circle, null, tint = if (status.contains("ready", true)) Lime else Color(0xFFE8B95B), modifier = Modifier.size(7.dp))
        Spacer(Modifier.width(6.dp)); Text(if (status.contains("ready", true)) "Ready" else "Setup", fontSize = 10.sp, color = Muted)
    }
    HorizontalDivider(color = Line.copy(alpha = .65f), thickness = 1.dp)
}

@Composable
private fun ChatPage(status: String, busy: Boolean, draft: String, transcript: List<ChatEntry>, sending: Boolean, logs: List<String>, onInstall: () -> Unit, onSend: (String) -> Unit, onDraft: (String) -> Unit) {
    var showLog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).background(Lime, RoundedCornerShape(99.dp))); Spacer(Modifier.width(7.dp)); Text("LOCAL CODING SESSION", fontSize = 9.sp, color = Muted, letterSpacing = 1.sp) }
        Spacer(Modifier.height(13.dp)); Text("Code, right here.", fontSize = 30.sp, fontWeight = FontWeight.Medium, color = Color.White, letterSpacing = (-1).sp)
        Spacer(Modifier.height(5.dp)); Text("Native OpenCode client · Debian Trixie runtime", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Terminal, null, tint = Lilac, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) { Text(status, fontSize = 10.sp, color = Color.White); Text("App-private Debian rootfs · ARM64", fontSize = 9.sp, color = Muted) }
            TextButton(enabled = !busy, onClick = onInstall) { Text(if (busy) "WORKING" else if (status.contains("ready", true)) "REPAIR" else "INSTALL", color = Lime, fontSize = 9.sp) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lime, trackColor = Surface2)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clickable { showLog = !showLog }, verticalAlignment = Alignment.CenterVertically) {
            Icon(if (showLog) Icons.Default.ExpandMore else Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(15.dp))
            Text(if (showLog) "Hide runtime log" else "Runtime log", color = Muted, fontSize = 10.sp)
            Spacer(Modifier.weight(1f)); Text("${logs.size} lines", color = Muted, fontSize = 9.sp)
        }
        if (showLog) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 115.dp).background(Color(0xFF101113), RoundedCornerShape(6.dp)).padding(8.dp)) {
            items(logs.takeLast(50)) { Text(it, color = Color(0xFFBFC1C6), fontSize = 9.sp, fontFamily = FontFamily.Monospace, lineHeight = 14.sp) }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            if (transcript.isEmpty()) item { AgentMessage("OpenCode", "Install the Debian Trixie runtime above, then connect an AI provider in OpenCode to start coding. Your prompts are sent to the local OpenCode server.") }
            items(transcript) { entry -> AgentMessage(if (entry.role == "user") "You" else "OpenCode", entry.text, entry.role == "user") }
            if (sending) item { Text("OpenCode is thinking…", color = Muted, fontSize = 11.sp) }
        }
        Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(10.dp)).padding(10.dp)) {
            BasicTextField(value = draft, onValueChange = onDraft, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(4.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp), decorationBox = { inner -> if (draft.isEmpty()) Text("Ask OpenCode anything…", color = Muted, fontSize = 13.sp); inner() })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {}) { Icon(Icons.Default.AttachFile, null, tint = Muted, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("Attach", color = Muted, fontSize = 10.sp) }
                Spacer(Modifier.weight(1f)); Button(enabled = !sending, onClick = { onSend(draft) }, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text("Send", fontSize = 11.sp); Spacer(Modifier.width(5.dp)); Icon(Icons.Default.Send, null, modifier = Modifier.size(13.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp)); Text("LOCAL SESSION  ·  PROVIDER CREDENTIALS STAY IN OPENCode", fontSize = 8.sp, color = Muted, letterSpacing = .4.sp, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.height(7.dp))
    }
}

@Composable
private fun AgentMessage(author: String, body: String, user: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 340.dp).background(if (user) Color(0xFF25272A) else Surface, RoundedCornerShape(9.dp)).padding(12.dp)) {
            Text(author, fontSize = 10.sp, color = if (user) Lime else Lilac, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp)); Text(body, fontSize = 12.sp, lineHeight = 19.sp, color = Color(0xFFD0D1D4))
        }
    }
}

@Composable
private fun PageTitle(kicker: String, title: String, subtitle: String) {
    Text(kicker, fontSize = 9.sp, color = Muted, letterSpacing = 1.1.sp)
    Spacer(Modifier.height(7.dp)); Text(title, fontSize = 25.sp, fontWeight = FontWeight.Medium, color = Color.White)
    Spacer(Modifier.height(4.dp)); Text(subtitle, fontSize = 11.sp, color = Muted)
}

@Composable
private fun TerminalPage(app: StudioApplication) {
    val context = LocalContext.current
    val output = remember { mutableStateListOf("OpenCode Studio terminal", "Starting Debian Trixie shell…") }
    var command by remember { mutableStateOf("") }
    var shell by remember { mutableStateOf<ProotLauncher.Session?>(null) }
    LaunchedEffect(Unit) {
        val session = withContext(Dispatchers.IO) { app.openTerminal() }
        shell = session
        if (session == null) output.add("Debian is not installed. Visit Chat → Install.")
        else {
            val data = ByteArray(4096)
            while (true) {
                val size = withContext(Dispatchers.IO) { session.read(data) }
                if (size <= 0) break
                output.add(String(data, 0, size, Charsets.UTF_8))
                if (output.size > 500) output.removeAt(0)
            }
        }
    }
    DisposableEffect(Unit) { onDispose { shell?.close() } }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageTitle("DEBIAN 13 · LIVE PTY", "Terminal", "Interactive bash running inside your Debian workspace")
        Spacer(Modifier.height(14.dp))
        Column(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF101113), RoundedCornerShape(9.dp)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(6.dp).background(Lime, RoundedCornerShape(99.dp))); Spacer(Modifier.width(7.dp)); Text("bash · ${if (shell == null) "disconnected" else "connected"}", color = Color.White, fontSize = 10.sp); Spacer(Modifier.weight(1f)); Text("UTF-8", fontSize = 9.sp, color = Muted) }
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f), reverseLayout = true) { items(output.asReversed()) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 15.sp, color = Color(0xFFD1D2D5)) } }
            Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("❯", color = Lime, fontFamily = FontFamily.Monospace); Spacer(Modifier.width(8.dp))
                BasicTextField(command, onValueChange = { command = it }, modifier = Modifier.weight(1f).padding(vertical = 8.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp), decorationBox = { inner -> if (command.isEmpty()) Text("Enter a command…", color = Muted, fontSize = 11.sp); inner() })
                IconButton(enabled = shell != null, onClick = { shell?.write((command + "\n").toByteArray()); command = "" }) { Icon(Icons.Default.Send, null, tint = Lime) }
            }
        }
    }
}

@Composable
private fun FilesPage(app: StudioApplication, notify: (String) -> Unit) {
    val context = LocalContext.current
    val root = remember { app.projectDirectory() }
    var refresh by remember { mutableIntStateOf(0) }
    var editFile by remember { mutableStateOf<File?>(null) }
    var editText by remember { mutableStateOf("") }
    var createNew by remember { mutableStateOf(false) }
    val entries = remember(refresh) { root.listFiles()?.sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }).orEmpty().toList() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageTitle("PROJECT WORKSPACE", "Files", "Read and edit files in Debian · ${entries.size} items")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, null, tint = Lilac, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text("/home/opencode/project", fontSize = 10.sp, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = { createNew = true }) { Icon(Icons.Default.NoteAdd, null, tint = Muted) }
            IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, null, tint = Muted) }
        }
        HorizontalDivider(color = Line)
        LazyColumn(Modifier.weight(1f)) {
            if (entries.isEmpty()) item { Text("Your project folder is empty. Open Terminal or ask OpenCode to create files.", Modifier.padding(14.dp), color = Muted, fontSize = 11.sp, lineHeight = 17.sp) }
            items(entries, key = { it.absolutePath }) { file ->
                Row(Modifier.fillMaxWidth().clickable {
                    if (file.isFile) { editFile = file; editText = runCatching { file.readText().take(200_000) }.getOrElse { "Unable to read file: ${it.message}" } }
                }.padding(vertical = 12.dp, horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description, null, tint = if (file.isDirectory) Lilac else Muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp)); Text(file.name + if (file.isDirectory) "/" else "", Modifier.weight(1f), color = Color.White, fontSize = 12.sp)
                    Text(if (file.isDirectory) "Folder" else formatBytes(file.length()), color = Muted, fontSize = 9.sp)
                }
                HorizontalDivider(color = Line.copy(alpha = .5f))
            }
        }
        Button(onClick = {
            runCatching {
                val archive = app.createProjectZip()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", archive)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share project ZIP"))
            }.onFailure { notify("ZIP export failed: ${it.message}") }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Icon(Icons.Default.Archive, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("Create & share ZIP") }
    }
    if (editFile != null || createNew) {
        val file = editFile
        var name by remember(editFile, createNew) { mutableStateOf(file?.name ?: "new-file.txt") }
        AlertDialog(onDismissRequest = { editFile = null; createNew = false }, containerColor = Surface,
            title = { Text(if (createNew) "Create file" else "Edit ${file?.name}", color = Color.White) },
            text = { Column {
                if (createNew) BasicTextField(name, { name = it }, Modifier.fillMaxWidth().background(Ink, RoundedCornerShape(5.dp)).padding(10.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp))
                BasicTextField(editText, { editText = it }, Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 380.dp).background(Ink, RoundedCornerShape(5.dp)).padding(10.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp))
            } }, confirmButton = { TextButton(onClick = {
                runCatching { (file ?: File(root, name)).writeText(editText); refresh++ }.onFailure { notify("Save failed: ${it.message}") }
                editFile = null; createNew = false
            }) { Text("Save", color = Lime) } }, dismissButton = { TextButton(onClick = { editFile = null; createNew = false }) { Text("Cancel", color = Muted) } })
    }
}

private fun formatBytes(n: Long): String = when { n < 1024 -> "$n B"; n < 1024 * 1024 -> "${n / 1024} KB"; else -> "${n / (1024 * 1024)} MB" }

@Composable
private fun BrowserPage() {
    var address by remember { mutableStateOf("https://opencode.ai") }
    var browser by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        PageTitle("NATIVE BROWSER", "Browser", "Independent browsing panel · not the OpenCode client")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(8.dp)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (browser?.canGoBack() == true) browser?.goBack() }) { Icon(Icons.Default.ArrowBack, null, tint = Muted) }
            IconButton(onClick = { if (browser?.canGoForward() == true) browser?.goForward() }) { Icon(Icons.Default.ArrowForward, null, tint = Muted) }
            IconButton(onClick = { browser?.reload() }) { Icon(Icons.Default.Refresh, null, tint = Muted) }
            BasicTextField(address, { address = it }, Modifier.weight(1f).background(Ink, RoundedCornerShape(5.dp)).padding(9.dp), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 10.sp))
            IconButton(onClick = { browser?.loadUrl(normalizeUrl(address)) }) { Icon(Icons.Default.ArrowUpward, null, tint = Lime) }
        }
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, color = Lime, trackColor = Surface2)
        AndroidView(modifier = Modifier.fillMaxSize().padding(top = 8.dp).background(Color.White, RoundedCornerShape(8.dp)), factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() { override fun onPageFinished(view: WebView, url: String?) { address = url ?: address } }
                webChromeClient = object : WebChromeClient() { override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress } }
                browser = this
                loadUrl(address)
            }
        }, update = { browser = it })
    }
}

private fun normalizeUrl(raw: String): String = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"

@Composable
private fun McpPage(app: StudioApplication, api: OpenCodeApi) {
    val tools = listOf(
        "Filesystem" to "Read/write project workspace · preinstalled",
        "Memory" to "Persistent project context · preinstalled",
        "GitHub" to "Repositories, issues and pull requests · token optional",
        "Sequential Thinking" to "Break complex tasks into steps · preinstalled",
        "Playwright" to "Browser automation · preinstalled"
    )
    var health by remember { mutableStateOf(JSONObject()) }
    LaunchedEffect(app.hasOpenCode()) { if (app.hasOpenCode()) health = api.mcpStatus() ?: JSONObject() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageTitle("LOCAL AGENT TOOLS", "MCP servers", "Five starter MCPs are provisioned with OpenCode")
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(tools) { (name, detail) ->
                val key = when (name) { "Sequential Thinking" -> "thinking"; else -> name.lowercase() }
                val state = health.optJSONObject(key)?.optString("status") ?: if (app.hasOpenCode()) "configured" else "install runtime"
                Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).background(Surface2, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Extension, null, tint = Lilac, modifier = Modifier.size(17.dp)) }
                    Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(name, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium); Spacer(Modifier.height(3.dp)); Text(detail, fontSize = 9.sp, color = Muted, lineHeight = 13.sp) }
                    Column(horizontalAlignment = Alignment.End) { Text(state.uppercase(), fontSize = 8.sp, color = if (state == "connected") Lime else Color(0xFFE8B95B)); if (name == "GitHub") Text("Add token", fontSize = 8.sp, color = Muted) }
                }
            }
            item { Text("Configured in the app-private OpenCode config. GitHub needs a personal access token to access private repos or perform authenticated actions.", fontSize = 10.sp, lineHeight = 16.sp, color = Muted, modifier = Modifier.padding(top = 5.dp)) }
        }
    }
}
