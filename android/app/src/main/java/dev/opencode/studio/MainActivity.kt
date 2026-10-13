package dev.opencode.studio

import android.os.Bundle
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    Files("Files", Icons.Default.FolderOpen), Browser("Browser", Icons.Default.Language),
    Mcp("MCPs", Icons.Default.Extension)
}

@Composable
private fun StudioApp() {
    var page by remember { mutableStateOf(Page.Chat) }
    var draft by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }
    var banner by remember { mutableStateOf("Debian Trixie runtime is not installed") }
    var url by remember { mutableStateOf("https://opencode.ai") }
    var sent by remember { mutableStateOf(false) }

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
                TopBar(page, banner)
                when (page) {
                    Page.Chat -> ChatPage(draft, { draft = it }, sent, { sent = true; draft = "" }, banner, { banner = it })
                    Page.Terminal -> TerminalPage(command, { command = it })
                    Page.Files -> FilesPage { banner = it }
                    Page.Browser -> BrowserPage(url, { url = it })
                    Page.Mcp -> McpPage()
                }
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
        Spacer(Modifier.width(6.dp))
        Text(if (status.contains("ready", true)) "Ready" else "Setup", fontSize = 10.sp, color = Muted)
    }
    HorizontalDivider(color = Line.copy(alpha = .65f), thickness = 1.dp)
}

@Composable
private fun ChatPage(draft: String, onDraft: (String) -> Unit, sent: Boolean, onSend: () -> Unit, status: String, onStatus: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(Lime, RoundedCornerShape(99.dp)))
            Spacer(Modifier.width(7.dp)); Text("LOCAL CODING SESSION", fontSize = 9.sp, color = Muted, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text("Code, right here.", fontSize = 30.sp, fontWeight = FontWeight.Medium, color = Color.White, letterSpacing = (-1).sp)
        Spacer(Modifier.height(6.dp))
        Text("A native OpenCode workspace backed by Debian Trixie.", fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(10.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Code, null, tint = Lilac, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(status, fontSize = 11.sp, color = Color.White); Text("Private app storage · first-run setup", fontSize = 9.sp, color = Muted) }
            TextButton(onClick = { onStatus("Runtime setup queued — connect Debian rootfs to continue") }) { Text("SET UP", color = Lime, fontSize = 9.sp) }
        }
        Spacer(Modifier.height(20.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { AgentMessage("OpenCode", "Welcome. Connect or install the Debian runtime to start a real coding session. Your native chat, terminal, explorer, browser and MCP tools will live together here.") }
            if (sent) item { AgentMessage("You", "Let’s work on the project.", user = true) }
        }
        Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(10.dp)).padding(10.dp)) {
            BasicTextField(value = draft, onValueChange = onDraft, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(4.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp), decorationBox = { inner -> if (draft.isEmpty()) Text("Ask OpenCode anything…", color = Muted, fontSize = 13.sp); inner() })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {}) { Icon(Icons.Default.AttachFile, null, tint = Muted, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(3.dp)); Text("Attach", color = Muted, fontSize = 10.sp) }
                Spacer(Modifier.weight(1f))
                Button(onClick = onSend, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text("Send", fontSize = 11.sp); Spacer(Modifier.width(5.dp)); Icon(Icons.Default.Send, null, modifier = Modifier.size(13.dp)) }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text("LOCAL WORKSPACE  ·  TELL THE AGENT BEFORE RUNNING COMMANDS", fontSize = 8.sp, color = Muted, letterSpacing = .5.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun AgentMessage(author: String, body: String, user: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Column(Modifier.widthIn(max = 330.dp).background(if (user) Color(0xFF25272A) else Surface, RoundedCornerShape(9.dp)).padding(12.dp)) {
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
private fun TerminalPage(command: String, onCommand: (String) -> Unit) {
    var output by remember { mutableStateOf(listOf("OpenCode workspace terminal", "Debian GNU/Linux 13 (trixie)", "Runtime not connected — set up Debian from Chat first.")) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageTitle("WORKSPACE TOOL", "Terminal", "Persistent shell inside the Debian workspace")
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxSize().background(Color(0xFF101113), RoundedCornerShape(9.dp)).padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(6.dp).background(Lime, RoundedCornerShape(99.dp))); Spacer(Modifier.width(7.dp)); Text("debian · bash", color = Color.White, fontSize = 10.sp); Spacer(Modifier.weight(1f)); Text("UTF-8", fontSize = 9.sp, color = Muted) }
            Spacer(Modifier.height(17.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) { items(output) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (it.startsWith("❯")) Lime else Color(0xFFBFC1C6)) } }
            Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("❯", color = Lime, fontFamily = FontFamily.Monospace); Spacer(Modifier.width(8.dp))
                BasicTextField(command, onValueChange = onCommand, modifier = Modifier.weight(1f), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 11.sp), decorationBox = { inner -> if (command.isEmpty()) Text("Enter a command…", color = Muted, fontSize = 11.sp); inner() })
                IconButton(onClick = { output = output + "❯ $command" + "Runtime is not connected. Install Debian to execute commands."; onCommand("") }) { Icon(Icons.Default.PlayArrow, null, tint = Lime) }
            }
        }
    }
}

