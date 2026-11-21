package eu.hxreborn.pixelinjector.xposed.hook.system

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Handler
import android.view.Display
import eu.hxreborn.pixelinjector.util.optionalField
import eu.hxreborn.pixelinjector.util.orNullIfMissing
import eu.hxreborn.pixelinjector.util.requiredField
import eu.hxreborn.pixelinjector.util.requiredMethod
import eu.hxreborn.pixelinjector.util.resolving
import eu.hxreborn.pixelinjector.xposed.Logger
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap

private const val POLICY_DOZE = 1
private const val POLICY_DIM = 2
private const val POLICY_BRIGHT = 3
private const val MODE_FADE = 2

private class DozeBindings(
    val displayId: Field,
    val request: Field,
    val policy: Field,
    val powerState: Field,
    val colorFade: Field,
    val context: Field,
    val handler: Field,
    val fadeEnabled: Field,
    val animator: Field,
    val displayEnabled: Field,
    val inTransition: Field,
    val stateController: Field,
    val skipTransition: Field?,
    val proximityController: Field,
    val screenOffBecauseOfProximity: Method,
    val screenState: Method,
    val fadeLevel: Method,
    val prepareFade: Method,
    val update: Method,
)

private class DozeTransition {
    var running = false
    var applyBrightness = false
    var waitingRelease = false
    var waitingPermission = false
}

internal fun XposedModule.installCrtDozeEntry(
    cl: ClassLoader,
    enabled: () -> Boolean,
    effectiveDozeState: (Any, Any) -> Int,
    ownsFade: (Any) -> Boolean,
) {
    listOf(
        "com.android.server.display.DisplayPowerController",
        "com.android.server.display.DisplayPowerController2",
    ).forEach { name ->
        val controller =
            try {
                cl.loadClass(name)
            } catch (_: ClassNotFoundException) {
                return@forEach
            }
        installCrtDozeController(cl, controller, enabled, effectiveDozeState, ownsFade)
    }
}

