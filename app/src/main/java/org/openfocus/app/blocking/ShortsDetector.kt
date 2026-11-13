package org.openfocus.app.blocking

import android.view.accessibility.AccessibilityNodeInfo
import org.openfocus.app.data.prefs.ShortsPlatform

/**
 * Data-driven rules for recognising short-form video feeds. Apps change their view ids from time
 * to time; contributions updating these lists are very welcome (see CONTRIBUTING.md).
 */
data class ShortsRule(
    val platform: ShortsPlatform,
    /** Fully-qualified view ids, any of which indicates the short-video player is on screen. */
    val viewIds: List<String> = emptyList(),
    /** Content descriptions / texts of a *selected* tab that indicates the feed (lower-case). */
    val selectedTabLabels: List<String> = emptyList(),
    /** If true the whole package is treated as a short-video feed. */
    val wholeApp: Boolean = false,
)

object ShortsDetector {
    val rules: List<ShortsRule> = listOf(
        ShortsRule(
            ShortsPlatform.YOUTUBE,
            viewIds = listOf(
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_player_page_container",
                "com.google.android.youtube:id/reel_watch_player",
                "com.google.android.youtube:id/reel_progress_bar",
            ),
        ),
        ShortsRule(
            ShortsPlatform.INSTAGRAM,
            viewIds = listOf(
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/clips_viewer_container",
                "com.instagram.android:id/clips_video_container",
            ),
        ),
        ShortsRule(ShortsPlatform.TIKTOK, wholeApp = true),
        ShortsRule(ShortsPlatform.TIKTOK_LITE, wholeApp = true),
        ShortsRule(
            ShortsPlatform.SNAPCHAT,
            viewIds = listOf("com.snapchat.android:id/spotlight_container"),
            selectedTabLabels = listOf("spotlight"),
        ),
    )

    private val byPackage = rules.associateBy { it.platform.packageName }

    fun ruleFor(pkg: String, enabled: Set<ShortsPlatform>): ShortsRule? =
        byPackage[pkg]?.takeIf { it.platform in enabled }

    fun isShortsOnScreen(rule: ShortsRule, root: AccessibilityNodeInfo?): Boolean {
        if (rule.wholeApp) return true
        if (root == null) return false
        for (id in rule.viewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.any { it.isVisibleToUser }) return true
        }
        if (rule.selectedTabLabels.isNotEmpty() && hasSelectedTab(root, rule.selectedTabLabels, 0)) return true
        return false
    }

    private fun hasSelectedTab(node: AccessibilityNodeInfo, labels: List<String>, depth: Int): Boolean {
        if (depth > 25) return false
        if (node.isSelected) {
            val label = (node.contentDescription ?: node.text)?.toString()?.lowercase()
            if (label != null && labels.any { label.contains(it) }) return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (hasSelectedTab(child, labels, depth + 1)) return true
        }
        return false
    }
}
