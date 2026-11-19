package eu.hxreborn.pixelinjector.ui.support

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException

private const val CONTRIBUTOR_ASSETS = "contributors"
private const val LOG_TAG = "ContributorSnapshot"

internal data class Contributor(
    val login: String,
    val added: Long,
    val removed: Long,
    val url: String,
    val avatar: ImageBitmap?,
    val owner: Boolean,
)

internal object ContributorSnapshot {
    private var contributors: List<Contributor>? = null

    @Synchronized
    fun load(assets: AssetManager): List<Contributor> = contributors ?: read(assets).also { contributors = it }

    private fun read(assets: AssetManager): List<Contributor> =
        try {
            val snapshot =
                assets.open("$CONTRIBUTOR_ASSETS/contributors.json").bufferedReader().use {
                    JSONArray(it.readText())
                }
            List(snapshot.length()) { index ->
                val row = snapshot.getJSONObject(index)
                Contributor(
                    login = row.getString("login"),
                    added = row.getLong("added"),
                    removed = row.getLong("removed"),
                    url = row.getString("url"),
                    avatar = readAvatar(assets, row.getString("avatar")),
                    owner = row.optBoolean("owner"),
                )
            }
        } catch (e: IOException) {
            Log.w(LOG_TAG, "Unable to read bundled contributors", e)
            emptyList()
        } catch (e: JSONException) {
            Log.w(LOG_TAG, "Invalid bundled contributor data", e)
            emptyList()
        }

    private fun readAvatar(
        assets: AssetManager,
        path: String,
    ): ImageBitmap? =
        try {
            assets.open("$CONTRIBUTOR_ASSETS/$path").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        } catch (e: IOException) {
            Log.w(LOG_TAG, "Unable to read contributor avatar $path", e)
            null
        }
}