private fun XposedModule.installCrtDozeController(
    cl: ClassLoader,
    controller: Class<*>,
    enabled: () -> Boolean,
    effectiveDozeState: (Any, Any) -> Int,
    ownsFade: (Any) -> Boolean,
) {
    val resolved =
        resolving {
            val request =
                cl.loadClass(
                    "android.hardware.display.DisplayManagerInternal\$DisplayPowerRequest",
                )
            val power = cl.loadClass("com.android.server.display.DisplayPowerState")
            val stateController =
                cl.loadClass(
                    "com.android.server.display.state.DisplayStateController",
                )
            val proximity =
                cl.loadClass(
                    "com.android.server.display.DisplayPowerProximityStateController",
                )
            val int = Int::class.javaPrimitiveType
            val float = Float::class.javaPrimitiveType
            val bool = Boolean::class.javaPrimitiveType
            val stateChange =
                orNullIfMissing {
                    controller.requiredMethod("animateScreenStateChange", int, int, bool)
                }
                    ?: controller.requiredMethod("animateScreenStateChange", int, bool)
            val brightness =
                orNullIfMissing {
                    controller.requiredMethod("animateScreenBrightness", float, float, float, bool)
                }
                    ?: controller.requiredMethod("animateScreenBrightness", float, float, float)
            val update = controller.requiredMethod("updatePowerStateInternal")
            val bindings =
                DozeBindings(
                    controller.requiredField("mDisplayId"),
                    controller.requiredField("mPowerRequest"),
                    request.requiredField("policy"),
                    controller.requiredField("mPowerState"),
                    power.requiredField("mColorFade"),
                    controller.requiredField("mContext"),
                    controller.requiredField("mHandler"),
                    controller.requiredField("mColorFadeEnabled"),
                    controller.requiredField("mColorFadeOffAnimator"),
                    controller.requiredField("mIsEnabled"),
                    controller.requiredField("mIsInTransition"),
                    controller.requiredField("mDisplayStateController"),
                    stateController.optionalField("mShouldSkipScreenOffTransition"),
                    stateController.requiredField("mDisplayPowerProximityStateController"),
                    proximity.requiredMethod("isScreenOffBecauseOfProximity"),
                    power.requiredMethod("getScreenState"),
                    power.requiredMethod("getColorFadeLevel"),
                    power.requiredMethod("prepareColorFade", Context::class.java, int),
                    controller.requiredMethod("sendUpdatePowerState"),
                )
            Triple(bindings, stateChange, Pair(brightness, update))
        }.getOrElse {
            Logger.warn("crt doze unavailable tweak=CrtScreenOff reason=${it.message}")
            return
        }
    val (bindings, stateChange, methods) = resolved
    val transitions = WeakHashMap<Any, DozeTransition>()
    val restore = {
        crtSystemHost?.takeIf { controller.isInstance(it) }?.let { host ->
            coordinationFor(host, bindings)
        }
        Unit
    }
    restoreSystemCoordinations.add(restore)
    restore()
    hook(stateChange).intercept { chain ->
        val controller = chain.thisObject ?: return@intercept chain.proceed()
        val existing = transitions[controller]
        val inProgress =
            existing?.running == true || existing?.applyBrightness == true ||
                existing?.waitingRelease == true || existing?.waitingPermission == true
        if (!enabled() && !inProgress) {
            transitions.remove(controller)
            return@intercept chain.proceed()
        }
        if (!beginCrtDozeWork()) return@intercept chain.proceed()
        val skip =
            try {
                if (bindings.displayId.getInt(controller) != Display.DEFAULT_DISPLAY) {
                    false
                } else {
                    val request =
                        bindings.request.get(controller) ?: error("display power request missing")
                    val policy = bindings.policy.getInt(request)
                    val transition = transitions.getOrPut(controller) { DozeTransition() }
                    val animator = bindings.animator.get(controller) as? ObjectAnimator
                    val coordination =
                        coordinationFor(controller, bindings)
                            ?: error("CRT coordination unavailable")
                    val generation = coordination.armedGeneration()
                    when {
                        policy == POLICY_BRIGHT || policy == POLICY_DIM -> {
                            if (transition.running || transition.waitingRelease ||
                                transition.waitingPermission
                            ) {
                                coordination.failed()
                            }
                            transition.running = false
                            transition.applyBrightness = false
                            transition.waitingRelease = false
                            transition.waitingPermission = false
                            false
                        }

                        transition.running -> {
                            if (policy != POLICY_DOZE || chain.getArg(0) == Display.STATE_OFF ||
                                coordination.isReleased()
                            ) {
                                transition.running = false
                                transition.applyBrightness = false
                                coordination.failed()
                                animator?.cancel()
                                false
                            } else if (animator?.isStarted == true) {
                                true
                            } else {
                                transition.running = false
                                transition.applyBrightness = true
                                true
                            }
                        }

                        transition.waitingRelease -> {
                            if (coordination.isReleased() || policy != POLICY_DOZE ||
                                chain.getArg(0) == Display.STATE_OFF
                            ) {
                                if (!coordination.isReleased()) coordination.failed()
                                transition.waitingRelease = false
                                coordination.reset()
                                false
                            } else {
                                true
                            }
                        }

                        transition.waitingPermission &&
                            (policy != POLICY_DOZE || chain.getArg(0) == Display.STATE_OFF) -> {
                            transition.waitingPermission = false
                            coordination.failed()
                            false
                        }

                        policy == POLICY_DOZE && generation != 0L -> {
                            val target = chain.getArg(0) as Int
                            val stateController =
                                bindings.stateController.get(controller)
                                    ?: error("display state controller missing")
                            val dozeState = effectiveDozeState(stateController, request)
                            val power =
                                bindings.powerState.get(controller)
                                    ?: error("display power state missing")
                            val canAnimate =
                                animator != null &&
                                    isCrtDozeTarget(
                                        target = target,
                                        dozeState = dozeState,
                                        displayEnabled =
                                            bindings.displayEnabled.getBoolean(
                                                controller,
                                            ),
                                        inTransition =
                                            bindings.inTransition.getBoolean(
                                                controller,
                                            ),
                                        skipTransition =
                                            bindings.skipTransition?.getBoolean(stateController) ==
                                                true,
                                        proximity =
                                            bindings.screenOffBecauseOfProximity.invoke(
                                                bindings.proximityController.get(stateController),
                                            ) == true,
                                    ) &&
                                    bindings.fadeEnabled.getBoolean(controller) &&
                                    bindings.screenState.invoke(power) == Display.STATE_ON &&
                                    bindings.fadeLevel.invoke(power) == 1f
                            if (!canAnimate || coordination.armedGeneration() != generation) {
                                transition.waitingPermission = false
                                consumeCrtAwakeState(stateController)
                                coordination.failed()
                                false
                            } else if (!coordination.isGranted(generation)) {
                                transition.waitingPermission = true
                                coordination.requestStart(generation)
                                true
                            } else {
                                transition.waitingPermission = false
                                val wasAwake = consumeCrtAwakeState(stateController)
                                val fade = bindings.colorFade.get(power)
                                if (wasAwake &&
                                    bindings.prepareFade.invoke(
                                        power,
                                        bindings.context.get(controller),
                                        MODE_FADE,
                                    ) ==
                                    true &&
                                    fade != null && ownsFade(fade)
                                ) {
                                    transition.running = true
                                    animator.start()
                                    coordination.started(generation)
                                    true
                                } else {
                                    coordination.failed()
                                    false
                                }
                            }
                        }

                        else -> {
                            if (transition.waitingPermission) coordination.failed()
                            transition.waitingPermission = false
                            false
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.error("crt doze failed tweak=CrtScreenOff reason=${e.message}", e)
                systemCoordination?.failed()
                transitions.remove(controller)
                false
            } catch (e: LinkageError) {
                Logger.error("crt doze failed tweak=CrtScreenOff reason=${e.message}", e)
                systemCoordination?.failed()
                transitions.remove(controller)
                false
            } finally {
                endCrtDozeWork()
            }
        if (skip) null else chain.proceed()
    }
    hook(methods.first).intercept { chain ->
        val transition = transitions[chain.thisObject]
        when {
            transition?.running == true || transition?.waitingPermission == true -> {
                Logger.info("crt aod brightness held tweak=CrtScreenOff target=${chain.getArg(0)}")
                null
            }

            transition?.applyBrightness == true -> {
                val args = chain.args.toTypedArray()
                args[2] = 0f
                Logger.info("crt aod brightness applied tweak=CrtScreenOff target=${args[0]}")
                chain.proceed(args)
            }

            else -> {
                chain.proceed()
            }
        }
    }
    hook(methods.second).intercept { chain ->
        val result = chain.proceed()
        val controller = chain.thisObject
        val transition = transitions[controller]
        if (controller != null && transition?.applyBrightness == true) {
            transition.applyBrightness = false
            transition.waitingRelease = true
            systemCoordination?.finished()
        }
        result
    }
}

internal fun isCrtDozeTarget(
    target: Int,
    dozeState: Int,
    displayEnabled: Boolean,
    inTransition: Boolean,
    skipTransition: Boolean,
    proximity: Boolean,
): Boolean =
    dozeState != Display.STATE_OFF &&
        (
            target == Display.STATE_ON || target == Display.STATE_DOZE ||
                target == Display.STATE_DOZE_SUSPEND
        ) &&
        displayEnabled && !inTransition && !skipTransition && !proximity

private fun coordinationFor(
    controller: Any,
    bindings: DozeBindings,
): CrtSystemCoordination? =
    crtSystemCoordination(controller) {
        CrtSystemCoordination(
            bindings.context.get(controller) as Context,
            bindings.handler.get(controller) as Handler,
            {
                try {
                    bindings.update.invoke(controller)
                } catch (e: Exception) {
                    Logger.error("crt doze update failed tweak=CrtScreenOff reason=${e.message}", e)
                } catch (e: LinkageError) {
                    Logger.error("crt doze update failed tweak=CrtScreenOff reason=${e.message}", e)
                }
            },
        )
    }
