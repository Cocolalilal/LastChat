package me.rerere.workspace

import java.io.File

data class ProotRuntime(
    val name: String,
    val executable: File,
    val loader: File,
    val loader32: File?,
)

data class ProotLaunchMode(
    val name: String,
    val environment: Map<String, String>,
)

data class ProotLaunchSelection(
    val runtimeName: String,
    val launchModeName: String,
)

object ProotRuntimes {
    fun resolve(nativeLibraryDir: File): List<ProotRuntime> = buildList {
        addIfPresent(
            name = "workspace",
            executable = File(nativeLibraryDir, "libproot_exec.so"),
            loader = File(nativeLibraryDir, "libproot_loader.so"),
            loader32 = File(nativeLibraryDir, "libproot_loader32.so"),
        )
    }

    private fun MutableList<ProotRuntime>.addIfPresent(
        name: String,
        executable: File,
        loader: File,
        loader32: File?,
    ) {
        if (executable.isFile && loader.isFile) {
            add(ProotRuntime(name, executable, loader, loader32?.takeIf { it.isFile }))
        }
    }
}

object ProotLaunchModes {
    val default = ProotLaunchMode(
        name = "default",
        environment = emptyMap(),
    )
    val noSeccomp = ProotLaunchMode(
        name = "no-seccomp",
        environment = mapOf(
            "PROOT_NO_SECCOMP" to "1",
        ),
    )
    val noSeccompAssume = ProotLaunchMode(
        name = "no-seccomp-assume",
        environment = mapOf(
            "PROOT_NO_SECCOMP" to "1",
            "PROOT_ASSUME_NEW_SECCOMP" to "1",
        ),
    )
    val compat = ProotLaunchMode(
        name = "compat",
        environment = mapOf(
            "PROOT_NO_SECCOMP" to "1",
            "PROOT_ASSUME_NEW_SECCOMP" to "1",
            "PROOT_FORCE_KOMPAT" to "1",
        ),
    )
    val all = listOf(default, noSeccomp, noSeccompAssume, compat)
}

object ProotLaunchPreferences {
    fun read(tempDir: File): ProotLaunchSelection? = runCatching {
        val lines = File(tempDir, FILE_NAME)
            .takeIf { it.isFile }
            ?.readLines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: return@runCatching null
        ProotLaunchSelection(
            runtimeName = lines.getOrNull(0) ?: return@runCatching null,
            launchModeName = lines.getOrNull(1) ?: return@runCatching null,
        )
    }.getOrNull()

    fun write(
        tempDir: File,
        runtime: ProotRuntime,
        mode: ProotLaunchMode,
    ) {
        runCatching {
            tempDir.mkdirs()
            File(tempDir, FILE_NAME).writeText("${runtime.name}\n${mode.name}\n")
        }
    }

    private const val FILE_NAME = "proot-launch.txt"
}
