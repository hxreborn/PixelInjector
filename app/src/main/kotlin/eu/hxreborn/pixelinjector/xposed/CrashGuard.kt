package eu.hxreborn.pixelinjector.xposed

import eu.hxreborn.pixelinjector.InstallDiagnostics
import java.io.File
import java.io.IOException
import java.util.Properties
import kotlin.math.abs

private const val KEY_IDENTITY = "identity"
private const val KEY_BOOT = "boot"
private const val KEY_BOOT_INSTANT = "bootInstantMs"
private const val KEY_GENERATION = "generation"
private const val KEY_STABLE = "stable"
private const val KEY_EARLY_DEATHS = "earlyDeaths"
private const val KEY_INSTALLING = "installing"
private const val KEY_QUARANTINED = "quarantined"
private const val KEY_SAFE_MODE_BOOT = "safeModeBoot"
private const val RUNTIME_DEATHS_FOR_SAFE_MODE = 2
private const val ANY_DEATHS_FOR_SAFE_MODE = 4
private const val STAGING_SUFFIX = ".tmp"
private const val SAME_BOOT_TOLERANCE_MS = 10_000L

private fun Properties.markStable() {
    remove(KEY_INSTALLING)
    setProperty(KEY_STABLE, true.toString())
    setProperty(KEY_EARLY_DEATHS, "0")
}

internal class CrashGuard private constructor(
    private val file: File,
    private val state: Properties,
    private val generation: String,
    private val bootInstant: () -> Long,
    val crashedDuringInstall: String?,
    val earlyDeaths: Int,
    val quarantined: Set<String>,
    val safeMode: Boolean,
) {
    private var pendingFailures: MutableMap<String, String>? = mutableMapOf()

    @Synchronized
    fun installing(tweak: String) {
        state.setProperty(KEY_INSTALLING, tweak)
        write(file, state, generation, bootInstant)
    }

    @Synchronized
    fun installed(
        version: Int? = null,
        outcomes: Map<String, String> = emptyMap(),
    ) {
        state.remove(KEY_INSTALLING)
        if (version != null) {
            InstallDiagnostics.record(state, version, outcomes + pendingFailures.orEmpty())
        }
        pendingFailures = null
        write(file, state, generation, bootInstant)
    }

    @Synchronized
    fun recordDeferredFailure(
        version: Int,
        tweak: String,
        message: String,
    ) {
        val pending = pendingFailures
        if (pending != null) {
            pending[tweak] = message
            return
        }
        InstallDiagnostics.record(state, version, mapOf(tweak to message))
        write(file, state, generation, bootInstant)
    }

    @Synchronized
    fun stable() {
        val current = read(file)
        if (current.getProperty(KEY_GENERATION) != generation) return
        current.markStable()
        write(file, current, generation, bootInstant)
        state.markStable()
    }

    companion object {
        fun open(
            file: File,
            identity: String,
            bootInstant: () -> Long,
            generation: String,
            countEarlyDeaths: Boolean,
        ): CrashGuard {
            val stored = read(file)
            val state =
                if (stored.getProperty(KEY_IDENTITY) == identity) {
                    stored
                } else {
                    Properties().apply { setProperty(KEY_IDENTITY, identity) }
                }
            val storedBoot = state.getProperty(KEY_BOOT)
            val sameBoot =
                storedBoot != null &&
                    state.getProperty(KEY_BOOT_INSTANT)?.toLongOrNull()?.let {
                        abs(bootInstant() - it) <= SAME_BOOT_TOLERANCE_MS
                    } == true
            val bootId = storedBoot.takeIf { sameBoot } ?: generation
            val crashed = state.getProperty(KEY_INSTALLING)?.takeIf { sameBoot && it.isNotEmpty() }
            state.remove(KEY_INSTALLING)
            if (crashed != null) {
                val quarantined = (quarantinedTweaks(state) + crashed).distinct()
                state.setProperty(KEY_QUARANTINED, quarantined.joinToString(","))
            }
            val storedDeaths =
                state.getProperty(KEY_EARLY_DEATHS)?.toIntOrNull()?.takeIf { sameBoot } ?: 0
            val diedEarly =
                countEarlyDeaths && sameBoot && state.getProperty(KEY_STABLE) == false.toString()
            val earlyDeaths =
                when {
                    diedEarly -> storedDeaths + 1
                    countEarlyDeaths -> 0
                    else -> storedDeaths
                }
            val runtimeLoop = crashed == null && earlyDeaths >= RUNTIME_DEATHS_FOR_SAFE_MODE
            if (diedEarly && (runtimeLoop || earlyDeaths >= ANY_DEATHS_FOR_SAFE_MODE)) {
                state.setProperty(KEY_SAFE_MODE_BOOT, bootId)
            }
            state.setProperty(KEY_BOOT, bootId)
            InstallDiagnostics.clear(state)
            state.setProperty(KEY_GENERATION, generation)
            state.setProperty(KEY_STABLE, false.toString())
            state.setProperty(KEY_EARLY_DEATHS, earlyDeaths.toString())
            deleteStaleStaging(file, generation)
            write(file, state, generation, bootInstant)
            return CrashGuard(
                file,
                state,
                generation,
                bootInstant,
                crashed,
                earlyDeaths,
                quarantinedTweaks(state).toSet(),
                state.getProperty(KEY_SAFE_MODE_BOOT) == bootId,
            )
        }

        private fun quarantinedTweaks(state: Properties): List<String> =
            state
                .getProperty(KEY_QUARANTINED)
                .orEmpty()
                .split(',')
                .filter { it.isNotEmpty() }

        private fun read(file: File): Properties =
            try {
                Properties().apply { if (file.isFile) file.inputStream().use(::load) }
            } catch (_: IllegalArgumentException) {
                Properties()
            }

        private fun stagingName(
            file: File,
            generation: String,
        ): String = "${file.name}.$generation$STAGING_SUFFIX"

        private fun deleteStaleStaging(
            file: File,
            generation: String,
        ) {
            val current = stagingName(file, generation)
            val prefix = "${file.name}."
            file.parentFile
                ?.listFiles { _, name ->
                    name.startsWith(prefix) && name.endsWith(STAGING_SUFFIX) && name != current
                }?.forEach { it.delete() }
        }

        private fun write(
            file: File,
            state: Properties,
            generation: String,
            bootInstant: () -> Long,
        ) {
            state.setProperty(KEY_BOOT_INSTANT, bootInstant().toString())
            val staging = File(file.parentFile, stagingName(file, generation))
            staging.outputStream().use { state.store(it, null) }
            val renamed = staging.renameTo(file)
            if (!renamed) throw IOException("rename ${staging.path} to ${file.path} failed")
        }
    }
}
