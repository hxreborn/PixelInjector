package eu.hxreborn.pixelinjector.xposed.hook.systemui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.member
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TWEAK = "NowPlayingChip"
private const val SHADE_EXPANSION_STATE_MANAGER =
    "com.android.systemui.shade.ShadeExpansionStateManager"
private const val STATE_BAR = "bar"
private const val STATE_CHIP = "chip"
private const val STATE_TINT = "tint"
private const val STATE_SHADE = "shade"
private const val ICON_INDEX = 0
private const val TITLE_INDEX = 1
private const val ICON_DP = 12
private const val PADDING_DP = 4
private const val GAP_DP = 3
private const val TITLE_SP = 11f
private const val PILL_ALPHA = 0x33000000
private const val COLOR_MASK = 0x00FFFFFF
private const val TEARDOWN_SECONDS = 3L

private val main = Handler(Looper.getMainLooper())
private var bar: ViewGroup? = null
private var chip: LinearLayout? = null

@Volatile private var sessions: ChipSessions? = null
private val closing = CopyOnWriteArrayList<ChipSessions>()
private var tint = Color.WHITE
private var shadeExpanded = false
private var hasTrack = false
private var retired = false
private val enabled = Switch(Prefs.NOW_PLAYING_CHIP) { updateSessions() }
private val rightSide =
    Value(Prefs.NOW_PLAYING_CHIP_SIDE, onLoad = { postUi(::place) }) {
        it ==
            Prefs.DOWNLOAD_CHIP_RIGHT
    }
private val titleWidth =
    Value(Prefs.NOW_PLAYING_CHIP_WIDTH, onLoad = {
        postUi(::place)
    }) { it.coerceIn(Prefs.NOW_PLAYING_WIDTH_MIN, Prefs.NOW_PLAYING_WIDTH_MAX) }
private val hideShade = Switch(Prefs.NOW_PLAYING_CHIP_HIDE_SHADE) { postUi(::render) }

internal val nowPlayingChip =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEMUI),
        prefs = listOf(enabled, rightSide, titleWidth, hideShade),
        install = XposedModule::installNowPlayingChip,
        saveState = ::saveNowPlayingChipState,
        onReloadRejected = {
            main.post {
                retired = false
                updateSessions()
            }
        },
        restoreState = { state ->
            val saved = state as? Map<*, *>
            (saved?.get(STATE_BAR) as? ViewGroup)?.let { savedBar ->
                postUi {
                    if (!savedBar.isAttachedToWindow) return@postUi
                    tint = saved[STATE_TINT] as? Int ?: Color.WHITE
                    shadeExpanded = saved[STATE_SHADE] as? Boolean ?: false
                    chip = saved[STATE_CHIP] as? LinearLayout
                    attach(savedBar)
                }
            }
        },
    )

private fun retireSessions() {
    retired = true
    sessions?.close()
    sessions = null
}

private fun saveNowPlayingChipState(): HashMap<String, Any?> {
    val retiredOnMain = CountDownLatch(1)
    main.post {
        isolateChipCallback(TWEAK, "retire") { retireSessions() }
        retiredOnMain.countDown()
    }
    val timedOut = !retiredOnMain.await(TEARDOWN_SECONDS, TimeUnit.SECONDS)
    if (timedOut) retireSessions()
    closing.forEach(ChipSessions::awaitStopped)
    check(!timedOut) { "NowPlayingChip teardown timed out" }
    return hashMapOf(
        STATE_BAR to bar,
        STATE_CHIP to chip,
        STATE_TINT to tint,
        STATE_SHADE to shadeExpanded,
    )
}

private class NowPlayingBindings(
    val inflate: Method,
    val tint: List<Method>,
    val shade: Method,
) {
    companion object {
        fun resolve(cl: ClassLoader): NowPlayingBindings =
            NowPlayingBindings(
                cl.loadClass(STATUS_BAR_VIEW).requiredMethod("onFinishInflate"),
                resolveStatusBarTint(cl).also { check(it.isNotEmpty()) { "Clock.onDarkChanged" } },
                cl
                    .loadClass(SHADE_EXPANSION_STATE_MANAGER)
                    .requiredMethod("updateStateInternal", Int::class.javaPrimitiveType),
            )
    }
}

private fun postUi(action: () -> Unit) {
    main.post { if (!retired) isolateChipCallback(TWEAK, "ui", action) }
}

private val LinearLayout.iconView: ImageView get() = getChildAt(ICON_INDEX) as ImageView

private val LinearLayout.titleView: TextView get() = getChildAt(TITLE_INDEX) as TextView

