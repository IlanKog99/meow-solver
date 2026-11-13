package com.ilan.meowsolver.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small on-device log the user can share for debugging. Lines live in memory and in filesDir/debug.log,
 * so they survive the app being killed. Never throws: logging must not break the solver.
 */
object DebugLog {
    const val MAX_LINES = 2000
    const val FILE_NAME = "debug.log"

    private val lines = ArrayDeque<String>()
    private var file: File? = null
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var crashHook = false

    @Synchronized fun init(dir: File) {
        if (!crashHook) {
            crashHook = true
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { t, e -> try { error("CRASH on ${t.name}", e) } finally { prev?.uncaughtException(t, e) } }
        }
        val f = File(dir, FILE_NAME)
        if (f == file) return
        file = f
        lines.clear()
        try { if (f.exists()) lines.addAll(f.readLines()) } catch (_: Exception) {}
        trim(rewrite = true)
    }

    // ponytail: file I/O on the caller (main) thread; one small append per entry. Move to an executor if it ever janks.
    @Synchronized fun log(msg: String) { try {
        val add = "${time.format(Date())} $msg".lines()
        lines.addAll(add)
        // Rewrites the whole file only when trimming (every ~MAX_LINES/4 lines); appends otherwise.
        if (!trim(rewrite = true)) file?.appendText(add.joinToString("\n", postfix = "\n"))
    } catch (_: Throwable) {} } // incl. OOM: logging must never break the caller

    fun error(msg: String, e: Throwable) { try { log("$msg: ${e.stackTraceToString().trimEnd()}") } catch (_: Throwable) {} }

    /** One line per row: region letter + '.' empty / 'x' X / 'C' cat, e.g. "a. ax bC". Enough to rebuild the board. */
    fun describe(b: Board): String = runCatching {
        (0 until b.n).joinToString("\n") { r -> (0 until b.n).joinToString(" ") { c ->
            val i = b.idx(r, c)
            "${'a' + b.region[i] % 26}${when (b.state[i]) { Cell.EMPTY -> '.'; Cell.X -> 'x'; Cell.CAT -> 'C' }}"
        } }
    }.getOrElse { "(board unreadable: $it)" }

    @Synchronized fun text(): String = lines.joinToString("\n")

    @Synchronized fun clear() {
        lines.clear()
        try { file?.writeText("") } catch (_: Exception) {}
    }

    /** Over the cap → drop to 3/4 of it (so trimming is rare). Returns true if it trimmed. */
    private fun trim(rewrite: Boolean): Boolean {
        if (lines.size <= MAX_LINES) return false
        repeat(lines.size - MAX_LINES * 3 / 4) { lines.removeFirst() }
        // Temp file + rename: a kill mid-write must not wipe the history.
        if (rewrite) file?.let { f -> try {
            val tmp = File(f.path + ".tmp")
            tmp.writeText(lines.joinToString("\n", postfix = "\n"))
            if (!tmp.renameTo(f)) f.writeText(tmp.readText())
        } catch (_: Exception) {} }
        return true
    }
}
