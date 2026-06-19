package sg.com.tertiarycourses.ai4kids.gdx

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.viewport.FitViewport
import sg.com.tertiarycourses.ai4kids.escape.CoopSession
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The Robot Lab — a top-down LibGDX escape room, rebuilt from the Robot Lab in
 * alfredang/ai4kids (`src/lib/escape-rooms.ts`). Drive the explorer with a
 * floating joystick, walk up to each machine and solve its puzzle. The three
 * machines each light up a secret word on the Word Display; find all three in
 * the word search and the square where they cross gives the exit-door code.
 *
 * The four puzzle *interactions* are copied faithfully:
 *   • Control Panel  — count the robots and key in the number   (code)
 *   • Robot Helper   — tap the 3 "how machines learn" steps in order (order)
 *   • Symbol Decoder — read the symbol→letter key, decode GEAR   (cipher)
 *   • Word Display   — drag to find ROBOT / LEARN / GEAR; they cross at one cell
 *   • Exit Keypad    — key in the crossing square's Column + Row (= 45)
 * Emoji art from the source is drawn here as simple shape pictograms.
 *
 * [onFinish] is called with the stars earned (0 if the player just backs out).
 */
class EscapeGdxGame(
    /** Which escape-room map to play (index into [levels]). */
    private val levelIndex: Int = 0,
    /** When non-null, the game runs as a co-op session (shared solved set). */
    private val coop: CoopSession? = null,
    private val onFinish: (Int) -> Unit,
) : ApplicationAdapter() {

    private companion object {
        const val SPEED = 230f
        const val JOY_RADIUS = 72f
        const val CHAR_R = 22f

        // The Control Panel "count the robots" field: -1 = robot, else glyph kind.
        // Six robots hide among the other machines (matches the source clue row).
        val ROBOT_FIELD = listOf(2, -1, -1, 1, -1, 0, -1, 7, 4, -1, 2, -1)
    }

    // Puzzle design canvas. The height is fixed at 800 (so every puzzle's vertical
    // layout is orientation-independent); the width widens in landscape so the
    // overlay fills the screen instead of letterboxing into a portrait card. The
    // panel and content centre on W, so wider W just gives more room to spread.
    private var W = 480f
    private val H = 800f
    private val landscapePanel get() = W > 700f // the wide-canvas (landscape) layout
    // In landscape the panel is a centred wide card (capped) with the dim filling
    // the rest of the screen; in portrait it's the usual near-full-width card.
    private val PANEL_W get() = if (landscapePanel) minOf(W - 80f, 1040f) else W - 56f
    private val PANEL_X get() = if (landscapePanel) (W - PANEL_W) / 2f else 28f
    private val PANEL_Y = 130f
    private val PANEL_H = 540f

    private val cInk = Color(0.16f, 0.14f, 0.30f, 1f)
    private val cAccent = Color(0.45f, 0.30f, 0.92f, 1f)
    private val confettiColors = arrayOf(
        Color(1f, 0.82f, 0.20f, 1f), Color(0.98f, 0.45f, 0.55f, 1f), Color(0.40f, 0.82f, 0.95f, 1f),
        Color(0.55f, 0.85f, 0.45f, 1f), Color(1f, 0.65f, 0.30f, 1f),
    )
    private val cGood = Color(0.20f, 0.78f, 0.45f, 1f)
    private val cSlot = Color(0.86f, 0.86f, 0.92f, 1f)
    private val cOpen = Color(0.93f, 0.93f, 0.97f, 1f)

    // Distinct colours for the 8 shape pictograms (cipher symbols & decoy icons).
    private val glyphColors = arrayOf(
        Color(0.95f, 0.55f, 0.20f, 1f), // 0 circle
        Color(0.30f, 0.66f, 0.95f, 1f), // 1 square
        Color(0.38f, 0.80f, 0.42f, 1f), // 2 triangle
        Color(0.92f, 0.42f, 0.62f, 1f), // 3 diamond
        Color(0.66f, 0.48f, 0.92f, 1f), // 4 ring
        Color(0.95f, 0.80f, 0.22f, 1f), // 5 plus
        Color(0.50f, 0.60f, 0.72f, 1f), // 6 cross
        Color(0.95f, 0.45f, 0.35f, 1f), // 7 star
    )

    /** Which Word-Display word each machine lights up once its puzzle is solved. */
    private val wordSource = mapOf("ROBOT" to "panel", "LEARN" to "robot", "GEAR" to "decoder")
    private fun isWordRevealed(word: String) = wordSource[word]?.let { it in solved } ?: true

    /* --------------------------- puzzle base --------------------------- */

    private abstract inner class Puzzle {
        abstract val instruction: String
        open fun onOpen() {}
        abstract fun onDown(p: Vector2)
        open fun onDrag(p: Vector2) {}
        open fun onUp(p: Vector2) {}
        abstract fun draw()
        abstract val complete: Boolean
    }

    /* --- Control Panel & Exit Keypad: a number lock --------------------- */

    private inner class NumberLock(
        private val code: String,
        private val heading: String,
        private val prompt: String,
        /** Count field icons (null = plain keypad). -1 = robot, else glyph kind. */
        private val icons: List<Int>? = null,
        private val showClues: Boolean = false,
    ) : Puzzle() {
        override val instruction = if (icons != null) "Count, then key in the number" else "Key in the code"
        private val entered = StringBuilder()
        private var done = false
        private val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "<", "0", "OK")
        private val cols = 3
        private val btn = 60f
        private val gapx = 16f
        private val gapy = 12f
        private val topRowY = 386f

        override fun onOpen() { entered.setLength(0); done = false }

        // Landscape puts the keypad on the right; portrait centres it (computed
        // from the current width so it follows the orientation-aware canvas).
        private fun keyRect(i: Int): FloatArray {
            val r = i / cols; val c = i % cols
            val kw = cols * btn + (cols - 1) * gapx
            // Landscape: keypad just right of centre (the count field sits just left).
            val kx0 = if (landscapePanel) W / 2f + 30f else (W - kw) / 2f
            val ky0 = if (landscapePanel) 470f else topRowY
            return floatArrayOf(kx0 + c * (btn + gapx), ky0 - r * (btn + gapy), btn, btn)
        }

        // Landscape arranges the count field just left of centre; portrait above the keypad.
        private fun iconCenter(i: Int): Vector2 {
            val cellW = 78f
            if (landscapePanel) {
                val c = i % 3; val r = i / 3
                return Vector2(W / 2f - 230f + c * 80f, 460f - r * 56f)
            }
            val c = i % 4; val r = i / 4
            val startX = (W - 4 * cellW) / 2f + cellW / 2f
            return Vector2(startX + c * cellW, 590f - r * 40f)
        }

        override fun onDown(p: Vector2) {
            keys.indices.forEach { i ->
                if (inRect(p, keyRect(i))) when (val k = keys[i]) {
                    "<" -> if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1)
                    "OK" -> if (entered.toString() == code) done = true else { wrongFlash = 1f; entered.setLength(0) }
                    else -> if (entered.length < code.length) entered.append(k)
                }
            }
        }

        override val complete: Boolean get() = done

        override fun draw() {
            // Display slot — under the count field (just left of centre) in landscape.
            val slotX = if (landscapePanel) W / 2f - 250f else (W - 200f) / 2f
            val slotY = if (landscapePanel) 220f else 452f
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            shapes.color = cSlot
            shapes.rect(slotX, slotY, 200f, 46f)
            keys.indices.forEach { i ->
                val r = keyRect(i)
                shapes.color = when (keys[i]) { "OK" -> cGood; "<" -> Color(0.9f, 0.5f, 0.3f, 1f); else -> cAccent }
                shapes.rect(r[0], r[1], r[2], r[3])
            }
            shapes.end()

            batch.begin()
            // Count-field pictograms (anti-aliased, drawn through the batch).
            icons?.forEachIndexed { i, k ->
                val c = iconCenter(i)
                if (k < 0) drawRobot(c.x, c.y, 15f) else drawGlyph(k, c.x, c.y, 14f)
            }
            font.color = cInk
            centerText(heading, W / 2f, 650f, 1.2f)
            centerText(prompt, W / 2f, 626f, 0.82f)
            if (showClues) {
                val cl = collectedClues()
                if (cl.isNotEmpty()) { font.color = cGood; centerText(cl.joinToString("   "), W / 2f, 540f, 0.78f) }
            }
            font.color = cInk
            centerText(if (entered.isEmpty()) "_" else entered.toString(), slotX + 100f, slotY + 16f, 1.3f)
            keys.indices.forEach { i ->
                val r = keyRect(i)
                font.color = Color.WHITE
                centerText(keys[i], r[0] + btn / 2f, r[1] + btn / 2f + 6f, if (keys[i].length > 1) 0.7f else 1.1f)
            }
            if (wrongFlash > 0f) { font.color = Color(0.96f, 0.34f, 0.34f, 1f); centerText("Try again!", W / 2f, 168f, 1f) }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Robot Helper: put the learning steps in order ------------------ */

    private inner class Order(private val steps: List<String>) : Puzzle() {
        override val instruction = "Tap the steps in order"
        private var display = steps.indices.toList()
        private val seq = ArrayList<Int>()

        override fun onOpen() {
            seq.clear()
            do { display = steps.indices.shuffled() } while (display == steps.indices.toList())
        }

        // Three cards, vertically centred between the heading and the footer.
        // Landscape spreads them as wide full-width rows; portrait stacks narrow.
        private fun cardRect(i: Int): FloatArray =
            if (landscapePanel) floatArrayOf(PANEL_X + 28f, 480f - i * 145f, PANEL_W - 56f, 120f)
            else floatArrayOf(40f, 470f - i * 118f, 400f, 100f)

        override fun onDown(p: Vector2) {
            display.indices.forEach { j ->
                if (inRect(p, cardRect(j))) {
                    val step = display[j]
                    when {
                        step in seq -> seq.clear()        // tap a placed card to start over
                        step == seq.size -> seq.add(step) // the correct next step → place it (green)
                        else -> { wrongFlash = 1f; seq.clear() } // wrong order → reject, don't light it
                    }
                }
            }
        }

        override val complete: Boolean get() = seq == steps.indices.toList()

        override fun draw() {
            val land = landscapePanel
            val badgeDx = if (land) 56f else 36f
            val badgeR = if (land) 30f else 20f
            val textDx = if (land) 110f else 68f

            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            display.indices.forEach { j ->
                val r = cardRect(j)
                val placed = display[j] in seq
                shapes.color = if (placed) cGood else cOpen
                shapes.rect(r[0], r[1], r[2], r[3])
                shapes.color = if (placed) Color.WHITE else cAccent
                shapes.circle(r[0] + badgeDx, r[1] + r[3] / 2f, badgeR)
            }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText("Robot Helper", W / 2f, 650f, if (land) 1.7f else 1.45f)
            centerText("Teach the robot to spot cats", W / 2f, 620f, if (land) 1.1f else 0.95f)
            display.indices.forEach { j ->
                val r = cardRect(j)
                val placed = display[j] in seq
                // White on the purple/green badge reads clearly in both states.
                font.color = if (placed) cGood else Color.WHITE
                val badge = if (placed) (seq.indexOf(display[j]) + 1).toString() else "?"
                centerText(badge, r[0] + badgeDx, r[1] + r[3] / 2f, if (land) 1.6f else 1.3f)
                // Step text left-aligned beside the badge, vertically centred.
                font.color = cInk
                wrapLeft(steps[display[j]], r[0] + textDx, r[1] + r[3] / 2f, r[2] - textDx - 30f, if (land) 1.35f else 0.98f)
            }
            font.color = cInk
            centerText(instruction, W / 2f, if (land) 158f else 188f, if (land) 1.1f else 0.95f)
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Symbol Decoder: substitution cipher ---------------------------- */

    private inner class Cipher(
        private val legendLetters: List<Char>, // legendLetters[k] is the letter for glyph-kind k
        private val coded: List<Int>,           // glyph kinds spelling the answer
        private val answer: String,
    ) : Puzzle() {
        override val instruction = "Read the key, spell the word"
        private val typed = StringBuilder()
        private var done = false
        private val tileLetters = legendLetters + '<'

        override fun onOpen() { typed.setLength(0); done = false }

        // Landscape stacks the legend in a 2x4 block on the left; portrait is a 4x2 row.
        private fun legendRect(k: Int): FloatArray {
            if (landscapePanel) {
                val cw = 120f; val ch = 42f
                val c = k % 2; val r = k / 2
                return floatArrayOf(PANEL_X + 50f + c * cw, 540f - r * (ch + 10f), cw - 8f, ch)
            }
            val c = k % 4; val r = k / 4
            val cellW = 104f; val cellH = 40f
            val x0 = (W - 4 * cellW) / 2f
            return floatArrayOf(x0 + c * cellW, 566f - r * (cellH + 6f), cellW - 8f, cellH)
        }

        // Landscape stacks the letter tiles in a 3x3 block on the right.
        private fun tileRect(i: Int): FloatArray {
            if (landscapePanel) {
                val b = 60f; val gx = 14f; val gy = 14f
                val c = i % 3; val r = i / 3
                val x0 = PANEL_X + PANEL_W - (3 * b + 2 * gx) - 60f
                return floatArrayOf(x0 + c * (b + gx), 460f - r * (b + gy), b, b)
            }
            val cols = 5; val c = i % cols; val r = i / cols
            val b = 56f; val gx = 12f; val gy = 10f
            val x0 = (W - (cols * b + (cols - 1) * gx)) / 2f
            return floatArrayOf(x0 + c * (b + gx), 330f - r * (b + gy), b, b)
        }

        override fun onDown(p: Vector2) {
            tileLetters.indices.forEach { i ->
                if (inRect(p, tileRect(i))) {
                    val ch = tileLetters[i]
                    if (ch == '<') { if (typed.isNotEmpty()) typed.deleteCharAt(typed.length - 1) }
                    else if (typed.length < answer.length) {
                        typed.append(ch)
                        if (typed.length == answer.length) {
                            if (typed.toString() == answer) done = true else { wrongFlash = 1f; typed.setLength(0) }
                        }
                    }
                }
            }
        }

        override val complete: Boolean get() = done

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            // Legend cells.
            legendLetters.indices.forEach { k ->
                val r = legendRect(k)
                shapes.color = cOpen
                shapes.rect(r[0], r[1], r[2], r[3])
            }
            // Answer slots — filled (neutral) as you type, green only once correct.
            for (i in answer.indices) {
                shapes.color = if (done) cGood else if (i < typed.length) Color(0.72f, 0.74f, 0.84f, 1f) else cSlot
                shapes.rect((W - answer.length * 50f) / 2f + i * 50f + 4f, 395f, 42f, 42f)
            }
            // Letter tiles.
            tileLetters.indices.forEach { i ->
                val r = tileRect(i)
                shapes.color = if (tileLetters[i] == '<') Color(0.9f, 0.5f, 0.3f, 1f) else cAccent
                shapes.rect(r[0], r[1], r[2], r[3])
            }
            shapes.end()

            batch.begin()
            // Symbol pictograms — legend keys + the coded message (anti-aliased).
            legendLetters.indices.forEach { k ->
                val r = legendRect(k)
                drawGlyph(k, r[0] + 20f, r[1] + r[3] / 2f, 12f)
            }
            coded.forEachIndexed { i, k ->
                drawGlyph(k, (W - coded.size * 56f) / 2f + i * 56f + 28f, 462f, 16f)
            }
            font.color = cInk
            centerText("Symbol Decoder", W / 2f, 650f, 1.35f)
            legendLetters.indices.forEach { k ->
                val r = legendRect(k)
                font.color = cInk
                centerText("= ${legendLetters[k]}", r[0] + r[2] - 24f, r[1] + r[3] / 2f, 1.0f)
            }
            font.color = cInk
            centerText("Secret word:", W / 2f, 498f, 0.9f)
            font.color = if (done) cGood else cInk
            typed.forEachIndexed { i, ch ->
                centerText(ch.toString(), (W - answer.length * 50f) / 2f + i * 50f + 25f, 416f, 1.3f)
            }
            tileLetters.indices.forEach { i ->
                val r = tileRect(i)
                font.color = Color.WHITE
                centerText(tileLetters[i].toString(), r[0] + r[2] / 2f, r[1] + r[3] / 2f, 1.25f)
            }
            if (wrongFlash > 0f) { font.color = Color(0.96f, 0.34f, 0.34f, 1f); centerText("Not quite!", W / 2f, 150f, 1.05f) }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Word Display: word search with a crossing cell ----------------- */

    private inner class WordSearch(
        private val words: List<String>,
        private val grid: Array<CharArray>,
        private val crossRow: Int,
        private val crossCol: Int,
    ) : Puzzle() {
        override val instruction = "Drag across a glowing word"
        private val n = grid.size
        private val cell = 44f
        private val x0 get() = (W - n * cell) / 2f // centred; follows the canvas width
        private val top = 582f
        private val found = HashSet<String>()
        private val foundCells = HashSet<Int>()
        private var startCell = -1
        private var dragCell = -1

        /** The grid only powers on once every machine has lit up its word. */
        private val readable get() = words.all { isWordRevealed(it) }

        override fun onOpen() { startCell = -1; dragCell = -1 }

        private fun cellAt(p: Vector2): Int {
            val col = ((p.x - x0) / cell).toInt()
            val row = ((top - p.y) / cell).toInt()
            return if (row in 0 until n && col in 0 until n && p.x >= x0 && p.y <= top) row * n + col else -1
        }

        private fun cx(col: Int) = x0 + col * cell + cell / 2f
        private fun cy(row: Int) = top - row * cell - cell / 2f

        override fun onDown(p: Vector2) { startCell = cellAt(p); dragCell = startCell }
        override fun onDrag(p: Vector2) { if (startCell >= 0) cellAt(p).let { if (it >= 0) dragCell = it } }

        override fun onUp(p: Vector2) {
            val end = cellAt(p).let { if (it >= 0) it else dragCell }
            if (startCell < 0 || end < 0) { startCell = -1; return }
            val r0 = startCell / n; val c0 = startCell % n
            val r1 = end / n; val c1 = end % n
            val dr = r1 - r0; val dc = c1 - c0
            val steps = max(abs(dr), abs(dc))
            if ((dr == 0 || dc == 0 || abs(dr) == abs(dc)) && steps > 0) {
                val sr = sgn(dr); val sc = sgn(dc)
                val cells = (0..steps).map { (r0 + sr * it) * n + (c0 + sc * it) }
                val word = cells.joinToString("") { grid[it / n][it % n].toString() }
                val match = words.firstOrNull { it == word || it == word.reversed() }
                if (match != null && match !in found && readable) {
                    found.add(match); foundCells.addAll(cells)
                }
            }
            startCell = -1; dragCell = -1
        }

        override val complete: Boolean get() = found.size == words.size

        override fun draw() {
            val on = readable
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            for (row in 0 until n) for (col in 0 until n) {
                val idx = row * n + col
                shapes.color = when {
                    !on -> Color(0.40f, 0.40f, 0.46f, 1f)   // powered down — static
                    complete && row == crossRow && col == crossCol -> Color(1f, 0.82f, 0.25f, 1f)
                    idx in foundCells -> cGood
                    idx == startCell || idx == dragCell -> Color(1f, 0.85f, 0.3f, 1f)
                    else -> cOpen
                }
                shapes.rect(x0 + col * cell + 2f, top - row * cell - cell + 2f, cell - 4f, cell - 4f)
            }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText("Word Display", W / 2f, 656f, 1.1f)
            for (c in 0 until n) centerText((c + 1).toString(), cx(c), top + 16f, 0.7f)
            for (r in 0 until n) centerText((r + 1).toString(), x0 - 16f, cy(r) + 6f, 0.7f)
            for (row in 0 until n) for (col in 0 until n) {
                val idx = row * n + col
                // Letters are scrambled-out static until the display powers on.
                font.color = if (!on) Color(0.30f, 0.30f, 0.34f, 1f)
                    else if (idx in foundCells || (complete && row == crossRow && col == crossCol)) Color.WHITE else cInk
                centerText(if (on) grid[row][col].toString() else "?", cx(col), cy(row) + 6f, 0.85f)
            }
            // Words to find: masked until their machine lights them up.
            font.color = cInk
            val list = words.joinToString("   ") {
                when { it in found -> "[$it]"; isWordRevealed(it) -> it; else -> "?".repeat(it.length) }
            }
            centerText(list, W / 2f, 196f, 0.8f)
            when {
                !on -> { font.color = Color(0.96f, 0.6f, 0.3f, 1f); centerText("Power up the display — solve every machine first!", W / 2f, 166f, 0.72f) }
                complete -> { font.color = cGood; centerText("They cross at Column ${crossCol + 1}, Row ${crossRow + 1}!", W / 2f, 166f, 0.78f) }
                else -> centerText(instruction, W / 2f, 166f, 0.7f)
            }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Note: a clue drop (never "solved", just closed) ---------------- */

    private inner class Note : Puzzle() {
        override val instruction = ""
        override fun onDown(p: Vector2) {}
        override val complete get() = false

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            // Dim the scene, then the clue card. The parchment is a placeholder —
            // swap this rect for a drawn Texture (the asset) at the same bounds.
            shapes.color = Color(0f, 0f, 0f, 0.6f)
            shapes.rect(0f, 0f, W, H)
            shapes.color = Color(0.96f, 0.91f, 0.66f, 1f)
            shapes.rect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H)
            shapes.color = Color(0.78f, 0.68f, 0.38f, 1f)
            shapes.rect(PANEL_X, PANEL_Y + PANEL_H - 12f, PANEL_W, 12f)
            // Close (X) button.
            shapes.color = Color(0.92f, 0.40f, 0.40f, 1f)
            shapes.circle(backCenter.x, backCenter.y, backR)
            shapes.end()

            batch.begin()
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Solar Panel: a multiple-choice question ----------------------- */

    private inner class Mcq(
        private val heading: String,
        private val question: String,
        /** [options][0] is the correct answer; choices are shuffled on open. */
        private val options: List<String>,
    ) : Puzzle() {
        override val instruction = "Tap the right answer"
        private var order = options.indices.toList()
        private var done = false

        override fun onOpen() { done = false; order = options.indices.shuffled() }

        // Landscape lays the choices out as a row; portrait stacks them.
        private fun optRect(i: Int): FloatArray {
            if (landscapePanel) {
                val gap = 28f
                val ow = (PANEL_W - 4 * gap) / 3f
                return floatArrayOf(PANEL_X + gap + i * (ow + gap), 300f, ow, 150f)
            }
            return floatArrayOf(56f, 452f - i * 96f, 368f, 78f)
        }

        override fun onDown(p: Vector2) {
            order.indices.forEach { j ->
                if (inRect(p, optRect(j))) {
                    if (order[j] == 0) done = true else wrongFlash = 1f
                }
            }
        }

        override val complete: Boolean get() = done

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            order.indices.forEach { j ->
                val r = optRect(j)
                shapes.color = if (done && order[j] == 0) cGood else cOpen
                shapes.rect(r[0], r[1], r[2], r[3])
            }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText(heading, W / 2f, 636f, 1.2f)
            wrapText(question, W / 2f, if (landscapePanel) 576f else 600f, if (landscapePanel) PANEL_W - 160f else 364f, 0.86f)
            order.indices.forEach { j ->
                val r = optRect(j)
                font.color = if (done && order[j] == 0) Color.WHITE else cInk
                centerText(options[order[j]], r[0] + r[2] / 2f, r[1] + r[3] / 2f + 6f, 0.92f)
            }
            if (wrongFlash > 0f) { font.color = Color(0.96f, 0.34f, 0.34f, 1f); centerText("Try again!", W / 2f, 168f, 1f) }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Power Circuit: rotate pipe tiles to connect power to the bulb -- */

    private inner class Circuit : Puzzle() {
        override val instruction = "Tap tiles to spin the pipes"
        // Connection bits: N=1, E=2, S=4, W=8 (grid row 0 = top).
        private val rowsN = 3
        private val colsN = 3
        private val cell = 92f
        private val gap = 8f
        private val pitch = cell + gap
        // Computed from the current width so the grid stays centred when the
        // canvas widens in landscape.
        private val gridX0 get() = (W - (colsN * cell + (colsN - 1) * gap)) / 2f
        private val gridY0 = 472f
        private val srcR = 1; private val srcC = 0
        private val bulbR = 1; private val bulbC = 2

        private val solution = IntArray(rowsN * colsN)
        private val mask = IntArray(rowsN * colsN)

        init {
            // The intended winding path: power in the west of (1,0), up and across
            // the top row, then down into the bulb at the east of (1,2).
            solution[idx(1, 0)] = 8 or 1 // W + N
            solution[idx(0, 0)] = 4 or 2 // S + E
            solution[idx(0, 1)] = 8 or 2 // W + E (straight)
            solution[idx(0, 2)] = 8 or 4 // W + S
            solution[idx(1, 2)] = 1 or 2 // N + E
            // Decoys (off-path) get assorted pipe shapes.
            val decoyShapes = listOf(8 or 2, 1 or 4, 1 or 2, 4 or 8, 2 or 4, 1 or 8)
            solution[idx(1, 1)] = decoyShapes.random()
            solution[idx(2, 0)] = decoyShapes.random()
            solution[idx(2, 1)] = decoyShapes.random()
            solution[idx(2, 2)] = decoyShapes.random()
        }

        override fun onOpen() {
            do {
                for (i in mask.indices) mask[i] = rotate(solution[i], (1..3).random())
            } while (solved())
        }

        private fun idx(r: Int, c: Int) = r * colsN + c
        private fun rotateCW(m: Int) = ((m shl 1) or (m shr 3)) and 0xF
        private fun rotate(m: Int, times: Int): Int { var x = m; repeat(times) { x = rotateCW(x) }; return x }
        private fun opposite(b: Int) = when (b) { 1 -> 4; 4 -> 1; 2 -> 8; else -> 2 }
        private fun stepRC(r: Int, c: Int, b: Int) = when (b) {
            1 -> r - 1 to c; 4 -> r + 1 to c; 2 -> r to c + 1; else -> r to c - 1
        }

        /** Flood power from the west of the source; the bulb lights if reached. */
        private fun solved(): Boolean {
            val powered = BooleanArray(rowsN * colsN)
            val queue = ArrayDeque<Int>()
            if (mask[idx(srcR, srcC)] and 8 != 0) { powered[idx(srcR, srcC)] = true; queue.add(idx(srcR, srcC)) }
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst(); val r = cur / colsN; val c = cur % colsN
                for (b in intArrayOf(1, 2, 4, 8)) if (mask[cur] and b != 0) {
                    val (nr, nc) = stepRC(r, c, b)
                    if (nr in 0 until rowsN && nc in 0 until colsN) {
                        val ni = idx(nr, nc)
                        if (!powered[ni] && mask[ni] and opposite(b) != 0) { powered[ni] = true; queue.add(ni) }
                    }
                }
            }
            return powered[idx(bulbR, bulbC)] && mask[idx(bulbR, bulbC)] and 2 != 0
        }

        override val complete: Boolean get() = solved()

        private fun cellRect(r: Int, c: Int) = floatArrayOf(gridX0 + c * pitch, gridY0 - r * pitch, cell, cell)

        override fun onDown(p: Vector2) {
            for (r in 0 until rowsN) for (c in 0 until colsN) {
                if (inRect(p, cellRect(r, c))) { mask[idx(r, c)] = rotateCW(mask[idx(r, c)]); return }
            }
        }

        override fun draw() {
            val powered = poweredSet()
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            for (r in 0 until rowsN) for (c in 0 until colsN) {
                val rect = cellRect(r, c)
                shapes.color = cOpen
                shapes.rect(rect[0], rect[1], rect[2], rect[3])
            }
            shapes.end()

            batch.begin()
            // Pipes & nodes — anti-aliased, round-capped (SmoothDraw) — over the cells.
            for (r in 0 until rowsN) for (c in 0 until colsN) {
                val rect = cellRect(r, c)
                val cx = rect[0] + cell / 2f; val cy = rect[1] + cell / 2f
                val col = if (powered[idx(r, c)]) cGood else Color(0.62f, 0.64f, 0.72f, 1f)
                val m = mask[idx(r, c)]
                if (m and 1 != 0) smooth.line(batch, cx, cy, cx, cy + cell / 2f, 13f, col)
                if (m and 4 != 0) smooth.line(batch, cx, cy, cx, cy - cell / 2f, 13f, col)
                if (m and 2 != 0) smooth.line(batch, cx, cy, cx + cell / 2f, cy, 13f, col)
                if (m and 8 != 0) smooth.line(batch, cx, cy, cx - cell / 2f, cy, 13f, col)
                smooth.circle(batch, cx, cy, 9f, col)
            }
            // Power inlet (left of source) and the bulb (right of the target).
            val s = cellRect(srcR, srcC); val b = cellRect(bulbR, bulbC)
            smooth.circle(batch, s[0] - 16f, s[1] + cell / 2f, 13f, Color(0.95f, 0.80f, 0.22f, 1f))
            smooth.circle(batch, b[0] + cell + 16f, b[1] + cell / 2f, 13f, if (complete) cGood else Color(0.62f, 0.64f, 0.72f, 1f))
            font.color = cInk
            centerText("Power Circuit", W / 2f, 636f, 1.2f)
            centerText("Spin the pipes to connect power to the bulb", W / 2f, 612f, 0.7f)
            font.color = Color(0.85f, 0.6f, 0.1f, 1f)
            centerText("PWR", s[0] - 16f, s[1] + cell / 2f - 26f, 0.62f)
            font.color = if (complete) cGood else cInk
            centerText("BULB", b[0] + cell + 16f, b[1] + cell / 2f - 26f, 0.62f)
            if (complete) { font.color = cGood; centerText("Power on!", W / 2f, 168f, 1f) }
            drawBackLabel()
            batch.end()
        }

        private fun poweredSet(): BooleanArray {
            val powered = BooleanArray(rowsN * colsN)
            val queue = ArrayDeque<Int>()
            if (mask[idx(srcR, srcC)] and 8 != 0) { powered[idx(srcR, srcC)] = true; queue.add(idx(srcR, srcC)) }
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst(); val r = cur / colsN; val c = cur % colsN
                for (bit in intArrayOf(1, 2, 4, 8)) if (mask[cur] and bit != 0) {
                    val (nr, nc) = stepRC(r, c, bit)
                    if (nr in 0 until rowsN && nc in 0 until colsN) {
                        val ni = idx(nr, nc)
                        if (!powered[ni] && mask[ni] and opposite(bit) != 0) { powered[ni] = true; queue.add(ni) }
                    }
                }
            }
            return powered
        }
    }

    /* --- Fairness Core: share the treats so everyone gets the same ----- */

    private inner class Fair(
        private val animalGlyphs: List<Int>, // pictogram stand-ins for the animals
        private val total: Int,
    ) : Puzzle() {
        override val instruction = "Give everyone the same"
        private val each = total / animalGlyphs.size
        private val give = IntArray(animalGlyphs.size)
        private var done = false

        override fun onOpen() { give.fill(0); done = false }
        private val used get() = give.sum()

        private fun colX(i: Int) = W / 2f + (i - (animalGlyphs.size - 1) / 2f) * 140f
        private fun plusRect(i: Int) = floatArrayOf(colX(i) - 30f, 360f, 60f, 52f)
        private fun minusRect(i: Int) = floatArrayOf(colX(i) - 30f, 296f, 60f, 52f)

        override fun onDown(p: Vector2) {
            animalGlyphs.indices.forEach { i ->
                if (inRect(p, plusRect(i)) && used < total) give[i]++
                else if (inRect(p, minusRect(i)) && give[i] > 0) give[i]--
            }
            done = used == total && give.all { it == each }
        }

        override val complete: Boolean get() = done

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            animalGlyphs.indices.forEach { i ->
                val pr = plusRect(i); shapes.color = cGood; shapes.rect(pr[0], pr[1], pr[2], pr[3])
                val mr = minusRect(i); shapes.color = Color(0.9f, 0.5f, 0.3f, 1f); shapes.rect(mr[0], mr[1], mr[2], mr[3])
            }
            shapes.end()

            batch.begin()
            animalGlyphs.forEachIndexed { i, g -> drawGlyph(g, colX(i), 500f, 24f) }
            font.color = cInk
            centerText("Fairness Core", W / 2f, 650f, 1.2f)
            centerText("Share the treats so everyone gets the same", W / 2f, 624f, 0.72f)
            animalGlyphs.indices.forEach { i ->
                font.color = cInk
                centerText("${give[i]}", colX(i), 452f, 1.4f)
                font.color = Color.WHITE
                centerText("+", colX(i), plusRect(i)[1] + 26f, 1.4f)
                centerText("-", colX(i), minusRect(i)[1] + 26f, 1.4f)
            }
            font.color = cInk
            centerText("Treats left: ${total - used}", W / 2f, 250f, 0.95f)
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Kindness Core: sort each sentence into Kind or Mean ----------- */

    private inner class Sort(
        private val items: List<Pair<String, Boolean>>, // text, true = kind
    ) : Puzzle() {
        override val instruction = "Sort kind from mean"
        private val cKind = Color(0.20f, 0.62f, 0.78f, 1f)
        private val cMean = Color(0.92f, 0.45f, 0.35f, 1f)
        private val choice = arrayOfNulls<Boolean>(items.size)

        override fun onOpen() { for (i in choice.indices) choice[i] = null }

        private fun rowRect(i: Int) = floatArrayOf(36f, 556f - i * 72f, W - 72f, 60f)
        private fun kBtn(i: Int): FloatArray { val r = rowRect(i); return floatArrayOf(r[0] + r[2] - 138f, r[1] + 8f, 62f, 44f) }
        private fun mBtn(i: Int): FloatArray { val r = rowRect(i); return floatArrayOf(r[0] + r[2] - 70f, r[1] + 8f, 62f, 44f) }

        override fun onDown(p: Vector2) {
            items.indices.forEach { i ->
                if (inRect(p, kBtn(i))) choice[i] = true
                else if (inRect(p, mBtn(i))) choice[i] = false
            }
        }

        override val complete: Boolean get() = items.indices.all { choice[it] == items[it].second }

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            items.indices.forEach { i ->
                val r = rowRect(i)
                shapes.color = cOpen; shapes.rect(r[0], r[1], r[2], r[3])
                val k = kBtn(i); shapes.color = if (choice[i] == true) cKind else Color(0.85f, 0.87f, 0.92f, 1f); shapes.rect(k[0], k[1], k[2], k[3])
                val m = mBtn(i); shapes.color = if (choice[i] == false) cMean else Color(0.85f, 0.87f, 0.92f, 1f); shapes.rect(m[0], m[1], m[2], m[3])
            }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText("Kindness Core", W / 2f, 650f, 1.2f)
            centerText("Tap Kind or Mean for each one", W / 2f, 624f, 0.72f)
            items.forEachIndexed { i, item ->
                val r = rowRect(i)
                font.color = cInk
                wrapLeft(item.first, r[0] + 16f, r[1] + r[3] / 2f, r[2] - 160f, 0.68f)
                val k = kBtn(i); font.color = if (choice[i] == true) Color.WHITE else cInk
                centerText("Kind", k[0] + k[2] / 2f, k[1] + k[3] / 2f, 0.6f)
                val m = mBtn(i); font.color = if (choice[i] == false) Color.WHITE else cInk
                centerText("Mean", m[0] + m[2] / 2f, m[1] + m[3] / 2f, 0.6f)
            }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- Honesty Core: choose the honest path at each fork ------------- */

    private inner class Maze(
        private val forks: List<Pair<String, String>>, // (honest, lie); honest is the way forward
    ) : Puzzle() {
        override val instruction = "Choose the honest path"
        private var at = 0
        private var done = false
        private var order = listOf(0, 1)

        override fun onOpen() { at = 0; done = false; order = listOf(0, 1).shuffled() }

        private fun optRect(j: Int) = floatArrayOf(48f, 446f - j * 118f, W - 96f, 100f)

        override fun onDown(p: Vector2) {
            (0..1).forEach { j ->
                if (inRect(p, optRect(j))) {
                    if (order[j] == 0) { // the honest statement
                        at++
                        if (at >= forks.size) done = true else order = listOf(0, 1).shuffled()
                    } else { wrongFlash = 1f; at = 0; order = listOf(0, 1).shuffled() } // a lie = dead end → back to start
                }
            }
        }

        override val complete: Boolean get() = done

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            (0..1).forEach { j -> val r = optRect(j); shapes.color = cOpen; shapes.rect(r[0], r[1], r[2], r[3]) }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText("Honesty Core", W / 2f, 650f, 1.2f)
            centerText("Fork ${at + 1} of ${forks.size} — pick the truth", W / 2f, 600f, 0.78f)
            val fork = forks[at.coerceAtMost(forks.size - 1)]
            (0..1).forEach { j ->
                val r = optRect(j)
                font.color = cInk
                wrapText(if (order[j] == 0) fork.first else fork.second, r[0] + r[2] / 2f, r[1] + r[3] / 2f + 18f, r[2] - 40f, 0.78f)
            }
            if (wrongFlash > 0f) { font.color = Color(0.96f, 0.34f, 0.34f, 1f); centerText("Dead end! Start over.", W / 2f, 150f, 0.95f) }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- The Suit: unscramble the three power words ------------------- */

    private inner class Unscramble(
        private val words: List<String>, // solved in order
    ) : Puzzle() {
        override val instruction = "Unscramble the words"
        private var wi = 0
        private val typed = StringBuilder()
        private var scrambled = charArrayOf()
        private val usedTiles = HashSet<Int>()
        private var done = false

        override fun onOpen() { wi = 0; done = false; loadWord() }

        private fun loadWord() {
            typed.setLength(0); usedTiles.clear()
            val w = words[wi]
            do { scrambled = w.toList().shuffled().toCharArray() } while (String(scrambled) == w && w.length > 1)
        }

        private fun slotRect(i: Int): FloatArray {
            val n = words[wi].length
            return floatArrayOf((W - n * 56f) / 2f + i * 56f, 430f, 48f, 48f)
        }
        private fun tileRect(i: Int): FloatArray {
            val n = scrambled.size
            return floatArrayOf((W - n * 56f) / 2f + i * 56f, 320f, 48f, 48f)
        }

        override fun onDown(p: Vector2) {
            // tap a filled slot to clear the last letter
            if (typed.isNotEmpty() && inRect(p, slotRect(typed.length - 1))) {
                val last = typed.last(); typed.deleteCharAt(typed.length - 1)
                val idx = scrambled.indices.firstOrNull { it in usedTiles && scrambled[it] == last }
                if (idx != null) usedTiles.remove(idx)
                return
            }
            scrambled.indices.forEach { i ->
                if (i !in usedTiles && inRect(p, tileRect(i)) && typed.length < words[wi].length) {
                    typed.append(scrambled[i]); usedTiles.add(i)
                    if (typed.length == words[wi].length) {
                        if (typed.toString() == words[wi]) {
                            wi++; if (wi >= words.size) done = true else loadWord()
                        } else { wrongFlash = 1f; typed.setLength(0); usedTiles.clear() }
                    }
                }
            }
        }

        override val complete: Boolean get() = done

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            for (i in words[wi].indices) {
                val r = slotRect(i)
                shapes.color = if (i < typed.length) Color(0.72f, 0.74f, 0.84f, 1f) else cSlot
                shapes.rect(r[0], r[1], r[2], r[3])
            }
            scrambled.indices.forEach { i ->
                if (i !in usedTiles) { val r = tileRect(i); shapes.color = cAccent; shapes.rect(r[0], r[1], r[2], r[3]) }
            }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText("The Hero Suit", W / 2f, 650f, 1.2f)
            centerText("Word ${wi + 1} of ${words.size} — tap the letters in order", W / 2f, 560f, 0.78f)
            typed.forEachIndexed { i, ch -> val r = slotRect(i); font.color = cInk; centerText(ch.toString(), r[0] + r[2] / 2f, r[1] + r[3] / 2f, 1.2f) }
            scrambled.indices.forEach { i ->
                if (i !in usedTiles) { val r = tileRect(i); font.color = Color.WHITE; centerText(scrambled[i].toString(), r[0] + r[2] / 2f, r[1] + r[3] / 2f, 1.2f) }
            }
            if (wrongFlash > 0f) { font.color = Color(0.96f, 0.34f, 0.34f, 1f); centerText("Not that word — try again!", W / 2f, 250f, 0.9f) }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- The Grand Hall: drag word-tiles into the acrostic crossword ---- */

    private inner class Crossword(
        private val rows: List<XRow>,
        private val secretCol: Int,
    ) : Puzzle() {
        override val instruction = "Drag each word into its numbered row"
        private val words = rows.map { it.word }
        private val wordInRow = IntArray(rows.size) { -1 } // word index placed in each row (-1 = empty)
        private val traySlot = words.indices.shuffled()
        private var drag = -1
        private val dragPos = Vector2()
        private val cell = 30f
        private val rowPitch = 38f
        private val gridCols = rows.maxOf { it.offset + it.word.length }
        private val gy0 = 506f

        private val gx0 get() = (W - gridCols * cell) / 2f
        private fun rowY(r: Int) = gy0 - r * rowPitch
        private fun cellRect(r: Int, col: Int) = floatArrayOf(gx0 + col * cell, rowY(r), cell, cell)
        private fun trayRect(slot: Int): FloatArray {
            val tw = 104f; val th = 40f; val gap = 12f
            val x0 = (W - (words.size * tw + (words.size - 1) * gap)) / 2f
            return floatArrayOf(x0 + slot * (tw + gap), 250f, tw, th)
        }
        private fun rowOfWord(w: Int): Int { for (r in rows.indices) if (wordInRow[r] == w) return r; return -1 }

        override fun onOpen() { for (r in wordInRow.indices) wordInRow[r] = -1; drag = -1 }

        override fun onDown(p: Vector2) {
            words.indices.forEach { w ->
                if (rowOfWord(w) < 0 && inRect(p, trayRect(traySlot.indexOf(w)))) { drag = w; dragPos.set(p); return }
            }
            rows.indices.forEach { r ->
                if (wordInRow[r] >= 0 && p.x >= gx0 && p.x <= gx0 + gridCols * cell && p.y >= rowY(r) && p.y <= rowY(r) + cell) {
                    drag = wordInRow[r]; wordInRow[r] = -1; dragPos.set(p); return
                }
            }
        }
        override fun onDrag(p: Vector2) { if (drag >= 0) dragPos.set(p) }
        override fun onUp(p: Vector2) {
            if (drag < 0) return
            var target = -1
            rows.indices.forEach { r -> if (p.y >= rowY(r) && p.y <= rowY(r) + cell) target = r }
            if (target >= 0 && wordInRow[target] < 0) wordInRow[target] = drag
            drag = -1
        }
        override val complete: Boolean get() = rows.indices.all { wordInRow[it] == it }

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            rows.forEachIndexed { r, row ->
                for (col in row.offset until row.offset + row.word.length) {
                    val rect = cellRect(r, col)
                    shapes.color = if (col == secretCol) Color(1f, 0.85f, 0.35f, 1f) else cOpen
                    shapes.rect(rect[0], rect[1], rect[2] - 2f, rect[3] - 2f)
                }
            }
            words.indices.forEach { w ->
                if (rowOfWord(w) < 0 && w != drag) { val t = trayRect(traySlot.indexOf(w)); shapes.color = cAccent; shapes.rect(t[0], t[1], t[2], t[3]) }
            }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText("Crossword", W / 2f, 652f, 1.2f)
            centerText("Drag each word into its numbered row", W / 2f, 596f, 0.66f)
            rows.forEachIndexed { r, row ->
                val nr = cellRect(r, row.offset)
                font.color = Color(0.4f, 0.4f, 0.5f, 1f); centerText("${row.num}", nr[0] + 7f, nr[1] + cell - 7f, 0.45f)
                val placed = wordInRow[r]
                if (placed >= 0) words[placed].forEachIndexed { i, ch ->
                    val cr = cellRect(r, row.offset + i); font.color = cInk; centerText(ch.toString(), cr[0] + cell / 2f, cr[1] + cell / 2f, 0.8f)
                }
            }
            words.indices.forEach { w ->
                if (rowOfWord(w) < 0 && w != drag) { val t = trayRect(traySlot.indexOf(w)); font.color = Color.WHITE; centerText(words[w], t[0] + t[2] / 2f, t[1] + t[3] / 2f, 0.62f) }
            }
            if (drag >= 0) {
                smooth.rect(batch, dragPos.x - 52f, dragPos.y - 20f, 104f, 40f, cAccent)
                font.color = Color.WHITE; centerText(words[drag], dragPos.x, dragPos.y, 0.62f)
            }
            drawBackLabel()
            batch.end()
        }
    }

    /* --- The exit: tap the symbol for each letter of the secret word ---- */

    private inner class SymbolLock(
        private val word: String,
        private val glyphOf: Map<Char, Int>,
    ) : Puzzle() {
        override val instruction = "Tap the symbol for each letter"
        private val target = word.map { glyphOf[it] ?: 0 }
        private val palette = (target + listOf(5, 6, 7)).distinct()
        private val entered = ArrayList<Int>()
        private var done = false

        override fun onOpen() { entered.clear(); done = false }

        private fun slotRect(i: Int) = floatArrayOf((W - word.length * 54f) / 2f + i * 54f, 408f, 46f, 46f)
        private fun tileRect(i: Int): FloatArray {
            val cols = 4; val b = 58f; val gx = 12f
            val x0 = (W - (cols * b + (cols - 1) * gx)) / 2f
            return floatArrayOf(x0 + (i % cols) * (b + gx), 320f - (i / cols) * (b + 12f), b, b)
        }

        override fun onDown(p: Vector2) {
            if (entered.isNotEmpty() && inRect(p, slotRect(entered.size - 1))) { entered.removeAt(entered.size - 1); return }
            palette.indices.forEach { i ->
                if (inRect(p, tileRect(i)) && entered.size < word.length) {
                    entered.add(palette[i])
                    if (entered.size == word.length) { if (entered == target) done = true else { wrongFlash = 1f; entered.clear() } }
                }
            }
        }
        override val complete: Boolean get() = done

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            for (i in word.indices) { val r = slotRect(i); shapes.color = if (i < entered.size) Color(0.72f, 0.74f, 0.84f, 1f) else cSlot; shapes.rect(r[0], r[1], r[2], r[3]) }
            palette.indices.forEach { i -> val r = tileRect(i); shapes.color = cOpen; shapes.rect(r[0], r[1], r[2], r[3]) }
            shapes.end()

            batch.begin()
            palette.indices.forEach { i -> val r = tileRect(i); drawGlyph(palette[i], r[0] + r[2] / 2f, r[1] + r[3] / 2f, 16f) }
            entered.forEachIndexed { i, g -> val r = slotRect(i); drawGlyph(g, r[0] + r[2] / 2f, r[1] + r[3] / 2f, 14f) }
            font.color = cInk
            centerText("Symbol Lock", W / 2f, 652f, 1.2f)
            centerText("Spell the secret word in symbols", W / 2f, 610f, 0.7f)
            val keys = glyphOf.keys.toList()
            keys.forEachIndexed { k, ch ->
                val lx = (W - keys.size * 72f) / 2f + k * 72f + 36f
                font.color = cInk; centerText("$ch =", lx - 16f, 560f, 0.78f)
                drawGlyph(glyphOf[ch] ?: 0, lx + 16f, 558f, 12f)
            }
            font.color = cGood; centerText(word, W / 2f, 488f, 1.0f)
            if (wrongFlash > 0f) { font.color = Color(0.96f, 0.34f, 0.34f, 1f); centerText("Not quite!", W / 2f, 238f, 0.9f) }
            drawBackLabel()
            batch.end()
        }
    }

    /* ------------------------------ world ------------------------------ */

    // A room's *content*, independent of where it sits on screen. The grid layout
    // (cell rects, machine positions, walls) is computed per orientation so the
    // same six rooms reflow between a 2x3 (portrait) and 3x2 (landscape) grid.
    /** A room placed on the level grid. [gx]/[gy] is its bottom-left grid unit;
     *  [gw]x[gh] its span (>1 = a conjoined room). Units no room covers are voids
     *  (solid), which lets maps be L-shaped / irregular. */
    private inner class GridRoom(
        val id: String, val title: String, val label: String?,
        val floor: Color, val nodeColor: Color = Color.WHITE,
        val gx: Int, val gy: Int, val gw: Int = 1, val gh: Int = 1,
        val puzzle: Puzzle? = null, val requires: String? = null, val clue: String? = null,
        /** Gate this station until *all* of these rooms are solved (e.g. the exit
         *  cipher needs every clue-bearing station first). */
        val requiresAll: List<String> = emptyList(),
    )
    private data class RoomCell(val title: String, val x: Float, val y: Float, val w: Float, val h: Float, val floor: Color)
    /** A crossword row: its clue [num], answer [word], and grid [offset] (start column). */
    private data class XRow(val num: Int, val word: String, val offset: Int)

    /** One escape-room map. [doors] are the connected room-id pairs — only those
     *  get an opening, so neighbours aren't all reachable. */
    private inner class EscapeLevel(
        val name: String, val gridCols: Int, val gridRows: Int,
        val rooms: List<GridRoom>, val doors: Set<Pair<String, String>>,
        val spawnRoom: String, val exitRoom: String, val clueRoom: String? = null,
        /** Core-carrying levels: the core ids (each matches a station id), the room
         *  the loose cores sit in, and the room with the suit they're delivered to. */
        val cores: List<String> = emptyList(),
        val coreRoom: String? = null,
        val suitRoom: String? = null,
    )

    // A clue drop (parchment placeholder for now) that will explain the trick.
    private val clueNote = Note()

    private fun floorColor(i: Int) = listOf(
        Color(0.30f, 0.34f, 0.46f, 1f), Color(0.34f, 0.30f, 0.48f, 1f), Color(0.28f, 0.42f, 0.36f, 1f),
        Color(0.40f, 0.34f, 0.30f, 1f), Color(0.26f, 0.36f, 0.50f, 1f), Color(0.30f, 0.40f, 0.42f, 1f),
    )[((i % 6) + 6) % 6]

    // ---- Level 0: the Robot Lab — a 3x3 with a wide central hall, a tall word
    // display and the three machines branching off the hub (not a flat grid). ----
    private val robotLab = EscapeLevel(
        name = "Robot Lab", gridCols = 3, gridRows = 3,
        rooms = listOf(
            GridRoom("entrance", "Entrance", null, floorColor(0), gx = 0, gy = 0),
            GridRoom("panel", "Control Panel", "Control Panel", floorColor(4), Color(0.20f, 0.62f, 0.98f, 1f), gx = 1, gy = 0,
                puzzle = NumberLock("6", "Control Panel", "Count the robots", icons = ROBOT_FIELD), clue = "Display: ROBOT"),
            GridRoom("keypad", "Exit Chamber", "Exit Keypad", floorColor(5), cGood, gx = 2, gy = 0,
                puzzle = NumberLock("45", "Exit Keypad", "Enter the door code", showClues = false), requires = "poster"),
            GridRoom("atrium", "Main Lab", null, floorColor(1), gx = 0, gy = 1, gw = 2),            // wide hub (2x1)
            GridRoom("poster", "Word Display", "Word Display", floorColor(2), cGood, gx = 2, gy = 1, gh = 2,  // tall (1x2)
                puzzle = WordSearch(
                    listOf("ROBOT", "LEARN", "GEAR"),
                    arrayOf("ZXQKVWYJ".toCharArray(), "GPDLHUFM".toCharArray(), "CEVEKXQZ".toCharArray(), "WYAAJPDH".toCharArray(),
                        "KVQROBOT".toCharArray(), "XZJNCWYF".toCharArray(), "MPUDKVQX".toCharArray(), "HFCZJWYP".toCharArray()),
                    4, 3,
                )),
            GridRoom("decoder", "Symbol Decoder", "Symbol Decoder", floorColor(3), Color(0.66f, 0.48f, 0.92f, 1f), gx = 0, gy = 2,
                puzzle = Cipher(listOf('G', 'E', 'A', 'R', 'S', 'T', 'N', 'O'), listOf(0, 1, 2, 3), "GEAR"), clue = "Display: GEAR"),
            GridRoom("robot", "Robot Helper", "Robot Helper", floorColor(4), Color(1f, 0.58f, 0.20f, 1f), gx = 1, gy = 2,
                puzzle = Order(listOf("Show the robot lots of cat photos", "The robot spots the pattern", "The robot guesses 'cat!' on a new photo")), clue = "Display: LEARN"),
        ),
        doors = setOf(
            "entrance" to "panel", "entrance" to "atrium",
            "atrium" to "decoder", "atrium" to "robot", "atrium" to "poster",
            "panel" to "keypad", "poster" to "keypad",
        ),
        spawnRoom = "entrance", exitRoom = "keypad", clueRoom = "entrance",
    )

    // ---- Levels 1-4: empty layouts (no puzzles yet) with varied shapes ----
    private val vault = EscapeLevel(
        name = "The Vault", gridCols = 3, gridRows = 3,
        rooms = listOf(
            GridRoom("hall", "Hall", null, floorColor(0), gx = 0, gy = 0, gw = 3, gh = 1),  // wide
            GridRoom("west", "West Wing", null, floorColor(1), gx = 0, gy = 1, gh = 2),       // tall 1x2
            GridRoom("core", "Core", null, floorColor(2), gx = 1, gy = 1),
            GridRoom("east", "East Wing", null, floorColor(3), gx = 2, gy = 1, gh = 2),       // tall 1x2
            GridRoom("top", "Top Vault", null, floorColor(4), gx = 1, gy = 2),
        ),
        doors = setOf("hall" to "west", "hall" to "east", "west" to "core", "core" to "top"),
        spawnRoom = "hall", exitRoom = "east",
    )
    // The Tower hosts kindness-castle ("The Superhero Suit") with a core-carrying
    // twist: solve each Core station, then ferry its (unlabeled) core from the
    // Landing to that station to charge it, then bring the charged cores to the
    // Suit in the Attic to unlock the final unscramble. Station ids == core ids.
    private val tower = EscapeLevel(
        name = "The Tower", gridCols = 2, gridRows = 4,
        rooms = listOf(
            GridRoom("foyer", "Foyer", null, floorColor(0), gx = 0, gy = 0, gw = 2),          // wide
            GridRoom("honesty", "Core Charger", "Core Charger", floorColor(1), Color(0.30f, 0.60f, 0.95f, 1f), gx = 0, gy = 1,
                puzzle = Maze(listOf(
                    "I broke the vase — I'll tell the truth." to "I'll hide it and blame the cat.",
                    "I found a lost wallet — I'll hand it in." to "Finders keepers — I'll keep it.",
                    "I forgot my homework — I'll own up." to "I'll say my dog ate it.",
                ))),
            GridRoom("fairness", "Core Charger", "Core Charger", floorColor(2), Color(0.98f, 0.80f, 0.25f, 1f), gx = 1, gy = 1,
                puzzle = Fair(listOf(0, 2, 4), 9)),
            GridRoom("landing", "Landing", null, floorColor(3), gx = 0, gy = 2, gw = 2),       // wide — cores + note
            GridRoom("attic", "The Suit", "The Suit", floorColor(4), cGood, gx = 0, gy = 3,
                puzzle = Unscramble(listOf("KIND", "TRUE", "FAIR"))),
            GridRoom("kindness", "Core Charger", "Core Charger", floorColor(5), Color(0.30f, 0.80f, 0.45f, 1f), gx = 1, gy = 3,
                puzzle = Sort(listOf(
                    "Want to play with us?" to true,
                    "You can't sit here!" to false,
                    "Great try — well done!" to true,
                    "Nobody likes you." to false,
                    "Here, let me help you up." to true,
                    "That's a dumb idea." to false,
                ))),
        ),
        doors = setOf("foyer" to "fairness", "fairness" to "honesty", "honesty" to "landing", "landing" to "kindness", "kindness" to "attic"),
        spawnRoom = "foyer", exitRoom = "attic", clueRoom = "landing",
        cores = listOf("fairness", "honesty", "kindness"),
        coreRoom = "landing", suitRoom = "attic",
    )
    // The Annex hosts the "Recycling Plant" (green-lab) puzzles, ported from
    // alfredang/ai4kids: a solar-power question, a recycling-order task and a
    // pipe-circuit, all feeding the POWER exit cipher.
    private val annex = EscapeLevel(
        name = "The Annex", gridCols = 3, gridRows = 3,                                        // L-shaped (2 voids)
        rooms = listOf(
            GridRoom("lobby", "Lobby", null, floorColor(0), gx = 0, gy = 0),
            // ids match the green-lab server stations: panel / bins / circuit.
            GridRoom("panel", "Solar Panel", "Solar Panel", floorColor(3), Color(0.95f, 0.80f, 0.22f, 1f), gx = 1, gy = 0, gw = 2,  // wide
                puzzle = Mcq("Solar Panel", "Which power comes from the sun and never runs out?",
                    listOf("Solar power", "Burning coal", "Plastic bags")),
                clue = "Sun = renewable"),
            GridRoom("bins", "Recycling Plant", "Recycling Plant", floorColor(2), cGood, gx = 0, gy = 1, gh = 2,                 // tall 1x2
                puzzle = Order(listOf("Empty and rinse the bottle", "Drop it in the recycling bin", "It's made into something new!")),
                clue = "Reuse, don't bin"),
            GridRoom("circuit", "Power Circuit", "Power Circuit", floorColor(4), Color(0.30f, 0.66f, 0.95f, 1f), gx = 1, gy = 1,
                puzzle = Circuit(), clue = "Power flows"),
            GridRoom("loft", "Exit Decoder", "Exit Decoder", floorColor(5), cGood, gx = 1, gy = 2,
                puzzle = Cipher(listOf('P', 'O', 'W', 'E', 'R', 'S', 'U', 'N'), listOf(0, 1, 2, 3, 4), "POWER"),
                requiresAll = listOf("panel", "bins", "circuit")),
            // grid units (2,1) and (2,2) are left void -> the map is an L.
        ),
        doors = setOf("lobby" to "panel", "lobby" to "bins", "bins" to "circuit", "circuit" to "loft"),
        spawnRoom = "lobby", exitRoom = "loft", clueRoom = "lobby",
    )
    // The Big Hall — "Lion City": a hub-and-spoke SG-culture room. Spawn in the
    // Grand Hall (with a numbered map note), solve the four themed rooms (each
    // reveals a word), then drag the words into the crossword — a secret word
    // (LION) reads down. The exit asks you to spell it back in symbols.
    private val bigHall = EscapeLevel(
        name = "The Big Hall", gridCols = 3, gridRows = 3,                                        // + shape (3 voids)
        rooms = listOf(
            GridRoom("hall", "Grand Hall", "Crossword", floorColor(2), Color(0.95f, 0.80f, 0.22f, 1f), gx = 1, gy = 1,  // central hub
                puzzle = Crossword(
                    listOf(XRow(1, "LAKSA", 5), XRow(2, "DIWALI", 4), XRow(3, "ORCHID", 5), XRow(4, "DURIAN", 0)),
                    secretCol = 5,
                ),
                requiresAll = listOf("food", "festival", "flower", "fruit")),
            GridRoom("food", "Hawker Stall", "Hawker Stall", floorColor(0), Color(0.95f, 0.55f, 0.20f, 1f), gx = 1, gy = 0,
                puzzle = Mcq("Hawker Stall", "Which local dish is a spicy coconut-milk noodle soup?", listOf("Laksa", "Sushi", "Pizza")),
                clue = "1 = LAKSA"),
            GridRoom("festival", "Little India", "Little India", floorColor(1), Color(0.92f, 0.42f, 0.62f, 1f), gx = 0, gy = 1,
                puzzle = Unscramble(listOf("DIWALI")),
                clue = "2 = DIWALI"),
            GridRoom("flower", "Gardens", "Gardens", floorColor(3), Color(0.66f, 0.48f, 0.92f, 1f), gx = 2, gy = 1,
                puzzle = Mcq("Gardens", "What is Singapore's national flower?", listOf("Orchid", "Rose", "Tulip")),
                clue = "3 = ORCHID"),
            GridRoom("fruit", "Fruit Stall", "Fruit Stall", floorColor(4), Color(0.30f, 0.78f, 0.45f, 1f), gx = 1, gy = 2,
                puzzle = Mcq("Fruit Stall", "Which thorny fruit is the 'King of Fruits'?", listOf("Durian", "Apple", "Grape")),
                clue = "4 = DURIAN"),
            GridRoom("exit", "Exit Gate", "Exit Gate", floorColor(5), cGood, gx = 2, gy = 2,
                puzzle = SymbolLock("LION", mapOf('L' to 0, 'I' to 1, 'O' to 2, 'N' to 3)),
                requires = "hall"),
            // grid units (0,0) (0,2) (2,0) are left void -> a plus shape with an exit nub.
        ),
        doors = setOf(
            "hall" to "food", "hall" to "festival", "hall" to "flower", "hall" to "fruit", "flower" to "exit",
        ),
        spawnRoom = "hall", exitRoom = "exit", clueRoom = "hall",
    )

    // Ordered to match ESCAPE_COOP_SLUGS so each level co-op-routes to the server
    // room whose puzzles it actually contains:
    //   0 robotLab=robot-lab  1 tower=kindness-castle  2 annex=green-lab
    //   3 vault=sg-history     4 bigHall=sg-culture     (3-4 are still empty maps)
    private val levels = listOf(robotLab, tower, annex, vault, bigHall)
    private val currentLevel get() = levels[levelIndex.coerceIn(0, levels.size - 1)]
    private val rooms get() = currentLevel.rooms
    private val totalStations get() = rooms.count { it.puzzle != null }

    private val cluePos = Vector2()
    private val spawnPos = Vector2()
    private var exitRoomIndex = 0
    private var spawnRoomIndex = 0
    private var clueRoomIndex = -1

    // ---- core-carrying mechanic (kindness-castle) ----
    private val charged = HashSet<String>()    // cores sitting charged at their station
    private val delivered = HashSet<String>()  // cores delivered to the suit
    private var carrying: String? = null        // the core id currently carried
    private var carryingCharged = false
    private var coreRoomIndex = -1
    private var suitRoomIndex = -1
    private val suitPos = Vector2()
    private val coreHome = HashMap<String, Vector2>()
    private val coreDrop = HashMap<String, Vector2>() // where a core was set down (overrides its home/station)
    private val coreTmp = Vector2()
    // A charged core glows in its charger's identity colour (so the two match).
    private fun coreColor(id: String) = rooms.firstOrNull { it.id == id }?.nodeColor ?: Color(0.72f, 0.72f, 0.82f, 1f)

    private val wallColor = Color(0.12f, 0.13f, 0.22f, 1f)

    // ---- layout (rebuilt on every orientation change) ----
    private var landscape = false
    private var worldW = 480f
    private var worldH = 800f
    private var areaX = 0f; private var areaY = 0f; private var areaW = 0f; private var areaH = 0f
    private var cells = listOf<RoomCell>()
    private var walls = listOf<FloatArray>()
    private var stationPos = listOf<Vector2>()
    private val exitDoor = Vector2()
    private val titlePos = Vector2()
    private val solvedPos = Vector2()
    private val cluesAnchor = Vector2()

    /** Lay out [currentLevel] for the current orientation. The level's grid fits
     *  the play area, so each map fills the screen in both portrait and landscape. */
    private fun loadLevel() {
        val level = currentLevel
        if (landscape) {
            worldW = 800f; worldH = 480f
            areaX = 22f; areaY = 32f; areaW = worldW - 44f; areaH = worldH - 92f
            titlePos.set(worldW / 2f, worldH - 18f)
            solvedPos.set(worldW / 2f, worldH - 42f)
            actionCenter.set(worldW - 58f, 58f)
            closeCenter.set(48f, worldH - 40f)
            cluesAnchor.set(worldW - 88f, worldH - 24f)
        } else {
            worldW = 480f; worldH = 800f
            areaX = 22f; areaY = 56f; areaW = worldW - 44f; areaH = 638f
            titlePos.set(worldW / 2f, 768f)
            solvedPos.set(worldW / 2f, 735f)
            actionCenter.set(410f, 90f)
            closeCenter.set(42f, 752f)
            cluesAnchor.set(worldW / 2f, 62f)
        }
        val cw = areaW / level.gridCols; val ch = areaH / level.gridRows
        cells = level.rooms.map { r ->
            RoomCell(r.title, areaX + r.gx * cw, areaY + r.gy * ch, r.gw * cw, r.gh * ch, r.floor)
        }
        stationPos = cells.map { Vector2(it.x + it.w / 2f, it.y + it.h / 2f + 8f) }
        spawnRoomIndex = level.rooms.indexOfFirst { it.id == level.spawnRoom }.coerceAtLeast(0)
        exitRoomIndex = level.rooms.indexOfFirst { it.id == level.exitRoom }.coerceAtLeast(0)
        clueRoomIndex = level.clueRoom?.let { id -> level.rooms.indexOfFirst { it.id == id } } ?: -1
        val sp = cells[spawnRoomIndex]; spawnPos.set(sp.x + sp.w / 2f, sp.y + sp.h / 2f)
        // Push the exit door to the room's edge so it's a deliberate walk from the
        // station (e.g. the player powers the suit at centre, then heads to the door).
        val ex = cells[exitRoomIndex]; exitDoor.set(ex.x + ex.w * 0.9f, ex.y + ex.h * 0.85f)
        if (clueRoomIndex >= 0) { val cc = cells[clueRoomIndex]; cluePos.set(cc.x + cc.w / 2f, cc.y + cc.h * 0.72f) }

        // Core-carrying setup: loose cores at one end of the core room, the note on
        // the far side, the suit at the suit room's station.
        coreRoomIndex = level.coreRoom?.let { id -> level.rooms.indexOfFirst { it.id == id } } ?: -1
        suitRoomIndex = level.suitRoom?.let { id -> level.rooms.indexOfFirst { it.id == id } } ?: -1
        coreHome.clear()
        if (coreRoomIndex >= 0) {
            val cc = cells[coreRoomIndex]
            level.cores.forEachIndexed { i, id -> coreHome[id] = Vector2(cc.x + cc.w * (0.16f + 0.15f * i), cc.y + cc.h * 0.42f) }
            if (clueRoomIndex == coreRoomIndex) cluePos.set(cc.x + cc.w * 0.84f, cc.y + cc.h * 0.55f) // note on the far side
        }
        if (suitRoomIndex >= 0) suitPos.set(stationPos[suitRoomIndex])

        walls = buildLevelWalls(level, cw, ch)
    }

    /** Walls from the level's rooms: solid along every room boundary and the outer
     *  border (and around voids), with a single doorway gap per connected pair. */
    private fun buildLevelWalls(level: EscapeLevel, cw: Float, ch: Float): List<FloatArray> {
        val cols = level.gridCols; val rows = level.gridRows
        val unit = Array(cols) { IntArray(rows) { -1 } }
        level.rooms.forEachIndexed { i, r ->
            for (gx in r.gx until r.gx + r.gw) for (gy in r.gy until r.gy + r.gh)
                if (gx in 0 until cols && gy in 0 until rows) unit[gx][gy] = i
        }
        fun connected(a: Int, b: Int): Boolean {
            if (a < 0 || b < 0) return false
            val ida = level.rooms[a].id; val idb = level.rooms[b].id
            return (ida to idb) in level.doors || (idb to ida) in level.doors
        }
        val t = 8f
        val out = ArrayList<FloatArray>()
        val pairEdges = HashMap<Long, ArrayList<FloatArray>>() // [x, y, len, vertical]
        fun key(a: Int, b: Int) = minOf(a, b).toLong() * 100000L + maxOf(a, b)

        // Vertical edges (between columns).
        for (col in 0..cols) for (row in 0 until rows) {
            val a = if (col - 1 in 0 until cols) unit[col - 1][row] else -1
            val b = if (col in 0 until cols) unit[col][row] else -1
            if (a == b) continue
            val x = areaX + col * cw; val y0 = areaY + row * ch
            if (a >= 0 && b >= 0 && connected(a, b)) {
                pairEdges.getOrPut(key(a, b)) { ArrayList() }.add(floatArrayOf(x, y0, ch, 1f))
            } else {
                out.add(floatArrayOf(x - t / 2f, y0, t, ch))
            }
        }
        // Horizontal edges (between rows).
        for (row in 0..rows) for (col in 0 until cols) {
            val a = if (row - 1 in 0 until rows) unit[col][row - 1] else -1
            val b = if (row in 0 until rows) unit[col][row] else -1
            if (a == b) continue
            val y = areaY + row * ch; val x0 = areaX + col * cw
            if (a >= 0 && b >= 0 && connected(a, b)) {
                pairEdges.getOrPut(key(a, b)) { ArrayList() }.add(floatArrayOf(x0, y, cw, 0f))
            } else {
                out.add(floatArrayOf(x0, y - t / 2f, cw, t))
            }
        }
        // Open the middle shared edge of each connected pair; wall the rest.
        for (edges in pairEdges.values) {
            edges.sortBy { it[0] + it[1] }
            val doorIdx = edges.size / 2
            edges.forEachIndexed { idx, e ->
                val x = e[0]; val y = e[1]; val len = e[2]; val vertical = e[3] > 0.5f
                if (idx == doorIdx) {
                    val seg = (len - minOf(len * 0.7f, 90f)) / 2f
                    if (seg > 0f) {
                        if (vertical) { out.add(floatArrayOf(x - t / 2f, y, t, seg)); out.add(floatArrayOf(x - t / 2f, y + len - seg, t, seg)) }
                        else { out.add(floatArrayOf(x, y - t / 2f, seg, t)); out.add(floatArrayOf(x + len - seg, y - t / 2f, seg, t)) }
                    }
                } else {
                    if (vertical) out.add(floatArrayOf(x - t / 2f, y, t, len))
                    else out.add(floatArrayOf(x, y - t / 2f, len, t))
                }
            }
        }
        return out
    }

    private fun currentRoomIndex(): Int {
        val i = cells.indexOfFirst { pos.x >= it.x && pos.x < it.x + it.w && pos.y >= it.y && pos.y < it.y + it.h }
        return if (i < 0) 0 else i
    }

    /** Index of an interactable (unsolved) machine in the room the player is in. */
    private fun activeMachine(): Int? {
        val i = currentRoomIndex()
        val rd = rooms[i]
        // Solved stations stay usable so the player can revisit/review them.
        if (rd.puzzle == null) return null
        val p = stationPos[i]
        return if (dst(pos.x, pos.y, p.x, p.y) < 80f) i else null
    }

    private fun nearExit() =
        currentRoomIndex() == exitRoomIndex && dst(pos.x, pos.y, exitDoor.x, exitDoor.y) < 46f

    private fun nearClue() =
        clueRoomIndex >= 0 && currentRoomIndex() == clueRoomIndex && dst(pos.x, pos.y, cluePos.x, cluePos.y) < 72f

    /* ---- core-carrying helpers ---- */
    private fun stationIndexOf(id: String) = rooms.indexOfFirst { it.id == id }
    private fun roomIndexAt(p: Vector2): Int {
        val i = cells.indexOfFirst { p.x >= it.x && p.x < it.x + it.w && p.y >= it.y && p.y < it.y + it.h }
        return if (i < 0) currentRoomIndex() else i
    }
    private fun coreRoomOf(id: String): Int = when {
        id == carrying -> currentRoomIndex()
        id in delivered -> suitRoomIndex
        coreDrop.containsKey(id) -> roomIndexAt(coreDrop[id]!!)
        id in charged -> stationIndexOf(id)
        else -> coreRoomIndex
    }
    private fun corePos(id: String): Vector2 = when {
        id == carrying -> coreTmp.set(pos.x, pos.y + CHAR_R + 16f)
        id in delivered -> coreTmp.set(suitPos.x + (currentLevel.cores.indexOf(id) - 1) * 34f, suitPos.y - 34f)
        coreDrop.containsKey(id) -> coreTmp.set(coreDrop[id]!!)
        id in charged -> stationIndexOf(id).let { if (it >= 0) coreTmp.set(stationPos[it].x, stationPos[it].y - 34f) else coreTmp.set(0f, 0f) }
        else -> coreHome[id]?.let { coreTmp.set(it) } ?: coreTmp.set(0f, 0f)
    }
    /** A core the player can pick up right now (loose in the core room, or a charged one at its station). */
    private fun pickableCore(): String? {
        if (carrying != null) return null
        for (id in currentLevel.cores) {
            if (id in delivered) continue
            if (coreRoomOf(id) == currentRoomIndex() && dst(pos.x, pos.y, corePos(id).x, corePos(id).y) < 60f) return id
        }
        return null
    }
    private fun nearStation(id: String): Boolean {
        val i = stationIndexOf(id)
        return i >= 0 && currentRoomIndex() == i && dst(pos.x, pos.y, stationPos[i].x, stationPos[i].y) < 80f
    }
    private fun nearSuit(): Boolean =
        suitRoomIndex >= 0 && currentRoomIndex() == suitRoomIndex && dst(pos.x, pos.y, suitPos.x, suitPos.y) < 80f

    private enum class Phase { PLAYING, PUZZLE, WON }

    // The walking scene uses an orientation-aware viewport; puzzle/win overlays use
    // a fixed 480x800 portrait viewport so their tuned layouts always fit (they
    // letterbox when the device is landscape).
    private lateinit var worldCam: OrthographicCamera
    private lateinit var worldViewport: FitViewport
    private lateinit var puzzleCam: OrthographicCamera
    private lateinit var puzzleViewport: FitViewport
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var smooth: SmoothDraw // anti-aliased circles/lines through the batch
    private val layout = GlyphLayout()
    private var winTime = 0f // seconds since the win screen opened (drives the typewriter)

    private val solved = HashSet<String>()
    private val pos = Vector2()
    private var phase = Phase.PLAYING

    private var joyActive = false
    private val joyOrigin = Vector2()
    private val joyKnob = Vector2()

    private var activePuzzle: Puzzle? = null
    private var activeStationId: String? = null
    private var wrongFlash = 0f
    private var flashMsg = ""
    private var prevTouched = false
    private var puzzleHadMistake = false
    // The open puzzle is solved and now in (locked) review mode until the X is tapped.
    private var puzzleSolved = false
    private var solvedTime = 0f // seconds the "Solved!" banner has been showing
    private val touch = Vector2()

    override fun create() {
        worldCam = OrthographicCamera()
        puzzleCam = OrthographicCamera()
        shapes = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont()
        smooth = SmoothDraw()
        font.setUseIntegerPositions(false)
        loadLevel()
        worldViewport = FitViewport(worldW, worldH, worldCam)
        puzzleViewport = FitViewport(W, H, puzzleCam)
        pos.set(spawnPos)
        Gdx.input.setCatchKey(com.badlogic.gdx.Input.Keys.BACK, false)
    }

    /* ----------------------------- helpers ----------------------------- */

    /** Unproject through whichever viewport owns the current phase. */
    private fun unprojectTouch(): Vector2 {
        touch.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
        return (if (phase == Phase.PLAYING) worldViewport else puzzleViewport).unproject(touch)
    }

    private val actionR = 46f
    private val closeR = 26f
    private val actionCenter = Vector2(410f, 90f) // repositioned per orientation in buildLayout
    private val closeCenter = Vector2(42f, 752f)
    private val backTmp = Vector2()
    // Puzzle close (X), top-right of the panel — follows the orientation-aware panel.
    private val backCenter: Vector2 get() = backTmp.set(PANEL_X + PANEL_W - 36f, PANEL_Y + PANEL_H - 32f)
    private val backR = 26f

    private fun dst(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by; return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    /** True if a CHAR_R circle at (cx,cy) overlaps any wall — used for collision. */
    private fun blocked(cx: Float, cy: Float): Boolean {
        val r = CHAR_R - 2f
        for (w in walls) {
            val px = cx.coerceIn(w[0], w[0] + w[2])
            val py = cy.coerceIn(w[1], w[1] + w[3])
            val dx = cx - px; val dy = cy - py
            if (dx * dx + dy * dy < r * r) return true
        }
        return false
    }

    private fun within(p: Vector2, c: Vector2, r: Float) = dst(p.x, p.y, c.x, c.y) < r
    private fun inRect(p: Vector2, r: FloatArray) = p.x >= r[0] && p.x <= r[0] + r[2] && p.y >= r[1] && p.y <= r[1] + r[3]
    private fun sgn(x: Int) = if (x > 0) 1 else if (x < 0) -1 else 0

    private fun isLocked(i: Int): Boolean {
        val r = rooms[i]
        // The suit's final puzzle stays locked until every core has been delivered.
        if (currentLevel.suitRoom == r.id && delivered.size < currentLevel.cores.size) return true
        if (r.requires != null && r.requires !in solved) return true
        return r.requiresAll.any { it !in solved }
    }

    /** The first still-unsolved room this station is waiting on (for the lock message). */
    private fun lockedOn(i: Int): String? {
        val r = rooms[i]
        r.requires?.let { if (it !in solved) return it }
        return r.requiresAll.firstOrNull { it !in solved }
    }
    private fun labelOf(id: String) = rooms.firstOrNull { it.id == id }?.title ?: id
    private fun collectedClues() = rooms
        .filter { it.id in solved && it.clue != null }
        .map { it.clue!! }

    /** One of 8 simple shape pictograms (stand-ins for the source's emoji), drawn
     *  anti-aliased through [batch] (call inside a batch pass, not a shapes pass). */
    private fun drawGlyph(kind: Int, cx: Float, cy: Float, s: Float) {
        val col = glyphColors[kind % glyphColors.size]
        when (kind % 8) {
            0 -> smooth.circle(batch, cx, cy, s, col)
            1 -> smooth.rect(batch, cx - s, cy - s, 2 * s, 2 * s, col)
            2 -> smooth.triangle(batch, cx - s, cy - s, 2 * s, 2 * s, col)
            3 -> { smooth.triangle(batch, cx - s, cy, 2 * s, s, col); smooth.triangle(batch, cx - s, cy - s, 2 * s, s, col, pointDown = true) }
            4 -> { smooth.circle(batch, cx, cy, s, col); smooth.circle(batch, cx, cy, s * 0.5f, Color.WHITE) }
            5 -> { smooth.rect(batch, cx - s * 0.35f, cy - s, s * 0.7f, 2 * s, col); smooth.rect(batch, cx - s, cy - s * 0.35f, 2 * s, s * 0.7f, col) }
            6 -> { smooth.line(batch, cx - s, cy - s, cx + s, cy + s, s * 0.5f, col); smooth.line(batch, cx - s, cy + s, cx + s, cy - s, s * 0.5f, col) }
            7 -> { smooth.circle(batch, cx, cy, s * 0.5f, col); smooth.line(batch, cx, cy - s, cx, cy + s, s * 0.4f, col); smooth.line(batch, cx - s, cy, cx + s, cy, s * 0.4f, col) }
        }
    }

    /** A little robot pictogram for the counting field (anti-aliased, via [batch]). */
    private fun drawRobot(cx: Float, cy: Float, s: Float) {
        val teal = Color(0.30f, 0.78f, 0.78f, 1f)
        smooth.line(batch, cx, cy + s, cx, cy + s * 1.5f, 3f, teal)
        smooth.circle(batch, cx, cy + s * 1.5f, 3f, teal)
        smooth.rect(batch, cx - s, cy - s, 2 * s, 2 * s, teal)
        smooth.circle(batch, cx - s * 0.4f, cy + s * 0.1f, s * 0.22f, cInk)
        smooth.circle(batch, cx + s * 0.4f, cy + s * 0.1f, s * 0.22f, cInk)
    }

    /* ----------------------------- input ----------------------------- */

    private fun handleInput(dt: Float) {
        val touched = Gdx.input.isTouched()
        val justDown = touched && !prevTouched
        val justUp = !touched && prevTouched

        when (phase) {
            Phase.PLAYING -> {
                if (justDown) {
                    val p = unprojectTouch()
                    when {
                        within(p, closeCenter, closeR) -> { onFinish(0); return }
                        within(p, actionCenter, actionR) -> doAction()
                        else -> { joyActive = true; joyOrigin.set(p); joyKnob.set(p) }
                    }
                }
                if (joyActive && touched) {
                    val p = unprojectTouch()
                    val dir = Vector2(p).sub(joyOrigin)
                    if (dir.len() > JOY_RADIUS) dir.setLength(JOY_RADIUS)
                    joyKnob.set(joyOrigin).add(dir)
                    val v = Vector2(dir).scl(1f / JOY_RADIUS)
                    // Move each axis independently and stop at walls (so the player
                    // slides along them and passes cleanly through doorways).
                    val nx = (pos.x + v.x * SPEED * dt).coerceIn(areaX + CHAR_R, areaX + areaW - CHAR_R)
                    if (!blocked(nx, pos.y)) pos.x = nx
                    val ny = (pos.y + v.y * SPEED * dt).coerceIn(areaY + CHAR_R, areaY + areaH - CHAR_R)
                    if (!blocked(pos.x, ny)) pos.y = ny
                }
                if (!touched) joyActive = false
            }
            Phase.PUZZLE -> {
                val pz = activePuzzle
                // Once solved the puzzle stays open for review — only the X closes it.
                if (justDown) {
                    val p = unprojectTouch()
                    if (within(p, backCenter, backR)) closePuzzle() else if (!puzzleSolved) pz?.onDown(p)
                } else if (touched && !puzzleSolved) {
                    pz?.onDrag(unprojectTouch())
                }
                if (justUp && !puzzleSolved) pz?.onUp(unprojectTouch())
                if (wrongFlash > 0f) puzzleHadMistake = true
                if (pz != null && pz.complete && !puzzleSolved) {
                    puzzleSolved = true // register the solve once, then let the player review
                    activeStationId?.let { id ->
                        solved.add(id)
                        // The exit-room lock (keypad / decoder) is a local gate, not a server station.
                        if (id != currentLevel.exitRoom) coop?.reportSolve(id, firstTry = !puzzleHadMistake)
                    }
                }
            }
            Phase.WON -> if (justDown) onFinish(starsForWin())
        }

        if (wrongFlash > 0f) wrongFlash -= dt
        prevTouched = touched
    }

    private fun doAction() {
        val lvl = currentLevel
        if (lvl.cores.isNotEmpty()) {
            val held = carrying
            if (held != null) {
                if (!carryingCharged && nearStation(held) && held in solved) {
                    charged.add(held); coreDrop.remove(held); carrying = null; flashMsg = "Core charged!"; wrongFlash = 0f; return
                }
                if (carryingCharged && nearSuit()) {
                    delivered.add(held); coreDrop.remove(held); carrying = null; flashMsg = "Core powers the suit!"; wrongFlash = 0f; return
                }
                // Otherwise: set the core down right here so the player can swap it.
                coreDrop[held] = Vector2(pos.x, pos.y)
                if (carryingCharged) charged.add(held)
                carrying = null
                return
            } else {
                val pick = pickableCore()
                if (pick != null) { carrying = pick; carryingCharged = pick in charged; charged.remove(pick); coreDrop.remove(pick); return }
            }
        }
        val mi = activeMachine()
        if (mi != null) {
            val rd = rooms[mi]
            if (isLocked(mi)) {
                flashMsg = if (lvl.suitRoom == rd.id && delivered.size < lvl.cores.size) "Bring all the cores to power the suit"
                    else "Locked — solve \"${labelOf(lockedOn(mi) ?: "")}\" first"
                wrongFlash = 1.4f; return
            }
            // A solved station re-opens in review mode (no re-scramble); a fresh one resets.
            val alreadySolved = rd.id in solved
            activePuzzle = rd.puzzle; activeStationId = rd.id
            if (!alreadySolved) rd.puzzle!!.onOpen()
            puzzleSolved = alreadySolved
            wrongFlash = 0f; puzzleHadMistake = false; phase = Phase.PUZZLE; return
        }
        if (nearClue()) {
            activePuzzle = clueNote; activeStationId = null; puzzleSolved = false; phase = Phase.PUZZLE; return
        }
        if (nearExit()) {
            if (solved.size >= totalStations) phase = Phase.WON
            else { flashMsg = "Open the Exit Keypad first!"; wrongFlash = 1.2f }
        }
    }

    private fun closePuzzle() {
        activePuzzle = null; activeStationId = null; phase = Phase.PLAYING; joyActive = false; wrongFlash = 0f; puzzleSolved = false
    }

    private fun starsForWin() = if (totalStations == 0) 0 else 5

    /* ----------------------------- render ----------------------------- */

    override fun render() {
        val dt = min(Gdx.graphics.deltaTime, 1f / 30f)
        // Co-op: fold the team's shared solved set in, and report which room we're
        // in for presence. Teammates' solves unlock the same gates for everyone.
        coop?.let { c ->
            c.state?.let { solved.addAll(it.solved) }
            c.atStation = rooms[currentRoomIndex()].id
        }
        handleInput(dt)
        winTime = if (phase == Phase.WON) winTime + dt else 0f
        solvedTime = if (puzzleSolved) solvedTime + dt else 0f

        Gdx.gl.glClearColor(0.06f, 0.06f, 0.10f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)

        when (phase) {
            Phase.PUZZLE -> { usePuzzleCam(); activePuzzle?.draw(); if (puzzleSolved && solvedTime < 2.5f) drawSolvedBanner() }
            Phase.WON -> { usePuzzleCam(); drawWin() }
            else -> { useWorldCam(); drawScene(); drawHud() }
        }
    }

    private fun useWorldCam() {
        worldViewport.apply()
        shapes.projectionMatrix = worldCam.combined
        batch.projectionMatrix = worldCam.combined
    }

    private fun usePuzzleCam() {
        puzzleViewport.apply()
        shapes.projectionMatrix = puzzleCam.combined
        batch.projectionMatrix = puzzleCam.combined
    }

    // Distinct token colours for co-op teammates (keyed by learner id).
    private val mateColors = arrayOf(
        Color(0.36f, 0.78f, 0.98f, 1f), Color(0.96f, 0.55f, 0.80f, 1f),
        Color(0.55f, 0.85f, 0.45f, 1f), Color(0.98f, 0.62f, 0.30f, 1f),
        Color(0.70f, 0.60f, 0.95f, 1f),
    )
    private fun playerColor(id: Int) = mateColors[((id % mateColors.size) + mateColors.size) % mateColors.size]
    // Teammates are laid out in a row near the top of the room (we only know which
    // room they're in, not their exact position).
    private fun mateX(i: Int, n: Int, c: RoomCell) = c.x + c.w / 2f + (i - (n - 1) / 2f) * 46f
    private fun mateY(c: RoomCell) = c.y + c.h - 44f

    private fun drawScene() {
        val curIdx = currentRoomIndex()
        val mi = activeMachine()
        val nearEx = nearExit()
        val nearC = nearClue()
        val exitOpen = solved.size >= totalStations
        // Co-op teammates standing in this room (avatar tokens, drawn below).
        val mates = coop?.state?.let { st ->
            st.players.filter { it.learnerId != st.you && it.atStation == rooms[curIdx].id }
        } ?: emptyList()

        // Context action — carrying a core to charge/power takes priority over a machine.
        val held = carrying
        val actLabel = when {
            held != null && !carryingCharged && nearStation(held) && held in solved -> "CHARGE"
            held != null && carryingCharged && nearSuit() -> "POWER"
            held != null -> "DROP"
            currentLevel.cores.isNotEmpty() && pickableCore() != null -> "TAKE"
            mi != null && isLocked(mi) -> "LOCK"
            mi != null && rooms[mi].id in solved -> "VIEW"
            mi != null -> "USE"
            nearC -> "READ"
            nearEx -> if (exitOpen) "EXIT" else "LOCK"
            else -> ""
        }
        val canAct = actLabel.isNotEmpty()

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        // Backdrop + every room's floor + the walls.
        shapes.color = wallColor
        shapes.rect(0f, 0f, worldW, worldH)
        cells.forEach { c -> shapes.color = c.floor; shapes.rect(c.x, c.y, c.w, c.h) }
        shapes.color = wallColor
        walls.forEach { shapes.rect(it[0], it[1], it[2], it[3]) }

        // Exit door (sits in the Exit Chamber — the last room).
        if (curIdx == exitRoomIndex) {
            shapes.color = if (exitOpen) Color(0.42f, 0.29f, 0.17f, 1f) else Color(0.4f, 0.2f, 0.2f, 1f)
            shapes.rect(exitDoor.x - 18f, exitDoor.y - 28f, 36f, 56f)
            if (nearEx) { shapes.color = Color.WHITE; shapes.rect(exitDoor.x - 22f, exitDoor.y + 28f, 44f, 4f) }
        }

        // Only the current room's machine is drawn (others are hidden anyway).
        rooms[curIdx].puzzle?.let {
            val p = stationPos[curIdx]
            val done = rooms[curIdx].id in solved
            val locked = isLocked(curIdx)
            val charger = rooms[curIdx].id in currentLevel.cores
            shapes.color = when {
                locked -> Color(0.5f, 0.5f, 0.56f, 1f)
                charger -> rooms[curIdx].nodeColor   // chargers keep their identity colour to match their core
                done -> cGood
                else -> rooms[curIdx].nodeColor
            }
            shapes.circle(p.x, p.y, 30f)
            shapes.color = Color.WHITE
            shapes.circle(p.x, p.y, 14f)
            if (locked) { shapes.color = cInk; shapes.rect(p.x - 6f, p.y - 4f, 12f, 11f) }
            if (mi == curIdx) { shapes.color = Color.WHITE; shapes.circle(p.x, p.y + 46f, 5f) }
        }

        // Clue drop (dummy art placeholder) — a parchment note in the clue room.
        if (clueRoomIndex >= 0 && curIdx == clueRoomIndex) {
            shapes.color = Color(0.96f, 0.91f, 0.66f, 1f)
            shapes.rect(cluePos.x - 15f, cluePos.y - 18f, 30f, 36f)
            shapes.color = Color(0.78f, 0.68f, 0.38f, 1f)
            shapes.rect(cluePos.x - 15f, cluePos.y + 12f, 30f, 6f)
            if (nearC) { shapes.color = Color.WHITE; shapes.circle(cluePos.x, cluePos.y + 34f, 5f) }
        }

        // Fog of war — cover every room the player isn't standing in.
        shapes.color = Color(0.05f, 0.05f, 0.10f, 0.95f)
        cells.forEachIndexed { idx, c -> if (idx != curIdx) shapes.rect(c.x, c.y, c.w, c.h) }

        shapes.end()

        // Character tokens and the on-screen buttons are drawn through the batch
        // as anti-aliased circles (SmoothDraw), over the fog.
        batch.begin()
        // Cores (kindness-castle): glow when charged/delivered; only those in this
        // room — or the one being carried — are visible (everything else is fogged).
        currentLevel.cores.forEach { id ->
            if (id == carrying || coreRoomOf(id) == curIdx) {
                val cp = corePos(id)
                // Cores stay an anonymous grey until charged — only then do they
                // glow and reveal their colour (and stay lit while carried).
                val live = id in charged || id in delivered || (id == carrying && carryingCharged)
                if (live) smooth.circle(batch, cp.x, cp.y, 18f, Color(1f, 1f, 1f, 0.45f))
                smooth.circle(batch, cp.x, cp.y, 13f, if (live) coreColor(id) else Color(0.60f, 0.62f, 0.70f, 1f))
                smooth.circle(batch, cp.x, cp.y, 5f, Color(1f, 1f, 1f, 0.9f))
            }
        }
        // Co-op teammate avatars (a coloured character token per player here).
        mates.forEachIndexed { i, m ->
            val mx = mateX(i, mates.size, cells[curIdx]); val my = mateY(cells[curIdx])
            smooth.circle(batch, mx, my, 16f, playerColor(m.learnerId))
            smooth.circle(batch, mx - 5f, my + 3f, 3f, cInk)
            smooth.circle(batch, mx + 5f, my + 3f, 3f, cInk)
        }
        // Player (drawn over the fog so it's never clipped at doorways).
        smooth.circle(batch, pos.x, pos.y, CHAR_R, Color(0.98f, 0.80f, 0.16f, 1f))
        smooth.circle(batch, pos.x - 7f, pos.y + 4f, 4f, cInk)
        smooth.circle(batch, pos.x + 7f, pos.y + 4f, 4f, cInk)
        // Floating joystick.
        if (joyActive) {
            smooth.circle(batch, joyOrigin.x, joyOrigin.y, JOY_RADIUS, Color(1f, 1f, 1f, 0.25f))
            smooth.circle(batch, joyKnob.x, joyKnob.y, 30f, cAccent)
        }
        // Action + close buttons.
        smooth.circle(batch, actionCenter.x, actionCenter.y, actionR, if (canAct) cAccent else Color(1f, 1f, 1f, 0.25f))
        smooth.circle(batch, closeCenter.x, closeCenter.y, closeR, Color.WHITE)

        font.color = Color.WHITE
        rooms[curIdx].label?.let { centerText(it, stationPos[curIdx].x, stationPos[curIdx].y - 40f, 0.9f) }
        if (clueRoomIndex >= 0 && curIdx == clueRoomIndex) centerText("Lab Note", cluePos.x, cluePos.y - 30f, 0.75f)
        if (curIdx == exitRoomIndex) centerText(if (exitOpen) "EXIT" else "LOCKED", exitDoor.x, exitDoor.y - 40f, 0.75f)
        font.color = Color.WHITE
        centerText(actLabel, actionCenter.x, actionCenter.y, 0.85f)
        font.color = cInk
        centerText("X", closeCenter.x, closeCenter.y, 1.1f)
        // Co-op teammate name under each avatar token.
        font.color = Color(0.88f, 0.93f, 1f, 1f)
        mates.forEachIndexed { i, m ->
            centerText(m.name.substringBefore(' '), mateX(i, mates.size, cells[curIdx]), mateY(cells[curIdx]) - 24f, 0.6f)
        }
        batch.end()

        if (wrongFlash > 0f) {
            batch.begin(); font.color = Color(0.96f, 0.34f, 0.34f, 1f)
            centerText(flashMsg, worldW / 2f, areaY + 28f, 1.0f)
            batch.end()
        }
    }

    private fun drawDimAndPanel() {
        shapes.color = Color(0f, 0f, 0f, 0.6f)
        shapes.rect(0f, 0f, W, H)
        shapes.color = Color.WHITE
        shapes.rect(PANEL_X, PANEL_Y, PANEL_W, PANEL_H)
        shapes.color = Color(0.92f, 0.40f, 0.40f, 1f)
        shapes.circle(backCenter.x, backCenter.y, backR)
    }

    private fun drawBackLabel() {
        font.color = Color.WHITE
        centerText("X", backCenter.x, backCenter.y, 1.25f)
    }

    /** Shown over a solved puzzle (review mode): the player taps X to continue.
     *  Centred on the panel so it never strands in the letterbox margin. */
    private fun drawSolvedBanner() {
        val bw = 420f; val bh = 56f
        val bx = (W - bw) / 2f
        val by = PANEL_Y + PANEL_H / 2f - bh / 2f
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        // Dark border so the banner stays distinct from green-lit options behind it.
        shapes.color = cInk
        shapes.rect(bx - 5f, by - 5f, bw + 10f, bh + 10f)
        shapes.color = cGood
        shapes.rect(bx, by, bw, bh)
        shapes.end()
        batch.begin()
        font.color = Color.WHITE
        centerText("Solved! Tap X to continue", W / 2f, by + bh / 2f, 1.05f)
        batch.end()
    }

    private fun drawWin() {
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.6f)
        shapes.rect(0f, 0f, W, H)
        shapes.color = cAccent
        val cardW = minOf(W - 100f, 560f) // a centred card, not a full-width banner in landscape
        shapes.rect((W - cardW) / 2f, 280f, cardW, 240f)
        shapes.end()

        batch.begin()
        // Anti-aliased confetti drifting up the celebration card (SmoothDraw).
        for (k in 0 until 14) {
            val seed = k * 127.1f
            val cx = 70f + (seed * 0.73f % (W - 140f))
            val cy = 280f + ((seed * 1.9f + winTime * 60f) % 240f)
            val r = 4f + (k % 3)
            smooth.circle(batch, cx, cy, r, confettiColors[k % confettiColors.size])
        }

        // Typewriter title, then the reward line with an inline star pictogram.
        val title = "You escaped!"
        val reveal = (winTime * 18f).toInt() // ~18 chars/sec
        RichText.draw(batch, font, layout, title, W / 2f, 466f, 1.8f, Color.WHITE, reveal)
        if (reveal >= title.length) {
            RichText.draw(
                batch, font, layout, "+5 [#FFD23B]stars[] :g7:", W / 2f, 408f, 1.2f, Color.WHITE,
                iconSize = 9f, icon = { _, x, y, s -> smooth.circle(batch, x, y, s, Color(0.95f, 0.80f, 0.22f, 1f)) },
            )
            font.color = Color(1f, 1f, 1f, 0.8f)
            centerText("Tap to go back", W / 2f, 344f, 0.95f)
        }
        batch.end()
    }

    private fun drawHud() {
        batch.begin()
        font.color = Color.WHITE
        centerText(cells[currentRoomIndex()].title, titlePos.x, titlePos.y, 1.2f)
        val coopState = coop?.state
        if (coopState != null) {
            centerText("Team ${coopState.solved.size}/${coopState.total} solved", solvedPos.x, solvedPos.y, 0.85f)
            if (coopState.players.size > 1) {
                val names = coopState.players.joinToString(", ") { it.name.substringBefore(' ') }
                font.color = Color(0.7f, 0.9f, 1f, 1f)
                centerText(names, solvedPos.x, solvedPos.y - 20f, 0.6f)
                font.color = Color.WHITE
            }
        } else {
            centerText("${solved.size}/$totalStations solved", solvedPos.x, solvedPos.y, 0.9f)
        }
        val clues = collectedClues()
        if (clues.isNotEmpty()) {
            font.color = Color(1f, 0.9f, 0.55f, 1f)
            centerText("Clues:", cluesAnchor.x, cluesAnchor.y, 0.78f)
            clues.forEachIndexed { i, c -> centerText(c, cluesAnchor.x, cluesAnchor.y - 18f - i * 18f, 0.7f) }
        }
        batch.end()
    }

    private fun centerText(text: String, cx: Float, cy: Float, scale: Float) {
        font.data.setScale(scale)
        layout.setText(font, text)
        font.draw(batch, layout, cx - layout.width / 2f, cy + layout.height / 2f)
    }

    private fun wrapText(text: String, cx: Float, topY: Float, width: Float, scale: Float) {
        font.data.setScale(scale)
        layout.setText(font, text, font.color, width, Align.center, true)
        font.draw(batch, layout, cx - width / 2f, topY)
    }

    /** Left-aligned wrapped text, vertically centred on [centerY]. */
    private fun wrapLeft(text: String, x: Float, centerY: Float, width: Float, scale: Float) {
        font.data.setScale(scale)
        layout.setText(font, text, font.color, width, Align.left, true)
        font.draw(batch, layout, x, centerY + layout.height / 2f)
    }

    override fun resize(width: Int, height: Int) {
        // Reflow the room grid for the new orientation, keeping the player in the
        // same room (moved to its new centre).
        val keepRoom = currentRoomIndex()
        landscape = width > height
        // Widen the puzzle canvas to the screen aspect in landscape (height stays
        // 800), so the overlay fills the screen with no side letterboxing.
        W = if (landscape) (width.toFloat() / height * H).coerceIn(480f, 1700f) else 480f
        loadLevel()
        worldViewport.worldWidth = worldW
        worldViewport.worldHeight = worldH
        worldViewport.update(width, height, true)
        puzzleViewport.setWorldSize(W, H)
        puzzleViewport.update(width, height, true)
        val c = cells[keepRoom]
        pos.set(c.x + c.w / 2f, c.y + c.h / 2f)
    }

    override fun dispose() {
        shapes.dispose(); batch.dispose(); font.dispose(); smooth.dispose()
    }
}
