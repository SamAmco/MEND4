package co.samco.mend4.desktop.session

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.system.exitProcess

/** Applied before reading passwords. OS-specific protection lives only here. */
internal object MemoryProtection {
    private val flags = listOf(
        "-XX:+DisableAttachMechanism",
        "-XX:-HeapDumpOnOutOfMemoryError",
        "-XX:-CreateCoredumpOnCrash",
    )

    fun prepare(args: Array<String>) {
        if (System.getProperty("os.name") !in listOf("Linux", "Mac OS X")) return
        // Attach cannot be disabled after VM startup. Relaunch once, before any key is loaded.
        if (!ManagementFactory.getRuntimeMXBean().inputArguments.containsAll(flags)) {
            check(System.getProperty("mend.protected") != "true") { "Unable to start a JVM with memory-dump protection" }
            val child = ProcessBuilder(listOf(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                *flags.toTypedArray(), "-Dmend.protected=true",
                "-Duser.home=${System.getProperty("user.home")}",
                "-cp", System.getProperty("java.class.path"),
                "co.samco.mend4.desktop.MainKt", *args,
            )).inheritIO().start()
            val hook = Thread { child.destroy() }
            Runtime.getRuntime().addShutdownHook(hook)
            val status = child.waitFor()
            Runtime.getRuntime().removeShutdownHook(hook)
            exitProcess(status)
        }
        if (System.getProperty("os.name") == "Linux") LinuxMemoryProtection.disableDumps()
    }
}

internal object LinuxMemoryProtection {
    private const val PR_SET_DUMPABLE = 4
    private const val PR_GET_DUMPABLE = 3

    private interface LibC : Library {
        fun prctl(option: Int, arg2: NativeLong, arg3: NativeLong, arg4: NativeLong, arg5: NativeLong): Int
    }

    fun disableDumps() {
        val libc = Native.load("c", LibC::class.java)
        val zero = NativeLong(0)
        check(libc.prctl(PR_SET_DUMPABLE, zero, zero, zero, zero) == 0) {
            "Could not disable Linux memory dumps (errno ${Native.getLastError()}); refusing to unlock"
        }
        check(libc.prctl(PR_GET_DUMPABLE, zero, zero, zero, zero) == 0) { "Linux memory-dump protection did not take effect" }
    }
}
