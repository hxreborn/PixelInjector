package eu.hxreborn.pixelinjector.xposed.hook.firefox

import android.content.Context
import android.os.Build
import eu.hxreborn.pixelinjector.Target
import eu.hxreborn.pixelinjector.betterfox.BETTERFOX_ASSET
import eu.hxreborn.pixelinjector.betterfox.BetterfoxList
import eu.hxreborn.pixelinjector.betterfox.chooseList
import eu.hxreborn.pixelinjector.betterfox.decodeOverrides
import eu.hxreborn.pixelinjector.betterfox.formatPrefValue
import eu.hxreborn.pixelinjector.betterfox.resolveBetterfox
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
import eu.hxreborn.pixelinjector.xposed.module
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.zip.ZipFile

private const val TWEAK = "FirefoxBetterfox"
private const val RUNTIME_SETTINGS = "org.mozilla.geckoview.RuntimeSettings"
private const val GECKO_RUNTIME = "org.mozilla.geckoview.GeckoRuntime"
private const val GECKO_RUNTIME_SETTINGS = "org.mozilla.geckoview.GeckoRuntimeSettings"

private val bundled: BetterfoxList? by lazy { loadBundled() }

private val switch = Switch(Prefs.FIREFOX_BETTERFOX) { refresh() }
private val offGroups = Value(Prefs.FIREFOX_BETTERFOX_OFF_GROUPS, { refresh() }) { it }
private val overrides =
    Value(Prefs.FIREFOX_BETTERFOX_OVERRIDES, { refresh() }) { decodeOverrides(it) }
private val fetchedList =
    Value(Prefs.FIREFOX_BETTERFOX_LIST, { refresh() }) { BetterfoxList.parseOrNull(it) }

private val reset = Value(Prefs.FIREFOX_BETTERFOX_RESET) { it }

@Volatile private var startupPrefs: Map<String, Any> = emptyMap()

@Volatile private var appliedPrefs: Map<String, Any> = emptyMap()

internal val firefoxBetterfox =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.FIREFOX),
        prefs = listOf(switch, offGroups, overrides, fetchedList, reset),
        install = XposedModule::installFirefoxBetterfox,
    )

private class BetterfoxBindings(
    val getPrefsMap: Method,
    val settings: Class<*>,
    val init: Method,
) {
    companion object {
        fun resolve(cl: ClassLoader) =
            BetterfoxBindings(
                getPrefsMap = cl.loadClass(RUNTIME_SETTINGS).requiredMethod("getPrefsMap"),
                settings = cl.loadClass(GECKO_RUNTIME_SETTINGS),
                init =
                    cl.loadClass(GECKO_RUNTIME).requiredMethod(
                        "init",
                        Context::class.java,
                        cl.loadClass(GECKO_RUNTIME_SETTINGS),
                    ),
            )
    }
}

private fun loadBundled(): BetterfoxList? =
    resolving {
        ZipFile(module.moduleApplicationInfo.sourceDir).use { zip ->
            val entry = checkNotNull(zip.getEntry("assets/$BETTERFOX_ASSET")) { "no-asset" }
            BetterfoxList.parse(zip.getInputStream(entry).use { it.readBytes().decodeToString() })
        }
    }.getOrElse {
        Logger.error("bundled list failed tweak=$TWEAK reason=${it.message}", it)
        null
    }

private fun refresh() {
    val list = bundled?.let { chooseList(it, fetchedList.value) }
    appliedPrefs =
        if (switch.enabled && list != null) {
            resolveBetterfox(list, offGroups.value, overrides.value)
        } else {
            emptyMap()
        }
}

private fun XposedModule.installFirefoxBetterfox(cl: ClassLoader): Boolean {
    val bindings =
        resolving { BetterfoxBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.member()} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${bindings.getPrefsMap.signature()},${bindings.init.signature()}",
    )
    hook(bindings.init).intercept { chain ->
        val enabled = switch.enabled
        val extra = if (enabled) appliedPrefs else emptyMap()
        val resetRequest = reset.value
        startupPrefs = emptyMap()
        try {
            val context = chain.getArg(0) as Context
            val profileBackedUp =
                prepareBetterfoxProfiles(context.filesDir, extra.keys, resetRequest)
            startupPrefs = if (profileBackedUp) extra else emptyMap()
            Logger.info(
                "profiles prepared tweak=$TWEAK enabled=$enabled reset=$resetRequest profileBackedUp=$profileBackedUp",
            )
        } catch (e: Exception) {
            Logger.error("profiles failed tweak=$TWEAK reason=${e.message}", e)
        } catch (e: LinkageError) {
            Logger.error("profiles failed tweak=$TWEAK reason=${e.message}", e)
        }
        chain.proceed()
    }
    hook(bindings.getPrefsMap).intercept { chain ->
        val enabled = switch.enabled

        @Suppress("UNCHECKED_CAST")
        val stock = chain.proceed() as Map<String, Any?>
        val extra = startupPrefs
        if (!enabled || extra.isEmpty() ||
            !bindings.settings.isInstance(chain.thisObject)
        ) {
            stock
        } else {
            val replaced = extra.keys.filter { it in stock }.joinToString(",")
            val values =
                extra.entries.joinToString(",") { "${it.key}=${formatPrefValue(it.value)}" }
            Logger.info(
                "applied tweak=$TWEAK prefs=${extra.size} replaced=$replaced values=$values",
            )
            LinkedHashMap(stock).apply { putAll(extra) }
        }
    }
    return true
}
