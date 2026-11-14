package eu.hxreborn.pixelinjector.xposed.hook

import android.os.Build
import android.os.SystemClock
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.xposed.Logger
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.result.MethodDataList
import java.lang.reflect.Method

internal object DexKit {
    private val nativeLib: Result<Unit> by lazy {
        try {
            System.loadLibrary("dexkit")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } catch (e: LinkageError) {
            Result.failure(e)
        }
    }

    @Volatile private var bridge: DexKitBridge? = null

    private val found = HashMap<String, List<Method>>()

    fun methods(
        tweak: String,
        cl: ClassLoader,
        query: (DexKitBridge) -> MethodDataList,
    ): List<Method> {
        found[tweak]?.let {
            Logger.info("dexkit tweak=$tweak carried=true")
            return it
        }
        nativeLib.getOrThrow()
        val start = SystemClock.uptimeMillis()
        val live = bridge ?: openBridge(tweak, cl).also { bridge = it }
        val methods = query(live).mapNotNull { methodInstance(it, cl) }
        Logger.info("dexkit tweak=$tweak durationMs=${SystemClock.uptimeMillis() - start}")
        if (methods.isNotEmpty()) found[tweak] = methods
        return methods
    }

    private fun openBridge(
        tweak: String,
        cl: ClassLoader,
    ): DexKitBridge {
        val fromApk = DexKitBridge.create(cl, false)
        if (fromApk.getDexNum() > 0) return fromApk
        val sdk = Build.VERSION.SDK_INT
        if (sdk >= Build.VERSION_CODES.CINNAMON_BUN) {
            Logger.warn("dexkit unavailable tweak=$tweak reason=no-dex-in-apk sdk=$sdk")
            return fromApk
        }
        fromApk.close()
        Logger.info("dexkit tweak=$tweak source=memory reason=no-dex-in-apk")
        return DexKitBridge.create(cl, true)
    }

    private fun methodInstance(
        data: MethodData,
        cl: ClassLoader,
    ): Method? =
        resolving { data.getMethodInstance(cl) }
            .onFailure {
                Logger.debug { "dexkit unresolved method=${data.descriptor} reason=${it.message}" }
            }.getOrNull()

    fun save(): HashMap<String, List<Method>> = HashMap(found)

    fun restore(saved: Any?) {
        (saved as? Map<*, *>)?.forEach { (tweak, methods) ->
            if (tweak is String &&
                methods is List<*>
            ) {
                found[tweak] = methods.filterIsInstance<Method>()
            }
        }
    }

    fun release() {
        val live = bridge
        bridge = null
        live?.close()
    }
}
