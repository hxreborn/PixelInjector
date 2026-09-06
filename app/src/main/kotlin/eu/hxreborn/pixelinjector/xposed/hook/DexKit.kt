package eu.hxreborn.pixelinjector.xposed.hook

import android.os.SystemClock
import eu.hxreborn.pixelinjector.xposed.Logger
import org.luckypray.dexkit.DexKitBridge

internal object DexKit {
    private val nativeLib: Result<Unit> by lazy { runCatching { System.loadLibrary("dexkit") } }

    @Volatile private var bridge: DexKitBridge? = null

    fun <T> query(
        tweak: String,
        cl: ClassLoader,
        block: (DexKitBridge) -> T,
    ): T {
        nativeLib.getOrThrow()
        val start = SystemClock.uptimeMillis()
        val live = bridge ?: DexKitBridge.create(cl, true).also { bridge = it }
        return block(live).also {
            Logger.info("dexkit tweak=$tweak durationMs=${SystemClock.uptimeMillis() - start}")
        }
    }

    fun release() {
        bridge?.close()
        bridge = null
    }
}
