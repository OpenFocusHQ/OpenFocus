package com.openfocushq.openfocus.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.launch
import com.openfocushq.openfocus.OpenFocusApp
import com.openfocushq.openfocus.core.Days
import com.openfocushq.openfocus.game.EventTypes
import java.time.LocalTime

/**
 * Watches foreground app changes and asks [BlockPolicy] whether to intervene. All decisions are
 * local; no event content is stored or transmitted.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private val container by lazy { (application as OpenFocusApp).container }
    private val handler = Handler(Looper.getMainLooper())

    private var exempt: Set<String> = emptySet()
    private var exemptRefreshedAt = 0L
    private var currentPkg: String? = null
    private var lastBlockPkg: String? = null
    private var lastBlockAt = 0L
    private var lastShortsCheckAt = 0L
    private var lastShortsLogAt = 0L

    private val recheck = object : Runnable {
        override fun run() {
            currentPkg?.let { evaluate(it) }
            handler.postDelayed(this, RECHECK_MS)
        }
    }

    /** Whether screen-content events are currently subscribed (only inside enabled video apps). */
    private var contentEvents = true // config may differ from the runtime mask until the first update

    override fun onServiceConnected() {
        super.onServiceConnected()
        running = true
        watchContentFor(null)
        refreshExempt(force = true)
        handler.postDelayed(recheck, RECHECK_MS)
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        refreshExempt()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pkg != currentPkg) {
                currentPkg = pkg
                watchContentFor(pkg)
                if (evaluate(pkg)) return
            }
        }
        checkShorts(pkg)
    }

    /**
     * Least privilege: subscribe to screen-content events only while an enabled short-video app is
     * in front. Everywhere else the Shield only learns which app is open.
     */
    private fun watchContentFor(pkg: String?) {
        val want = ShortsDetector.needsContentEvents(pkg, container.settings.value.shorts)
        if (want == contentEvents) return
        contentEvents = want
        serviceInfo = serviceInfo?.apply { eventTypes = ShortsDetector.eventMask(want) } ?: return
    }

    /** @return true if the app was blocked. */
    private fun evaluate(pkg: String): Boolean {
        val config = container.blocks.config.value
        val now = System.currentTimeMillis()
        val decision = BlockPolicy.decide(
            pkg = pkg,
            config = config,
            nowMillis = now,
            epochDay = Days.today(),
            minuteOfDay = LocalTime.now().let { it.hour * 60 + it.minute },
            exempt = exempt,
            usageMinutesToday = { container.usage.todayMinutes(it) },
        ) ?: return false
        if (pkg == lastBlockPkg && now - lastBlockAt < 1_500) return true
        lastBlockPkg = pkg
        lastBlockAt = now
        // Ascent-style: don't kick the user out. The pause screen covers the app; the user decides.
        launchBlockScreen(pkg, decision)
        return true
    }

    private fun checkShorts(pkg: String) {
        val settings = container.settings.value
        if (settings.shieldPaused && settings.activeFocus == null) return
        val rule = ShortsDetector.ruleFor(pkg, settings.shorts) ?: return
        val now = System.currentTimeMillis()
        if (now - lastShortsCheckAt < 300) return
        lastShortsCheckAt = now
        val detected = runCatching { ShortsDetector.isShortsOnScreen(rule, rootInActiveWindow) }.getOrDefault(false)
        if (!detected) return
        if (now - lastBlockAt < 1_200) return
        lastBlockAt = now
        lastBlockPkg = pkg
        // In-app feeds (Shorts, Reels): step back out of the feed. Whole-app feeds (TikTok): leave the
        // app where it is and let the pause screen offer "Close app".
        if (!rule.wholeApp) performGlobalAction(GLOBAL_ACTION_BACK)
        if (now - lastShortsLogAt > 5_000) {
            lastShortsLogAt = now
            container.appScope.launch { container.game.apply(EventTypes.SHORTS_BLOCKED, pkg, emit = false) }
        }
        launchBlockScreen(
            pkg,
            BlockDecision(BlockReason.SHORTS, rule.platform.label, canPay = false, backToApp = !rule.wholeApp),
        )
    }

    private fun launchBlockScreen(pkg: String, decision: BlockDecision) {
        val intent = Intent(this, BlockActivity::class.java)
            // No window animation: the pause screen fades itself in over the (still visible) app.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
            .putExtra(BlockActivity.EXTRA_REASON, decision.reason.name)
            .putExtra(BlockActivity.EXTRA_DETAIL, decision.detail)
            .putExtra(BlockActivity.EXTRA_GATE, decision.gate.name)
            .putExtra(BlockActivity.EXTRA_WAIT_SECONDS, decision.waitSeconds)
            .putExtra(BlockActivity.EXTRA_ALLOW_MINUTES, decision.allowMinutes)
            .putExtra(BlockActivity.EXTRA_CAN_PAY, decision.canPay)
            .putExtra(BlockActivity.EXTRA_BACK_TO_APP, decision.backToApp)
        runCatching { startActivity(intent) }
    }

    private fun refreshExempt(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (force || now - exemptRefreshedAt > 5 * 60_000) {
            exempt = InstalledApps.exemptPackages(this)
            exemptRefreshedAt = now
        }
    }

    companion object {
        private const val RECHECK_MS = 15_000L
        /** Best-effort flag for the UI; the authoritative check is [isEnabled]. */
        @Volatile var running = false

        fun isEnabled(context: android.content.Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = "${context.packageName}/${BlockerAccessibilityService::class.java.name}"
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
