package eu.hxreborn.pixelinjector.support

import android.content.Context
import android.os.Build
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.support.ReportBuilder.toHex
import eu.hxreborn.pixelinjector.ui.util.Root
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private val CODE_ENTRY = Regex("""classes\d*\.dex|resources\.arsc|AndroidManifest\.xml""")
private const val RESOURCES_ENTRY = "resources.arsc"
private const val RESOURCES_OMITTED =
    "resources.arsc omitted: the full zip exceeds the attachment limit"
private const val SERVICES_JAR = "/system/framework/services.jar"
private const val BUFFER_BYTES = 1 shl 16
private val AOSP_ROM_PROPERTIES =
    listOf(
        "ro.lineage.version",
        "ro.crdroid.build.version",
        "ro.pixelexperience.version",
        "ro.derp.version",
        "ro.evolution.version",
        "ro.aospa.version",
        "ro.modversion",
    )

class RomCode(
    val kind: String,
    val path: String,
    val sha256: String,
    val size: Long,
) {
    val name: String get() = File(path).name

    fun toJson(): JSONObject =
        JSONObject()
            .put("kind", kind)
            .put("sha256", sha256)
            .put("size", size)
            .put("file", name)

    fun toStoredJson(): JSONObject = toJson().put("path", path)

    fun zip(includeResources: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun add(
                name: String,
                input: InputStream,
            ) {
                zip.putNextEntry(ZipEntry(name))
                input.use { it.copyTo(zip, BUFFER_BYTES) }
                zip.closeEntry()
            }
            ZipFile(path).use { source ->
                val code =
                    source.entries().toList().filter {
                        CODE_ENTRY.matches(it.name) &&
                            (includeResources || it.name != RESOURCES_ENTRY)
                    }
                code.forEach { add(it.name, source.getInputStream(it)) }
                if (code.none { it.name.endsWith(".dex") }) {
                    val vdex = vdex() ?: error("no dex and no vdex for $path")
                    add(vdex.name, vdex.inputStream())
                }
            }
            if (!includeResources) zip.setComment(RESOURCES_OMITTED)
        }
        return out.toByteArray()
    }

    private fun vdex(): File? {
        val source = File(path)
        val abi = Build.SUPPORTED_ABIS.first()
        val isa =
            when (abi) {
                "arm64-v8a" -> "arm64"
                "armeabi-v7a" -> "arm"
                else -> abi
            }
        return File(source.parentFile, "oat/$isa/${source.nameWithoutExtension}.vdex").takeIf {
            it.canRead()
        }
    }

    companion object {
        const val SYSTEMUI = "systemui"
        const val SERVICES = "services"

        val wanted: Boolean
            get() =
                !Build.MANUFACTURER.equals("Google", ignoreCase = true) &&
                    AOSP_ROM_PROPERTIES.all { systemProperty(it).isEmpty() }

        private fun systemProperty(name: String): String =
            runCatching {
                Class
                    .forName("android.os.SystemProperties")
                    .getMethod("get", String::class.java)
                    .invoke(null, name) as String
            }.getOrDefault("")

        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            DigestOutputStream(OutputStream.nullOutputStream(), digest).use { out ->
                file.inputStream().use { it.copyTo(out, BUFFER_BYTES) }
            }
            return digest.digest().toHex()
        }

        fun find(
            context: Context,
            kind: String,
        ): RomCode? {
            val path =
                when (kind) {
                    SYSTEMUI -> {
                        context.packageManager
                            .getApplicationInfo(
                                ModuleConstants.SYSTEMUI_PACKAGE,
                                0,
                            ).sourceDir
                    }

                    SERVICES -> {
                        SERVICES_JAR
                    }

                    else -> {
                        return null
                    }
                }
            var file = File(path)
            if (!file.canRead()) {
                val copy =
                    File(
                        context.cacheDir,
                        "rom-code/$kind/${file.name}",
                    ).apply { parentFile?.mkdirs() }
                check(
                    Root.copyReadable(path, copy),
                ) { "$path is not readable and the root copy failed" }
                file = copy
            }
            return RomCode(kind, file.path, sha256Of(file), file.length())
        }

        fun from(json: JSONObject?): RomCode? =
            json?.let {
                RomCode(
                    kind = it.optString("kind"),
                    path = it.optString("path"),
                    sha256 = it.optString("sha256"),
                    size = it.optLong("size"),
                )
            }
    }
}
