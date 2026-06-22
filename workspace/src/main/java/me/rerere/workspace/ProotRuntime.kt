package me.rerere.workspace

import java.io.File

data class ProotRuntime(
    val name: String,
    val executable: File,
    val loader: File,
    val loader32: File?,
    val supportsAshmemMemfd: Boolean = false,
)

data class ProotLaunchMode(
    val name: String,
    val environment: Map<String, String>,
    val minimalFlags: Boolean = false,
    val verbose: Boolean = false,
    val useAshmemMemfd: Boolean = false,
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
            supportsAshmemMemfd = true,
        )
        addIfPresent(
            name = "termux-userland",
            executable = File(nativeLibraryDir, "libproot-userland.so"),
            loader = File(nativeLibraryDir, "libproot-loader.so"),
            loader32 = File(nativeLibraryDir, "libproot-loader32.so"),
        )
        addIfPresent(
            name = "termux",
            executable = File(nativeLibraryDir, "libproot.so"),
            loader = File(nativeLibraryDir, "libproot-loader.so"),
            loader32 = File(nativeLibraryDir, "libproot-loader32.so"),
        )
    }

    private fun MutableList<ProotRuntime>.addIfPresent(
        name: String,
        executable: File,
        loader: File,
        loader32: File?,
        supportsAshmemMemfd: Boolean = false,
    ) {
        if (executable.isFile && loader.isFile) {
            add(ProotRuntime(name, executable, loader, loader32?.takeIf { it.isFile }, supportsAshmemMemfd))
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
    val minimal = ProotLaunchMode(
        name = "minimal",
        environment = emptyMap(),
        minimalFlags = true,
    )
    val minimalAssume = ProotLaunchMode(
        name = "minimal-assume",
        environment = mapOf(
            "PROOT_NO_SECCOMP" to "1",
            "PROOT_ASSUME_NEW_SECCOMP" to "1",
        ),
        minimalFlags = true,
    )
    val noMemfd = ProotLaunchMode(
        name = "no-memfd",
        environment = mapOf(
            "PROOT_NO_SECCOMP" to "1",
            "PROOT_ASSUME_NEW_SECCOMP" to "1",
            "PROOT_ASSUME_MEMFD_UNSUPPORTED" to "1",
        ),
        useAshmemMemfd = true,
    )
    val noMemfdMinimal = ProotLaunchMode(
        name = "no-memfd-minimal",
        environment = mapOf(
            "PROOT_NO_SECCOMP" to "1",
            "PROOT_ASSUME_NEW_SECCOMP" to "1",
            "PROOT_ASSUME_MEMFD_UNSUPPORTED" to "1",
            "PROOT_IGNORE_MISSING_BINDINGS" to "1",
        ),
        minimalFlags = true,
        useAshmemMemfd = true,
    )
    val all = listOf(noMemfd, noMemfdMinimal, default, noSeccomp, noSeccompAssume, compat, minimal, minimalAssume)
    val verboseDiagnostic = ProotLaunchMode(
        name = "verbose-diagnostic",
        environment = mapOf(
            "PROOT_NO_SECCOMP" to "1",
            "PROOT_ASSUME_NEW_SECCOMP" to "1",
            "PROOT_ASSUME_MEMFD_UNSUPPORTED" to "1",
        ),
        minimalFlags = false,
        verbose = true,
        useAshmemMemfd = true,
    )
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
