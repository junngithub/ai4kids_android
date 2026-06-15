package sg.com.tertiarycourses.ai4kids.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import org.json.JSONObject
import sg.com.tertiarycourses.ai4kids.model.Activity

/**
 * App-wide progress: stars earned per activity, persisted locally to
 * `SharedPreferences` (JSON). No accounts, no network, no personal data — just
 * a fun running tally so kids see their stars grow. Android port of the iOS
 * `ProgressStore`.
 */
class ProgressStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Stars earned per activity id, exposed as Compose state. */
    private var starsByActivity by mutableStateOf<Map<String, Int>>(emptyMap())

    init {
        starsByActivity = load()
    }

    /** Total stars across every activity. */
    val totalStars: Int
        get() = starsByActivity.values.sum()

    /** Stars earned in a single activity. */
    fun stars(activity: Activity): Int = starsByActivity[activity.id] ?: 0

    /** Award [count] stars for an activity and persist immediately. */
    fun award(count: Int, activity: Activity) {
        if (count <= 0) return
        val updated = starsByActivity.toMutableMap()
        updated[activity.id] = (updated[activity.id] ?: 0) + count
        starsByActivity = updated
        persist()
    }

    /** Reset all progress (used by the Parents' Corner). */
    fun resetAll() {
        starsByActivity = emptyMap()
        persist()
    }

    private fun load(): Map<String, Int> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key -> put(key, json.getInt(key)) }
            }
        }.getOrDefault(emptyMap())
    }

    private fun persist() {
        val json = JSONObject()
        starsByActivity.forEach { (key, value) -> json.put(key, value) }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "ai4kids.prefs"
        const val KEY = "ai4kids.progress.v1"
    }
}

/** Provides the shared [ProgressStore] down the Compose tree (mirrors the iOS
 *  environment object injection). */
val LocalProgressStore = staticCompositionLocalOf<ProgressStore> {
    error("ProgressStore not provided")
}
