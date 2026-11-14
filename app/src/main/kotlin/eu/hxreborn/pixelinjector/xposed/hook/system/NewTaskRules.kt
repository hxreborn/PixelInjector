package eu.hxreborn.pixelinjector.xposed.hook.system

import eu.hxreborn.pixelinjector.xposed.Logger

private const val MAX_RULE_TEXT_LENGTH = 65536
private const val MAX_RULE_LENGTH = 1024
private const val MAX_RULE_COUNT = 256

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
        return sourceOk && targetOk &&
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

internal fun parseNewTaskRules(text: String): List<NewTaskRule> {
    val kept =
        if (text.length <= MAX_RULE_TEXT_LENGTH) {
            text
        } else {
            val prefix = text.take(MAX_RULE_TEXT_LENGTH)
            prefix.substring(0, prefix.indexOfLast { it == '\n' || it == '\r' }.coerceAtLeast(0))
        }
    val lines = kept.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    val shortLines = lines.filter { it.length <= MAX_RULE_LENGTH }
    val rules = shortLines.take(MAX_RULE_COUNT).mapNotNull(::parseNewTaskRule)
    val droppedChars = text.length - kept.length
    val droppedLongLines = lines.size - shortLines.size
    val droppedOverCount = (shortLines.size - MAX_RULE_COUNT).coerceAtLeast(0)
    if (droppedChars + droppedLongLines + droppedOverCount > 0) {
        Logger.warn(
            "new task rules truncated tweak=ForceNewTask droppedChars=$droppedChars " +
                "droppedLongLines=$droppedLongLines droppedLinesOverCount=$droppedOverCount",
        )
    }
    return rules
}

private fun parseNewTaskRule(line: String): NewTaskRule? {
    val parts = line.split(':')
    if (parts.size < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null
    val (sourcePackage, sourceComponent) = splitEndpoint(parts[0])
    val (targetPackage, targetComponent) = splitEndpoint(parts[1])
    val options =
        parts
            .getOrNull(2)
            ?.split(',')
            ?.map { it.trim() }
            .orEmpty()
    return NewTaskRule(
        sourcePackage = sourcePackage,
        sourceComponent = sourceComponent,
        targetPackage = targetPackage,
        targetComponent = targetComponent,
        ignoreResult = "ir" in options,
        newDocument = "nd" in options,
    )
}
