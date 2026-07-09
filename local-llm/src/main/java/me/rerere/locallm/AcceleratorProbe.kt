package me.rerere.locallm

import com.google.ai.edge.litertlm.Backend

/** Resolved set of backends for one engine load. */
data class ResolvedBackends(
    val main: Backend,
    val vision: Backend?,
    val audio: Backend?,
    /** The effective accelerator actually chosen (after AUTO resolution + crash fallback). */
    val effective: LocalAccelerator,
    val usingGpu: Boolean,
)

object AcceleratorProbe {
    fun resolve(model: InstalledLocalModel): ResolvedBackends {
        val prefersGpu = model.defaultConfig.accelerators.firstOrNull()?.equals("gpu", ignoreCase = true) == true
        val gpuBlocked = model.runtimeFlags.gpuCrashed

        val useGpu = when (model.config.accelerator) {
            LocalAccelerator.CPU -> false
            LocalAccelerator.GPU -> !gpuBlocked
            LocalAccelerator.AUTO -> prefersGpu && !gpuBlocked
        }

        val main: Backend = if (useGpu) Backend.GPU() else Backend.CPU()
        val vision: Backend? = if (model.supportsImage && !model.runtimeFlags.visionUnavailable) {
            val visionPrefersGpu =
                model.defaultConfig.visionAccelerator?.equals("gpu", ignoreCase = true) == true
            if (useGpu && visionPrefersGpu) Backend.GPU() else Backend.CPU()
        } else null
        val audio: Backend? = if (model.supportsAudio) {
            if (useGpu) Backend.GPU() else Backend.CPU()
        } else null

        return ResolvedBackends(
            main = main,
            vision = vision,
            audio = audio,
            effective = if (useGpu) LocalAccelerator.GPU else LocalAccelerator.CPU,
            usingGpu = useGpu,
        )
    }
}
