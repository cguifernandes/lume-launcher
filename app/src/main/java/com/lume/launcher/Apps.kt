package com.lume.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager

class App(val label: String, val info: ActivityInfo) {
    val pkg: String get() = info.packageName
    val component get() = ComponentName(info.packageName, info.name)
}

/** Apps de TV (LEANBACK) primeiro, depois os de celular; um por pacote, sem o próprio launcher. */
fun loadApps(pm: PackageManager, self: String): List<App> {
    val byPkg = LinkedHashMap<String, App>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
            val ai = ri.activityInfo
            if (ai.packageName == self || ai.packageName in byPkg) continue
            byPkg[ai.packageName] = App(ri.loadLabel(pm).toString(), ai)
        }
    }
    return byPkg.values.sortedBy { it.label.lowercase() }
}
