package com.drain.scout

import kotlin.system.exitProcess

/** Runs inside Shizuku's process with shell privileges. Started by Shizuku.bindUserService. */
class ShellService : IShell.Stub() {

    override fun exec(command: String): String = ShellRunner.run(command)

    override fun destroy() {
        exitProcess(0)
    }
}
