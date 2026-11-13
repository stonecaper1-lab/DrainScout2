package com.drain.scout

import java.io.InputStream

/**
 * Reads a finished or running process without ever calling waitFor(timeout) or exitValue().
 * Shizuku's remote process object throws "process hasn't exited" from both, which is what
 * broke capture in v1.3.0. Streams are read to EOF on their own threads, with a watchdog.
 */
internal object ShellRunner {
    /** Marks a result that is an error message, not command output. */
    const val ERR = "\u0001ERR:"

    private const val TIMEOUT_MS = 30_000L
    private const val MAX_CHARS = 300_000

    /** Runs a shell command in the current process. Used inside the Shizuku user service. */
    fun run(command: String): String = try {
        collect(ProcessBuilder("sh", "-c", command).start())
    } catch (t: Throwable) {
        ERR + t.javaClass.simpleName + ": " + t.message
    }

    fun collect(process: Process, timeoutMs: Long = TIMEOUT_MS, maxChars: Int = MAX_CHARS): String {
        val out = StringBuilder()
        val err = StringBuilder()
        val outThread = pump(process.inputStream, out, maxChars)
        val errThread = pump(process.errorStream, err, 4_000)

        outThread.join(timeoutMs)
        if (outThread.isAlive) {
            try {
                process.destroy()
            } catch (_: Throwable) {
            }
            outThread.join(2_000L)
        }
        errThread.join(1_000L)
        try {
            process.destroy()
        } catch (_: Throwable) {
        }

        val stdout = synchronized(out) { out.toString() }
        if (stdout.isNotEmpty()) return stdout
        val stderr = synchronized(err) { err.toString() }.trim()
        return if (stderr.isNotEmpty()) ERR + stderr else ""
    }

    /** Keeps reading after the limit so the process never blocks on a full pipe. */
    private fun pump(stream: InputStream, sink: StringBuilder, limit: Int): Thread {
        val thread = Thread {
            try {
                val buf = CharArray(8192)
                stream.bufferedReader().use { reader ->
                    while (true) {
                        val n = reader.read(buf)
                        if (n < 0) break
                        synchronized(sink) {
                            val room = limit - sink.length
                            if (room > 0) sink.append(buf, 0, minOf(n, room))
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        thread.isDaemon = true
        thread.start()
        return thread
    }
}
