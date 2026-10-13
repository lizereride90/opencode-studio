package dev.opencode.studio

import android.app.Application
import android.system.Os
import android.util.Log
import id.or.oo.pr.engine.ProotHost
import id.or.oo.pr.engine.ProotLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Owns the private Debian prefix and wires the patched Android PRoot engine into the app. */
class StudioApplication : Application(), ProotHost {
    companion object { private const val TAG = "OpenCodeStudio" }
    override val prefixDir get() = File(filesDir, "usr")
    override val homeDir get() = File(filesDir, "home")
    @get:JvmName("getProotPackageName") override val packageName get() = super.getPackageName()
    @get:JvmName("getProotCacheDir") override val cacheDir get() = File(filesDir, "proot-cache").also { it.mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val launcher by lazy { ProotLauncher(this) }
    @Volatile private var serverJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        preparePrefix()
    }

    private fun preparePrefix() {
        listOf("bin", "etc/pr", "tmp", "var/lib/pr/installed-rootfs", "var/lib/pr/containers", "var/lib/pr/dlcache", "share").forEach {
            File(prefixDir, it).mkdirs()
        }
        homeDir.mkdirs()
        // The upstream engine packages patched ARM64 host binaries in jniLibs. They are
        // COPIED (never symlinked) into the private prefix and marked executable: the
        // bundled .so files are mode 644, and a symlink would inherit the non-exec bit.
        // NOTE: this requires extractNativeLibs=true (see manifest + useLegacyPackaging),
        // otherwise the .so files stay inside the APK and never appear on disk.
        val native = File(applicationInfo.nativeLibraryDir)
        mapOf("proot" to "libproot.so", "pr-cli" to "libpr-cli.so", "busybox" to "libbusybox.so").forEach { (name, lib) ->
            val dest = File(prefixDir, "bin/$name")
            val target = File(native, lib)
            try {
                if (!target.exists()) {
                    Log.e(TAG, "Missing native lib ${target.absolutePath} — reinstall the APK (native libs were not extracted)")
                } else if (!dest.isFile || dest.length() != target.length()) {
                    stageExecutable(target, dest)
                } else if (!dest.canExecute() && !dest.setExecutable(true)) {
                    // stale symlink from an older app version, or lost exec bit — restage
                    stageExecutable(target, dest)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot stage $name", e)
            }
        }
        runtimeBinaryError()?.let { Log.e(TAG, it) }
        runCatching {
            assets.open("bin/busybox.applets").bufferedReader().useLines { lines ->
                lines.map(String::trim).filter { it.isNotEmpty() && it != "[" && it != "]" }.forEach { applet ->
                    val link = File(prefixDir, "bin/$applet")
                    if (!link.exists()) Os.symlink("busybox", link.absolutePath)
                }
            }
        }.onFailure { Log.e(TAG, "Could not create BusyBox applet links", it) }
    }

    fun hasDebian(): Boolean = File(prefixDir, "var/lib/pr/containers/debian/rootfs/etc/debian_version").isFile
    fun hasOpenCode(): Boolean = hasDebian() && File(prefixDir, "var/lib/pr/containers/debian/rootfs/root/.local/share/opencode-studio/setup-ok").isFile

    /** Non-null when the staged proot/pr-cli/busybox binaries are not runnable. */
    fun runtimeBinaryError(): String? {
        val bad = listOf("proot", "pr-cli", "busybox")
            .map { File(prefixDir, "bin/$it") }
            .filter { !it.isFile || !it.canExecute() }
            .map { it.name }
        return if (bad.isEmpty()) null
        else "Runtime binaries missing (${bad.joinToString(", ")}). Update to the latest app version or reinstall — the Debian installer cannot run without them."
    }

    private fun stageExecutable(target: File, dest: File) {
        dest.delete()
        target.inputStream().use { ins -> dest.outputStream().use { outs -> ins.copyTo(outs) } }
        if (!dest.setExecutable(true)) Log.e(TAG, "Could not set executable bit on ${dest.absolutePath}")
    }

    fun installDebian(onLine: (String) -> Unit, onDone: (Boolean) -> Unit) =
        runtimeBinaryError()?.let { err ->
            scope.launch(Dispatchers.Main) { onLine(err); onDone(false) }
        } ?: runPty(
            listOf(File(prefixDir, "bin/pr-cli").absolutePath, "install", "debian:trixie", "--override-alias", "debian"), onLine
        ) { _ -> onDone(hasDebian()) }

    fun configureOpenCode(onLine: (String) -> Unit, onDone: (Boolean) -> Unit) {
        val script = """
            set -e
            export DEBIAN_FRONTEND=noninteractive
            apt-get update
            apt-get install -y nodejs npm git curl ca-certificates unzip zip
            npm install --global opencode-ai @modelcontextprotocol/server-filesystem @modelcontextprotocol/server-memory @modelcontextprotocol/server-github @modelcontextprotocol/server-sequential-thinking @playwright/mcp
            mkdir -p /home/opencode/project /root/.config/opencode
            cat > /root/.config/opencode/opencode.json <<'OPENCODE_CONFIG'
            {
              "mcp": {
                "filesystem": {"type":"local","command":["mcp-server-filesystem","/home/opencode/project"],"enabled":true},
                "memory": {"type":"local","command":["mcp-server-memory"],"enabled":true},
                "github": {"type":"local","command":["mcp-server-github"],"enabled":true},
                "thinking": {"type":"local","command":["mcp-server-sequential-thinking"],"enabled":true},
                "playwright": {"type":"local","command":["playwright-mcp"],"enabled":true}
              }
            }
            OPENCODE_CONFIG
            opencode --version
            mkdir -p /root/.local/share/opencode-studio
            touch /root/.local/share/opencode-studio/setup-ok
        """.trimIndent()
        runPty(loginCommand("/bin/bash", "-lc", script), onLine) { _ -> onDone(hasOpenCode()) }
    }

    fun startOpenCodeServer(onLine: (String) -> Unit, onDone: (Boolean) -> Unit) {
        if (serverJob?.isActive == true) return
        serverJob = runPty(loginCommand("/bin/bash", "-lc", "cd /home/opencode/project && exec opencode serve --hostname 127.0.0.1 --port 4096"), onLine, onDone)
    }

    fun runShell(command: String, onLine: (String) -> Unit, onDone: (Boolean) -> Unit) =
        runPty(loginCommand("/bin/bash", "-lc", command), onLine, onDone)

    fun openTerminal(): ProotLauncher.Session? = if (hasDebian()) launcher.startSession("debian", user = "root") else null

    fun projectDirectory(): File = File(prefixDir, "var/lib/pr/containers/debian/rootfs/home/opencode/project").also {
        if (hasDebian()) it.mkdirs()
    }

    fun createProjectZip(): File {
        val root = projectDirectory()
        val out = File(cacheDir, "opencode-workspace.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            root.walkTopDown().filter { it.isFile && !it.isHidden }.forEach { file ->
                val path = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                zip.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return out
    }

    private fun loginCommand(vararg command: String): List<String> = listOf(
        File(prefixDir, "bin/pr-cli").absolutePath, "login", "debian", "--user", "root", "--", *command
    )

    private fun runPty(args: List<String>, onLine: (String) -> Unit, onDone: (Boolean) -> Unit) = scope.launch {
        val session = withContext(Dispatchers.IO) { launcher.startCustomSession(args) }
        if (session == null) {
            withContext(Dispatchers.Main) { onLine("Could not start PRoot. Check that the ARM64 runtime is installed."); onDone(false) }
            return@launch
        }
        val buffer = ByteArray(4096)
        val pending = ByteArrayOutputStream()
        var success = false
        try {
            while (true) {
                val count = withContext(Dispatchers.IO) { session.read(buffer) }
                if (count <= 0) break
                for (i in 0 until count) {
                    if (buffer[i] == '\n'.code.toByte()) {
                        val line = pending.toString(StandardCharsets.UTF_8.name()).trimEnd('\r')
                        pending.reset()
                        withContext(Dispatchers.Main) { onLine(line) }
                    } else pending.write(buffer[i].toInt())
                }
            }
            if (pending.size() > 0) withContext(Dispatchers.Main) { onLine(pending.toString(StandardCharsets.UTF_8.name())) }
            success = true
        } catch (e: Exception) {
            Log.e(TAG, "PRoot session failed", e)
            withContext(Dispatchers.Main) { onLine("Runtime error: ${e.message ?: "unknown error"}") }
        } finally {
            runCatching { session.close() }
            withContext(Dispatchers.Main) { onDone(success) }
        }
    }
}
