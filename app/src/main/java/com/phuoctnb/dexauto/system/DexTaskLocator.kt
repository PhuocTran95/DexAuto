package com.phuoctnb.dexauto.system

data class DexTaskState(
    val taskIdsByPackage: Map<String, Int>
)

class DexTaskLocator {
    fun parse(output: String, targetDisplayId: Int): DexTaskState {
        var currentDisplayId: Int? = null
        var inTaskListing = false
        val taskIdsByPackage = linkedMapOf<String, Int>()

        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            DISPLAY_HEADER.matchEntire(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                currentDisplayId = it
                inTaskListing = true
            }
            if (TASK_LISTING_END.matches(line)) {
                inTaskListing = false
            }
            if (currentDisplayId != targetDisplayId || !inTaskListing) return@forEach

            if (!TASK_HEADER.containsMatchIn(line)) return@forEach

            val taskId = TASK_ID.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return@forEach
            val taskType = TASK_TYPE.find(line)?.groupValues?.getOrNull(1)
            if (!taskType.equals("standard", ignoreCase = true)) return@forEach

            val packageName = AFFINITY_PACKAGE.find(line)?.groupValues?.getOrNull(1)
                ?: return@forEach
            taskIdsByPackage.putIfAbsent(packageName, taskId)
        }

        return DexTaskState(taskIdsByPackage)
    }

    private companion object {
        val DISPLAY_HEADER = Regex("""Display\s+#?(\d+).*""", RegexOption.IGNORE_CASE)
        val TASK_LISTING_END = Regex(
            """Resumed activities in task display areas.*""",
            RegexOption.IGNORE_CASE
        )
        val TASK_HEADER = Regex("""^\*\s+Task\{""")
        val TASK_ID = Regex("""\s#(\d+)\b""")
        val TASK_TYPE = Regex("""\btype=([A-Za-z-]+)""")
        val AFFINITY_PACKAGE = Regex(
            """\bA=(?:\d+:)?([A-Za-z_][A-Za-z0-9._]*)"""
        )
    }
}
