package dev.ghazi.igfocus.service

import android.content.Context
import android.content.pm.PackageManager
import dev.ghazi.igfocus.rules.InstagramRules

object InstagramInfo {
    /** Instagram's version name, or null if it is not installed. */
    fun installedVersion(context: Context): String? = try {
        context.packageManager.getPackageInfo(InstagramRules.PACKAGE, 0).versionName
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}
