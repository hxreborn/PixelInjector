package eu.hxreborn.pixelinjector.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import java.io.File

private val DIRS = listOf("/product/media/audio/ui", "/system/media/audio/ui")
private val CAMEL = Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")

private val ATTRIBUTES =
    AudioAttributes
        .Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

object SystemSounds {
    fun list(): List<String> =
        DIRS
            .flatMap { File(it).listFiles().orEmpty().asList() }
            .filter { it.isFile }
            .distinctBy { it.name }
            .sortedBy { it.name.lowercase() }
            .map { it.path }

    fun label(path: String): String =
        File(path)
            .name
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace(CAMEL, " ")
            .replaceFirstChar(Char::uppercase)

    fun play(
        context: Context,
        path: String,
    ): Ringtone? =
        RingtoneManager.getRingtone(context, Uri.fromFile(File(path)))?.apply {
            audioAttributes = ATTRIBUTES
            play()
        }
}
