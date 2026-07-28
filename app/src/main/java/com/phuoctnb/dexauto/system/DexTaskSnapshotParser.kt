package com.phuoctnb.dexauto.system

import android.graphics.Rect
import com.phuoctnb.dexauto.data.DexSessionApp

class DexTaskSnapshotParser {
    fun parse(
        output: String,
        targetDisplayId: Int,
        excludedPackage: String
    ): List<DexSessionApp> = parseRecords(output, targetDisplayId, excludedPackage).map {
        DexSessionApp(
            packageName = it.packageName,
            bounds = Rect(it.left, it.top, it.right, it.bottom)
        )
    }

    internal fun parseRecords(
        output: String,
        targetDisplayId: Int,
        excludedPackage: String
    ): List<ParsedTask> {
        val tasks = mutableListOf<ParsedTask>()
        var currentDisplayId: Int? = null
        var task: MutableTask? = null

        fun flushTask() {
            val candidate = task ?: return
            val bounds = candidate.bounds
            val packageName = candidate.packageName
            if (
                candidate.displayId == targetDisplayId &&
                candidate.visible &&
                candidate.standardTask != false &&
                bounds != null &&
                bounds.right > bounds.left &&
                bounds.bottom > bounds.top &&
                !packageName.isNullOrBlank() &&
                packageName != excludedPackage &&
                packageName !in SYSTEM_PACKAGES
            ) {
                tasks += ParsedTask(
                    packageName,
                    bounds.left,
                    bounds.top,
                    bounds.right,
                    bounds.bottom
                )
            }
        }

        output.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            DISPLAY_HEADER.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                currentDisplayId = it
            }

            if (isTaskHeader(rawLine)) {
                flushTask()
                task = MutableTask(
                    displayId = displayIdFrom(line) ?: currentDisplayId,
                    standardTask = taskTypeFrom(line)
                )
            }

            val current = task ?: return@forEach
            if (current.packageName == null) {
                packageFrom(line)?.let { current.packageName = it }
            }
            if (current.bounds == null && TASK_BOUNDS.matches(rawLine)) {
                boundsFrom(line)?.let { current.bounds = it }
            }
            if (VISIBLE_TRUE.containsMatchIn(line)) {
                current.visible = true
            }
        }
        flushTask()

        return tasks
            .asReversed()
            .distinctBy { it.packageName }
            .asReversed()
    }

    private fun isTaskHeader(rawLine: String): Boolean {
        return TASK_BRACED.matches(rawLine) || TASK_PLAIN.containsMatchIn(rawLine.trim())
    }

    private fun displayIdFrom(line: String): Int? {
        return DISPLAY_ID.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun taskTypeFrom(line: String): Boolean? {
        val type = TASK_TYPE.find(line)?.groupValues?.getOrNull(1) ?: return null
        return type.equals("standard", ignoreCase = true)
    }

    private fun packageFrom(line: String): String? {
        return AFFINITY_PACKAGE.find(line)?.groupValues?.getOrNull(1)
            ?: COMPONENT_PACKAGE.find(line)?.groupValues?.getOrNull(1)
    }

    private fun boundsFrom(line: String): ParsedBounds? {
        val match = RECT_BOUNDS.find(line) ?: BRACKET_BOUNDS.find(line) ?: return null
        val values = match.groupValues.drop(1).mapNotNull(String::toIntOrNull)
        if (values.size != 4) return null
        return ParsedBounds(values[0], values[1], values[2], values[3])
    }

    private data class MutableTask(
        var displayId: Int?,
        val standardTask: Boolean?,
        var packageName: String? = null,
        var bounds: ParsedBounds? = null,
        var visible: Boolean = false
    )

    internal data class ParsedTask(
        val packageName: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private data class ParsedBounds(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private companion object {
        val DISPLAY_HEADER = Regex("""Display\s+#?(\d+)""", RegexOption.IGNORE_CASE)
        val DISPLAY_ID = Regex("""\bdisplayId=(\d+)""")
        val TASK_TYPE = Regex("""\btype=([A-Za-z]+)""")
        val TASK_BRACED = Regex("""^\s{2}\*\s+Task\{.*$""")
        val TASK_PLAIN = Regex("""^TASK\s+(?:id=)?\d+""", RegexOption.IGNORE_CASE)
        val TASK_BOUNDS = Regex("""^\s{2,4}(?:mBounds=Rect|bounds=).*$""")
        val AFFINITY_PACKAGE = Regex(
            """\bA=(?:\d+:)?([A-Za-z_][A-Za-z0-9._]*)"""
        )
        val COMPONENT_PACKAGE = Regex(
            """(?:ActivityRecord\{.*?\s|ACTIVITY\s+|realActivity=ComponentInfo\{)([A-Za-z0-9_][A-Za-z0-9._]*)/"""
        )
        val RECT_BOUNDS = Regex(
            """\bmBounds=Rect\((-?\d+),\s*(-?\d+)\s*-\s*(-?\d+),\s*(-?\d+)\)"""
        )
        val BRACKET_BOUNDS = Regex(
            """\bbounds=\[(-?\d+),\s*(-?\d+)\]\[(-?\d+),\s*(-?\d+)\]"""
        )
        val VISIBLE_TRUE = Regex("""\b(?:visible|visibleRequested|isVisible)=true\b""")
        val SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.sec.android.app.launcher"
        )
    }
}