private fun attach(view: ViewGroup) {
    bar = view
    chip = chip?.takeIf { it.context === view.context }
        ?: view.findViewWithTag<LinearLayout>(TWEAK)
        ?: LinearLayout(view.context).apply {
            tag = TWEAK
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padding = context.dp(PADDING_DP)
            setPadding(padding, padding, padding, padding)
            background = GradientDrawable().apply { cornerRadius = Float.MAX_VALUE }
            visibility = View.GONE
            addView(
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                },
                LinearLayout.LayoutParams(context.dp(ICON_DP), context.dp(ICON_DP)).apply {
                    marginEnd =
                        context.dp(GAP_DP)
                },
            )
            addView(
                TextView(context).apply {
                    setSingleLine()
                    ellipsize = TextUtils.TruncateAt.END
                    textSize = TITLE_SP
                    includeFontPadding = false
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    place()
    applyTint()
    updateSessions()
    Logger.info("attached tweak=$TWEAK")
}

private fun place() {
    val view = chip ?: return
    val statusBar = bar ?: return
    view.titleView.maxWidth = view.context.dp(titleWidth.value)
    placeStatusBarChip(statusBar, view, rightSide.value, ViewGroup.LayoutParams.WRAP_CONTENT, TWEAK)
}

private fun applyTint() {
    val view = chip ?: return
    (view.background as GradientDrawable).setColor((tint and COLOR_MASK) or PILL_ALPHA)
    view.titleView.setTextColor(tint)
}

private fun render() {
    chip?.visibility =
        if (enabled.enabled && hasTrack && !(hideShade.enabled && shadeExpanded)) {
            View.VISIBLE
        } else {
            View.GONE
        }
}

private fun updateSessions() {
    postUi {
        val context = bar?.context ?: return@postUi
        if (enabled.enabled) {
            val running = sessions
            if (running != null) {
                running.requestRefresh()
            } else {
                sessions = ChipSessions(context).also { it.start() }
            }
        } else {
            sessions?.close()
            sessions = null
            hasTrack = false
        }
        render()
    }
}

private class ChipSessions(
    private val context: Context,
) {
    private val thread = HandlerThread("PixelInjector-NowPlayingChip").apply { start() }
    private val worker = Handler(thread.looper)
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val controllers = linkedMapOf<MediaController, MediaController.Callback>()

    @Volatile private var closed = false
    private val listener =
        MediaSessionManager.OnActiveSessionsChangedListener { active ->
            isolateChipCallback(TWEAK, "sessions") { replace(active.orEmpty()) }
        }

    fun start() {
        worker.post {
            isolateChipCallback(TWEAK, "start") {
                if (closed) return@isolateChipCallback
                manager.addOnActiveSessionsChangedListener(listener, null, worker)
                replace(manager.getActiveSessions(null))
            }
        }
    }

    fun requestRefresh() {
        worker.post { refreshSafely() }
    }

    private fun replace(active: List<MediaController>) {
        if (closed) return
        unregisterAll()
        active.forEach { controller ->
            val callback =
                object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = refreshSafely()

                    override fun onPlaybackStateChanged(state: PlaybackState?) = refreshSafely()

                    override fun onSessionDestroyed() {
                        isolateChipCallback(TWEAK, "destroyed") {
                            controllers.remove(controller)?.let(controller::unregisterCallback)
                            refresh()
                        }
                    }
                }
            controllers[controller] = callback
            controller.registerCallback(callback, worker)
        }
        refresh()
    }

    private fun unregisterAll() {
        controllers.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        controllers.clear()
    }

    private fun refreshSafely() {
        isolateChipCallback(TWEAK, "media") { refresh() }
    }

    private fun refresh() {
        if (closed) return
        val selected =
            controllers.keys.firstOrNull {
                it.playbackState?.state ==
                    PlaybackState.STATE_PLAYING
            }
        val metadata = selected?.metadata
        val title =
            metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
                ?: metadata?.getText(MediaMetadata.METADATA_KEY_TITLE)
        val icon =
            selected?.let { appIcon(it.packageName) }
        postUi {
            if (closed) return@postUi
            hasTrack = !title.isNullOrBlank()
            chip?.let { view ->
                view.iconView.setImageDrawable(icon)
                view.titleView.text = title
                view.contentDescription = title
            }
            render()
            Logger.info("updated tweak=$TWEAK playing=$hasTrack package=${selected?.packageName}")
        }
    }

    private fun appIcon(packageName: String): Drawable? =
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        } catch (e: LinkageError) {
            null
        }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        closing.removeAll { !it.thread.isAlive }
        closing.add(this)
        worker.post {
            isolateChipCallback(TWEAK, "close") {
                manager.removeOnActiveSessionsChangedListener(listener)
            }
            isolateChipCallback(TWEAK, "close") { unregisterAll() }
            thread.quitSafely()
        }
    }

    fun awaitStopped() {
        thread.join(TimeUnit.SECONDS.toMillis(TEARDOWN_SECONDS))
        check(!thread.isAlive) { "NowPlayingChip worker did not stop" }
    }
}

private fun XposedModule.installNowPlayingChip(cl: ClassLoader): Boolean {
    val bindings =
        resolving { NowPlayingBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} " +
                    "build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    val tintMembers = bindings.tint.joinToString { it.signature() }
    Logger.info(
        "resolved tweak=$TWEAK members=${bindings.inflate.signature()},$tintMembers," +
            "${bindings.shade.signature()}",
    )
    hook(bindings.inflate).intercept { chain ->
        val result = chain.proceed()
        isolateChipCallback(TWEAK, "inflate") {
            val view = chain.thisObject as ViewGroup
            postUi { attach(view) }
        }
        result
    }
    installStatusBarTint(bindings.tint, TWEAK) { color ->
        postUi {
            tint = color
            applyTint()
        }
    }
    hook(bindings.shade).intercept { chain ->
        val result = chain.proceed()
        isolateChipCallback(TWEAK, "shade") {
            val expanded = chain.getArg(0) as Int != 0
            postUi {
                shadeExpanded = expanded
                render()
            }
        }
        result
    }
    postUi {
        if (bar == null) findLiveStatusBar(bindings.inflate.declaringClass)?.let(::attach)
    }
    return true
}
