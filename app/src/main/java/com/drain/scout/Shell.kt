package com.drain.scout

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** One place that runs shell commands: user service first, reflection fallback second. */
internal object Shell {
    private const val SERVICE_VERSION = 5
    private const val CONNECT_TIMEOUT_S = 10L

    @Volatile
    var lastError: String? = null

    @Volatile
    var mode: String = "not used yet"

    @Volatile
    private var service: IShell? = null

    @Volatile
    private var gate: CountDownLatch? = null

    private var appContext: Context? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null && binder.pingBinder()) IShell.Stub.asInterface(binder) else null
            gate?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @Synchronized
    private fun connect(): IShell? {
        val existing = service
        if (existing != null && existing.asBinder().isBinderAlive) return existing
        val ctx = appContext ?: return null
        return try {
            val latch = CountDownLatch(1)
            gate = latch
            val args = Shizuku.UserServiceArgs(ComponentName(ctx, ShellService::class.java))
                .daemon(false)
                .processNameSuffix("shell")
                .debuggable(false)
                .version(SERVICE_VERSION)
                .tag("drainscout")
            Shizuku.bindUserService(args, connection)
            if (latch.await(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)) {
                service
            } else {
                lastError = "Shizuku user service did not start within ${CONNECT_TIMEOUT_S}s"
                null
            }
        } catch (t: Throwable) {
            lastError = describe(t)
            null
        }
    }

    /** Returns command output, or an empty string if the command failed (see lastError). */
    fun run(command: String): String {
        val remote = connect()
        if (remote != null) {
            try {
                val out = remote.exec(command)
                mode = "User service"
                return unwrap(out)
            } catch (t: Throwable) {
                service = null
                lastError = describe(t)
            }
        }
        mode = "Reflection fallback"
        return unwrap(ReflectShell.run(command))
    }

    private fun unwrap(out: String?): String {
        if (out == null) return ""
        if (out.startsWith(ShellRunner.ERR)) {
            lastError = out.removePrefix(ShellRunner.ERR).trim()
            return ""
        }
        return out
    }

    private fun describe(t: Throwable): String {
        val root = t.cause ?: t
        return root.javaClass.simpleName + ": " + root.message
    }
}