@Composable
private fun FilesPage(onNotice: (String) -> Unit) {
    val entries = listOf("src/" to "Folder", "app/" to "Folder", "MainActivity.kt" to "Kotlin", "debian-trixie.md" to "Markdown", "package.json" to "JSON", "README.md" to "Markdown")
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageTitle("PROJECT FILES", "Explorer", "Browse and edit files in your Debian home")
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Folder, null, tint = Lilac, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text("/home/opencode/project", fontSize = 10.sp, color = Color.White); Spacer(Modifier.weight(1f)); IconButton(onClick = { onNotice("Create file from the native project explorer") }) { Icon(Icons.Default.NoteAdd, null, tint = Muted) } }
        HorizontalDivider(color = Line)
        LazyColumn(Modifier.weight(1f)) { items(entries) { (name, kind) -> Row(Modifier.fillMaxWidth().clickable { onNotice("Open $name") }.padding(vertical = 14.dp, horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (kind == "Folder") Icons.Default.Folder else Icons.Default.Description, null, tint = if (kind == "Folder") Lilac else Muted, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(11.dp)); Text(name, Modifier.weight(1f), color = Color.White, fontSize = 12.sp); Text(kind, color = Muted, fontSize = 10.sp) }; HorizontalDivider(color = Line.copy(alpha = .5f)) } }
        Button(onClick = { onNotice("ZIP export requires the Debian file runtime to be connected") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Icon(Icons.Default.Archive, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text("Create ZIP archive") }
    }
}

@Composable
private fun BrowserPage(url: String, onUrl: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageTitle("BROWSER TOOLS", "Browser", "Native controls for OpenCode browser sessions")
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {}) { Icon(Icons.Default.ArrowBack, null, tint = Muted) }
            IconButton(onClick = {}) { Icon(Icons.Default.Refresh, null, tint = Muted) }
            BasicTextField(url, onUrl, Modifier.weight(1f).padding(vertical = 12.dp), textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp), singleLine = true)
            IconButton(onClick = {}) { Icon(Icons.Default.ArrowForward, null, tint = Lime) }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxSize().background(Surface, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(25.dp)) {
                Icon(Icons.Default.Language, null, tint = Lilac, modifier = Modifier.size(34.dp)); Spacer(Modifier.height(12.dp))
                Text("Browser runtime not connected", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp)); Text("This panel is reserved for a native browser session. It won’t load the OpenCode web UI in a WebView.", fontSize = 11.sp, color = Muted, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun McpPage() {
    val tools = listOf("Filesystem" to "Read and write project files", "GitHub" to "Issues, pull requests and code", "Browser" to "Navigate and inspect pages", "Playwright" to "Run end-to-end browser tests", "Memory" to "Persistent project context")
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        PageTitle("TOOLS & INTEGRATIONS", "MCP servers", "Five starter server definitions for the local agent")
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(tools) { (name, detail) -> Row(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(8.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(Surface2, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Extension, null, tint = Lilac, modifier = Modifier.size(17.dp)) }
                Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(name, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium); Spacer(Modifier.height(3.dp)); Text(detail, fontSize = 10.sp, color = Muted) }
                Text("SETUP", fontSize = 9.sp, color = Lime, letterSpacing = .5.sp)
            } }
            item { Text("MCP servers need configuration and review before use. Credentials stay in private app storage.", fontSize = 10.sp, lineHeight = 16.sp, color = Muted, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}
