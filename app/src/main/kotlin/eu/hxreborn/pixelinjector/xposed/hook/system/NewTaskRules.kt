package eu.hxreborn.pixelinjector.xposed.hook.system

internal data class NewTaskRule(
    val sourcePackage: String,
    val sourceComponent: String,
    val targetPackage: String,
    val targetComponent: String,
    val ignoreResult: Boolean,
    val newDocument: Boolean,
) {
    fun matches(
        source: String,
        sourceComponent: String?,
        target: String,
        targetComponent: String,
    ): Boolean {
        val selfStart = source == target
        val sourceOk = sourcePackage == source || (sourcePackage == "*" && !selfStart)
        val targetOk = targetPackage == target || (targetPackage == "*" && !selfStart)
        return sourceOk &&
            targetOk &&
            (
                this.sourceComponent.isEmpty() ||
                    matchSimple(
                        this.sourceComponent,
                        sourceComponent,
                    )
            ) &&
            (this.targetComponent.isEmpty() || matchSimple(this.targetComponent, targetComponent))
    }
}

internal fun matchSimple(
    pattern: String,
    value: String?,
): Boolean {
    if (value == null) return false
    return if (pattern.endsWith("*")) value.startsWith(pattern.dropLast(1)) else pattern == value
}

private fun splitEndpoint(text: String): Pair<String, String> {
    val pkg = text.substringBefore('/')
    val component = text.substringAfter('/', "")
    return pkg to if (component.startsWith(".")) pkg + component else component
}

internal fun parseNewTaskRules(text: String): List<NewTaskRule> =
    text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val parts = line.split(':')
            if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return@mapNotNull null
            val (sourcePackage, sourceComponent) = splitEndpoint(parts[0])
            val (targetPackage, targetComponent) = splitEndpoint(parts[1])
            val options =
                parts
                    .getOrNull(2)
                    ?.split(',')
                    ?.map { it.trim() }
                    .orEmpty()
            NewTaskRule(
                sourcePackage = sourcePackage,
                sourceComponent = sourceComponent,
                targetPackage = targetPackage,
                targetComponent = targetComponent,
                ignoreResult = "ir" in options,
                newDocument = "nd" in options,
            )
        }.toList()
