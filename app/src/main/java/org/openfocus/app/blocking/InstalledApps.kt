package org.openfocus.app.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

data class AppInfo(val packageName: String, val label: String, val icon: Drawable?)

object InstalledApps {
    fun launchable(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                runCatching {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    AppInfo(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai))
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Packages that must never be blocked, so the user can always get home, call or type. */
    fun exemptPackages(context: Context): Set<String> {
        val pm = context.packageManager
        val set = mutableSetOf(
            context.packageName, "com.android.systemui", "com.android.settings", "android",
            "com.android.phone", "com.google.android.dialer", "com.android.dialer",
            "com.android.emergency", "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        )
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY).forEach { set += it.activityInfo.packageName }
        val dial = Intent(Intent.ACTION_DIAL)
        pm.queryIntentActivities(dial, 0).forEach { set += it.activityInfo.packageName }
        runCatching {
            context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.forEach { set += it.packageName }
        }
        runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.let { set += it }
        }
        return set
    }
}
