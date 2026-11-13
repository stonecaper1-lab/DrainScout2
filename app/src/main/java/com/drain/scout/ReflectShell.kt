package com.drain.scout

import rikka.shizuku.Shizuku

/**
 * Fallback used only when the Shizuku user service cannot be started.
 * Shizuku.newProcess is private since API 13.1, so it is called through reflection.
 */
internal object ReflectShell {
    fun run(command: String): String = try {
        val method = Shizuku::class.java.declaredMethods
            .firstOrNull { it.name == "newProcess" && it.parameterTypes.size == 3 }
            ?: error("Shizuku.newProcess was not found in this Shizuku API version")
        method.isAccessible = true
        val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        ShellRunner.collect(process)
    } catch (t: Throwable) {
        val root = t.cause ?: t
        ShellRunner.ERR + root.javaClass.simpleName + ": " + root.message
    }
}
