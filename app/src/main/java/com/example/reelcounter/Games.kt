package com.example.reelcounter

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build

// Which apps count as games in Kids mode, and the shared "umbrella" limit for all of them.
// An app counts as a game when Android marks it as one, or when it was installed recently
// and the parent has not allowed it. That second rule covers a game installed today.
object Games {
    const val STORE = "com.android.vending"

    private fun p(c: Context) = c.getSharedPreferences("reels_v2", Context.MODE_PRIVATE)

    // ---- Settings ----
    fun umbrellaMin(c: Context): Int = p(c).getInt("kids_game_min", 5)
    fun setUmbrellaMin(c: Context, v: Int) {
        p(c).edit().putInt("kids_game_min", v).apply()
    }

    fun newAppRule(c: Context): Boolean = p(c).getBoolean("kids_new_apps", true)
    fun setNewAppRule(c: Context, v: Boolean) {
        p(c).edit().putBoolean("kids_new_apps", v).apply()
        cache.clear()
    }

    fun newAppDays(c: Context): Int = p(c).getInt("kids_new_days", 7)
    fun setNewAppDays(c: Context, v: Int) {
        p(c).edit().putInt("kids_new_days", v).apply()
        cache.clear()
    }

    fun storeGuard(c: Context): Boolean = p(c).getBoolean("kids_store_guard", true)
    fun setStoreGuard(c: Context, v: Boolean) {
        p(c).edit().putBoolean("kids_store_guard", v).apply()
    }

    // ---- The allowed list: apps that stay free of the games limit ----
    fun allowed(c: Context): Set<String> =
        (p(c).getString("kids_allowed_games", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet()

    fun setAllowed(c: Context, pkg: String, on: Boolean) {
        val set = allowed(c).toMutableSet()
        if (on) set.add(pkg) else set.remove(pkg)
        p(c).edit().putString("kids_allowed_games", set.joinToString(",")).apply()
        cache.clear()
    }

    // ---- What counts as a game ----
    private val cache = HashMap<String, Pair<Long, Boolean>>()

    private fun isUserApp(ai: ApplicationInfo): Boolean =
        (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0

    @Suppress("DEPRECATION")
    private fun marked(ai: ApplicationInfo): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && ai.category == ApplicationInfo.CATEGORY_GAME) return true
        return (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
    }

    private fun recent(c: Context, pkg: String): Boolean {
        return try {
            val born = c.packageManager.getPackageInfo(pkg, 0).firstInstallTime
            born >= System.currentTimeMillis() - newAppDays(c) * 86400000L
        } catch (e: Exception) {
            false
        }
    }

    // True if the app could count as a game, ignoring the allowed list (for the settings list).
    fun isCandidate(c: Context, pkg: String): Boolean {
        if (pkg == c.packageName || pkg == STORE) return false
        return try {
            val ai = c.packageManager.getApplicationInfo(pkg, 0)
            if (!isUserApp(ai) && !marked(ai)) return false
            marked(ai) || (newAppRule(c) && isUserApp(ai) && recent(c, pkg))
        } catch (e: Exception) {
            false
        }
    }

    // True if time in this app is counted against the games limit (remembered for a minute).
    fun counts(c: Context, pkg: String): Boolean {
        val now = System.currentTimeMillis()
        val hit = cache[pkg]
        if (hit != null && now - hit.first < 60000L) return hit.second
        val r = !allowed(c).contains(pkg) && isCandidate(c, pkg)
        cache[pkg] = Pair(now, r)
        return r
    }

    // Installed apps that count as games, for the settings list.
    fun candidates(c: Context): List<Pair<String, String>> {
        val pm = c.packageManager
        val i = Intent(Intent.ACTION_MAIN)
        i.addCategory(Intent.CATEGORY_LAUNCHER)
        val out = ArrayList<Pair<String, String>>()
        try {
            for (ri in pm.queryIntentActivities(i, 0)) {
                val pkg = ri.activityInfo.packageName
                if (isCandidate(c, pkg)) out.add(Pair(pkg, ri.loadLabel(pm).toString()))
            }
        } catch (e: Exception) {
        }
        return out.distinctBy { it.first }.sortedBy { it.second.lowercase() }.take(60)
    }
}
