package eu.hxreborn.pixelinjector.xposed.hook.firefox

import android.util.AtomicFile
import eu.hxreborn.pixelinjector.betterfox.ProfileBackupMeta
import eu.hxreborn.pixelinjector.betterfox.restoreTouchedPrefs
import eu.hxreborn.pixelinjector.betterfox.touchedPrefLines
import java.io.File
import java.security.MessageDigest

private const val USER_JS = "user.js"
private const val PREFS_JS = "prefs.js"
private const val META = "meta.json"
private const val RESET = "reset"
private val PROFILE_NAME = Regex("[A-Za-z0-9]+\\.default[A-Za-z0-9._-]*")

internal fun prepareBetterfoxProfiles(
    filesDir: File,
    prefNames: Set<String>,
    resetRequest: String,
): Boolean {
    val store = File(filesDir, "pixelinjector-betterfox-backup")
    val mozilla = File(filesDir, "mozilla")
    val profiles =
        if (mozilla.exists()) {
            checkNotNull(mozilla.listFiles()) { "profile directory unreadable path=$mozilla" }
                .filter { it.isDirectory && PROFILE_NAME.matches(it.name) }
        } else {
            emptyList()
        }
    val resetMarker = File(store, RESET)
    if (resetRequest.isNotEmpty() && resetMarker.readAtomic()?.decodeToString() != resetRequest) {
        for (profile in profiles) {
            val backup = File(store, profile.name)
            if (File(backup, META).readAtomic() != null) {
                restoreProfile(backup, profile)
                check(backup.deleteRecursively()) { "backup remove failed path=$backup" }
            }
        }
        resetMarker.writeAtomic(resetRequest.toByteArray())
    }
    if (prefNames.isNotEmpty()) {
        for (profile in profiles) backUpProfile(File(store, profile.name), profile, prefNames)
    }
    return profiles.isNotEmpty()
}

private fun backUpProfile(
    backup: File,
    profile: File,
    prefNames: Set<String>,
) {
    val current = File(profile, PREFS_JS).readIfPresent()
    val metadata = File(backup, META)
    val stored =
        metadata.readAtomic()?.let {
            checkNotNull(ProfileBackupMeta.decode(it.decodeToString())) {
                "backup metadata unreadable profile=${profile.name}"
            }
        }
    if (stored != null) {
        if (stored.userJsSha256 != null) {
            val bytes =
                checkNotNull(File(backup, USER_JS).readAtomic()) {
                    "user.js backup missing profile=${profile.name}"
                }
            check(sha256(bytes) == stored.userJsSha256) {
                "user.js backup mismatch profile=${profile.name}"
            }
        }
        val missing = prefNames - stored.prefs.keys
        if (missing.isNotEmpty()) {
            metadata.writeAtomic(
                stored.withPrefs(touchedPrefLines(current, missing)).encode().toByteArray(),
            )
        }
        return
    }
    val userJs = File(profile, USER_JS)
    val userJsSha256 =
        if (userJs.exists()) {
            val bytes = userJs.readBytes()
            val copy = File(backup, USER_JS)
            copy.writeAtomic(bytes)
            sha256(bytes)
        } else {
            null
        }
    metadata.writeAtomic(
        ProfileBackupMeta(
            System.currentTimeMillis(),
            userJsSha256,
            touchedPrefLines(current, prefNames),
        ).encode().toByteArray(),
    )
}

private fun restoreProfile(
    backup: File,
    profile: File,
) {
    val meta =
        checkNotNull(
            File(backup, META).readAtomic()?.let { ProfileBackupMeta.decode(it.decodeToString()) },
        ) { "backup metadata unreadable profile=${profile.name}" }
    val userJs = File(profile, USER_JS)
    if (meta.userJsSha256 == null) {
        check(!userJs.exists() || userJs.delete()) {
            "user.js remove failed profile=${profile.name}"
        }
    } else {
        val bytes =
            checkNotNull(File(backup, USER_JS).readAtomic()) {
                "user.js backup missing profile=${profile.name}"
            }
        check(sha256(bytes) == meta.userJsSha256) {
            "user.js backup mismatch profile=${profile.name}"
        }
        userJs.writeAtomic(bytes)
        check(sha256(userJs.readBytes()) == meta.userJsSha256) {
            "user.js restore mismatch profile=${profile.name}"
        }
    }
    val prefsJs = File(profile, PREFS_JS)
    prefsJs.writeAtomic(restoreTouchedPrefs(prefsJs.readIfPresent(), meta.prefs))
    check(touchedPrefLines(prefsJs.readBytes(), meta.prefs.keys) == meta.prefs) {
        "prefs.js restore mismatch profile=${profile.name}"
    }
}

private fun File.readIfPresent(): ByteArray = if (exists()) readBytes() else ByteArray(0)

private fun File.readAtomic(): ByteArray? =
    if (exists() || File("$path.bak").exists()) AtomicFile(this).readFully() else null

private fun File.writeAtomic(bytes: ByteArray) {
    val atomic = AtomicFile(this)
    val stream = atomic.startWrite()
    try {
        stream.write(bytes)
        atomic.finishWrite(stream)
    } catch (e: Exception) {
        atomic.failWrite(stream)
        throw e
    } catch (e: LinkageError) {
        atomic.failWrite(stream)
        throw e
    }
    check(atomic.readFully().contentEquals(bytes)) { "write mismatch file=$path" }
}

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
