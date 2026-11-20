package eu.hxreborn.pixelinjector

import java.io.File
import java.util.Properties
import kotlin.math.abs

private const val OUTCOME_PREFIX = "installOutcome."
private const val VERSION_KEY = "installVersion"
private const val BOOT_KEY = "bootInstantMs"
private const val GENERATION_KEY = "generation"
private const val BOOT_TOLERANCE_MS = 10_000L

internal class InstallDiagnostics(
    val generation: String,
    val failures: Map<String, String>,
) {
    companion object {
        fun file(
            directory: File,
            process: String,
        ): File = File(directory, "pixelinjector-guard-${process.replace(':', '_')}.properties")

        fun record(
            state: Properties,
            version: Int,
            outcomes: Map<String, String>,
        ) {
            state.setProperty(VERSION_KEY, version.toString())
            outcomes.forEach { (tweak, outcome) ->
                state.setProperty(OUTCOME_PREFIX + tweak, outcome)
            }
        }

        fun clear(state: Properties) {
            state.remove(VERSION_KEY)
            state.keys.removeAll { it is String && it.startsWith(OUTCOME_PREFIX) }
        }

        fun read(
            contents: String,
            pid: Int,
            version: Int,
            bootInstant: Long,
        ): InstallDiagnostics? {
            val state = Properties()
            try {
                contents.reader().use(state::load)
            } catch (_: IllegalArgumentException) {
                return null
            }
            if (state.getProperty(VERSION_KEY) != version.toString()) return null
            val recordedBoot = state.getProperty(BOOT_KEY)?.toLongOrNull() ?: return null
            if (abs(bootInstant - recordedBoot) > BOOT_TOLERANCE_MS) return null
            val generation = state.getProperty(GENERATION_KEY) ?: return null
            if (generation.substringBefore('-').toIntOrNull() != pid) return null
            val failures =
                buildMap {
                    for (key in state.stringPropertyNames()) {
                        if (!key.startsWith(OUTCOME_PREFIX)) continue
                        val outcome = state.getProperty(key)
                        if (isInstallFailure(
                                outcome,
                            )
                        ) {
                            put(key.removePrefix(OUTCOME_PREFIX), outcome)
                        }
                    }
                }
            return InstallDiagnostics(generation, failures)
        }
    }
}

internal fun isInstallFailure(body: String): Boolean {
    if (body.startsWith("target not found tweak=")) return true
    return body.startsWith("install failed tweak=")
}
