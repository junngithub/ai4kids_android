package sg.com.tertiarycourses.ai4kids.ui.activities.phonics

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * Phonics Quest — an offline, on-device phonics adventure for ages 4–6.
 *
 * Ideas adapted (not copied) from the PhonixQuest concept: a map of phonics
 * "worlds", bite-size mini-games, and gamified progression with stars and
 * unlocking. Everything runs locally — sounds are spoken with the device's
 * built-in TextToSpeech, so there's no network, no AI service, no accounts.
 */

/** The mini-game kinds a world can use. */
enum class PhonicsKind { POP, BUILD, RHYME, LISTEN }

/** "Pop the Phoneme" round: which starting sound does this picture make? */
data class PopRound(val emoji: String, val word: String, val answer: Char, val options: List<Char>)

/** "Build the Word" round: spell the word for the picture from letter tiles. */
data class BuildRound(val emoji: String, val word: String)

/** "Rhyme Time" round: pick the option that rhymes with the target. */
data class RhymeRound(
    val emoji: String,
    val word: String,
    val options: List<Pair<String, String>>, // emoji to word
    val answer: Int,
)

/** "Listen & Find" round: hear the word, then tap the matching word among
 *  similar-sounding choices (no pictures — the child decides by listening). */
data class ListenRound(
    val word: String,
    val options: List<String>, // candidate words; [answer] is the spoken word
    val answer: Int,
)

/** One world on the adventure map. Only the list matching [kind] is populated. */
data class PhonicsStage(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val color: Color,
    val kind: PhonicsKind,
    val pop: List<PopRound> = emptyList(),
    val build: List<BuildRound> = emptyList(),
    val rhyme: List<RhymeRound> = emptyList(),
    val listen: List<ListenRound> = emptyList(),
) {
    val rounds: Int
        get() = when (kind) {
            PhonicsKind.POP -> pop.size
            PhonicsKind.BUILD -> build.size
            PhonicsKind.RHYME -> rhyme.size
            PhonicsKind.LISTEN -> listen.size
        }
}

/** The five worlds of Phonics Quest. */
val PHONICS_STAGES: List<PhonicsStage> = listOf(
    PhonicsStage(
        id = "letters-land",
        title = "Letters Land",
        subtitle = "Starting sounds",
        emoji = "🅰️",
        color = Theme.Pink,
        kind = PhonicsKind.POP,
        pop = listOf(
            PopRound("🍎", "Apple", 'A', listOf('A', 'B', 'S')),
            PopRound("🐻", "Bear", 'B', listOf('B', 'D', 'M')),
            PopRound("🐱", "Cat", 'C', listOf('C', 'K', 'T')),
            PopRound("🐶", "Dog", 'D', listOf('D', 'B', 'P')),
            PopRound("🥚", "Egg", 'E', listOf('E', 'A', 'I')),
            PopRound("🌙", "Moon", 'M', listOf('M', 'N', 'W')),
        ),
    ),
    PhonicsStage(
        id = "blend-bridge",
        title = "Blend Bridge",
        subtitle = "Build short words",
        emoji = "🌉",
        color = Theme.Orange,
        kind = PhonicsKind.BUILD,
        build = listOf(
            BuildRound("🐱", "CAT"),
            BuildRound("🐶", "DOG"),
            BuildRound("☀️", "SUN"),
            BuildRound("🎩", "HAT"),
            BuildRound("🚌", "BUS"),
        ),
    ),
    PhonicsStage(
        id = "silent-letters",
        title = "Whisper Woods",
        subtitle = "Silent letters",
        emoji = "🤫",
        color = Theme.Purple,
        kind = PhonicsKind.BUILD,
        build = listOf(
            BuildRound("🐑", "LAMB"),   // silent B
            BuildRound("🔪", "KNIFE"),  // silent K
            BuildRound("👻", "GHOST"),  // silent H
            BuildRound("🏰", "CASTLE"), // silent T
            BuildRound("✍️", "WRITE"),  // silent W
        ),
    ),
    PhonicsStage(
        id = "rhyme-road",
        title = "Rhyme Road",
        subtitle = "Words that rhyme",
        emoji = "🎵",
        color = Theme.Green,
        kind = PhonicsKind.RHYME,
        rhyme = listOf(
            RhymeRound("🐱", "Cat", listOf("🎩" to "Hat", "🐶" to "Dog", "☀️" to "Sun"), 0),
            RhymeRound("⭐", "Star", listOf("🚗" to "Car", "🌙" to "Moon", "🐟" to "Fish"), 0),
            RhymeRound("🌳", "Tree", listOf("🐝" to "Bee", "🐱" to "Cat", "☀️" to "Sun"), 0),
            RhymeRound("🐸", "Frog", listOf("🪵" to "Log", "🐱" to "Cat", "⭐" to "Star"), 0),
            RhymeRound("🐌", "Snail", listOf("🐳" to "Whale", "🐶" to "Dog", "🐦" to "Bird"), 0),
        ),
    ),
    PhonicsStage(
        id = "story-kingdom",
        title = "Story Kingdom",
        subtitle = "Listen & find",
        emoji = "👑",
        color = Theme.Blue,
        kind = PhonicsKind.LISTEN,
        listen = listOf(
            ListenRound("Sun", listOf("Sun", "Sock", "Sand"), 0),
            ListenRound("Dog", listOf("Dog", "Dot", "Duck"), 0),
            ListenRound("Tree", listOf("Tree", "Try", "Train"), 0),
            ListenRound("Cat", listOf("Cat", "Cap", "Cot"), 0),
            ListenRound("Bear", listOf("Bear", "Bee", "Boat"), 0),
        ),
    ),
)

/**
 * Per-stage progress (best stars 0–3) persisted to SharedPreferences and exposed
 * as Compose state. A stage unlocks once the previous one is cleared (≥1 star).
 */
class PhonicsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ai4kids.phonics", Context.MODE_PRIVATE)
    private var stars by mutableStateOf<Map<String, Int>>(emptyMap())

    init {
        stars = PHONICS_STAGES.associate { it.id to prefs.getInt("stage.${it.id}", 0) }
            .filterValues { it > 0 }
    }

    fun stars(stageId: String): Int = stars[stageId] ?: 0

    val totalStars: Int get() = stars.values.sum()

    /** True if the stage at [index] is playable (first stage, or previous cleared). */
    fun isUnlocked(index: Int): Boolean {
        if (index <= 0) return true
        val prev = PHONICS_STAGES[index - 1]
        return stars(prev.id) >= 1
    }

    /** Record a stage result; returns the star *improvement* (0 if not a new best). */
    fun record(stageId: String, earned: Int): Int {
        val old = stars(stageId)
        if (earned <= old) return 0
        stars = stars.toMutableMap().apply { put(stageId, earned) }
        prefs.edit().putInt("stage.$stageId", earned).apply()
        return earned - old
    }
}
