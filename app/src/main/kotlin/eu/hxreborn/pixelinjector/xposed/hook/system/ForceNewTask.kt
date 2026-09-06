package eu.hxreborn.pixelinjector.xposed.hook.system

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.IBinder
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.util.optionalField
import eu.hxreborn.pixelinjector.util.optionalMethod
import eu.hxreborn.pixelinjector.util.reason
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethods
import eu.hxreborn.pixelinjector.util.signature
import eu.hxreborn.pixelinjector.xposed.Logger
import eu.hxreborn.pixelinjector.xposed.Switch
import eu.hxreborn.pixelinjector.xposed.Target
import eu.hxreborn.pixelinjector.xposed.Tweak
import eu.hxreborn.pixelinjector.xposed.Value
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

private const val TWEAK = "ForceNewTask"
private const val STARTER = "com.android.server.wm.ActivityStarter"
private const val REQUEST = "com.android.server.wm.ActivityStarter\$Request"
private const val RECORD = "com.android.server.wm.ActivityRecord"

private val switch = Switch(Prefs.NEW_TASK)
private val rules = Value(Prefs.NEW_TASK_RULES) { parseNewTaskRules(it) }

internal val forceNewTask =
    Tweak(
        key = TWEAK,
        targets = setOf(Target.SYSTEM),
        prefs = listOf(switch, rules),
        install = XposedModule::installForceNewTask,
    )

private class StarterBindings(
    val executeRequest: List<Method>,
    val intent: Field,
    val callingPackage: Field,
    val activityInfo: Field,
    val requestCode: Field,
    val resultTo: Field,
    val isInAnyTask: Method?,
    val recordInfo: Field?,
) {
    companion object {
        fun resolve(cl: ClassLoader): StarterBindings {
            val starter = cl.loadClass(STARTER)
            val request = cl.loadClass(REQUEST)
            val record = cl.loadClass(RECORD)
            val isInAnyTask = record.optionalMethod("isInAnyTask", 1)
            val recordInfo = record.optionalField("info")
            if (isInAnyTask == null) {
                Logger.warn(
                    "optional member absent tweak=$TWEAK member=ActivityRecord#isInAnyTask reason=no-method",
                )
            }
            if (recordInfo == null) {
                Logger.warn(
                    "optional member absent tweak=$TWEAK member=ActivityRecord#info reason=no-field",
                )
            }
            return StarterBindings(
                executeRequest = starter.requiredMethods("executeRequest"),
                intent = request.requiredField("intent"),
                callingPackage = request.requiredField("callingPackage"),
                activityInfo = request.requiredField("activityInfo"),
                requestCode = request.requiredField("requestCode"),
                resultTo = request.requiredField("resultTo"),
                isInAnyTask = isInAnyTask,
                recordInfo = recordInfo,
            )
        }
    }

    fun apply(request: Any) {
        val intent = intent.get(request) as Intent? ?: return
        if (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0) return
        val source = callingPackage.get(request) as String? ?: return
        val target = activityInfo.get(request) as ActivityInfo? ?: return
        val resultTo = resultTo.get(request) as IBinder?
        val hasResult = requestCode.getInt(request) >= 0 && resultTo != null
        val sourceComponent = componentOf(resultTo)
        val rule =
            rules.value.firstOrNull {
                it.matches(
                    source,
                    sourceComponent,
                    target.packageName,
                    target.name,
                )
            }
                ?: return
        if (hasResult && !rule.ignoreResult) return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (rule.newDocument) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        Logger.debug { "new task source=$source target=${target.packageName}/${target.name}" }
    }

    private fun componentOf(resultTo: IBinder?): String? {
        if (isInAnyTask == null || recordInfo == null || resultTo == null) return null
        val record = isInAnyTask.invoke(null, resultTo) ?: return null
        return (recordInfo.get(record) as ActivityInfo?)?.name
    }
}

private fun XposedModule.installForceNewTask(cl: ClassLoader): Boolean {
    val b =
        runCatching { StarterBindings.resolve(cl) }.getOrElse {
            Logger.error(
                "target not found tweak=$TWEAK member=${it.message} build=${Build.ID} reason=${it.reason()}",
            )
            return false
        }
    Logger.info(
        "resolved tweak=$TWEAK members=${b.executeRequest.joinToString(",") { it.signature() }}",
    )
    for (method in b.executeRequest) {
        hook(method).intercept { chain ->
            if (switch.enabled && rules.value.isNotEmpty()) {
                runCatching { b.apply(chain.getArg(0)) }
                    .onFailure {
                        Logger.error("apply failed tweak=$TWEAK reason=${it.message}", it)
                    }
            }
            chain.proceed()
        }
    }
    return true
}
