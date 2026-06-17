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
class EscapeGdxGame(private val onFinish: (Int) -> Unit) : ApplicationAdapter() {

    private companion object {
        const val W = 480f
        const val H = 800f
        const val SPEED = 230f
        const val JOY_RADIUS = 72f
        const val CHAR_R = 22f

        const val PANEL_X = 28f
        const val PANEL_Y = 130f
        const val PANEL_W = W - 56f
        const val PANEL_H = 540f

        // The Control Panel "count the robots" field: -1 = robot, else glyph kind.
        // Six robots hide among the other machines (matches the source clue row).
        val ROBOT_FIELD = listOf(2, -1, -1, 1, -1, 0, -1, 7, 4, -1, 2, -1)
    }

    private val cInk = Color(0.16f, 0.14f, 0.30f, 1f)
    private val cAccent = Color(0.45f, 0.30f, 0.92f, 1f)
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
        private val kx0 = (W - (cols * btn + (cols - 1) * gapx)) / 2f
        private val topRowY = 386f

        override fun onOpen() { entered.setLength(0); done = false }

        private fun keyRect(i: Int): FloatArray {
            val r = i / cols; val c = i % cols
            return floatArrayOf(kx0 + c * (btn + gapx), topRowY - r * (btn + gapy), btn, btn)
        }

        private fun iconCenter(i: Int): Vector2 {
            val c = i % 4; val r = i / 4
            val cellW = 78f
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
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            icons?.forEachIndexed { i, k ->
                val c = iconCenter(i)
                if (k < 0) drawRobot(c.x, c.y, 15f) else drawGlyph(k, c.x, c.y, 14f)
            }
            shapes.color = cSlot
            shapes.rect((W - 200f) / 2f, 452f, 200f, 46f)
            keys.indices.forEach { i ->
                val r = keyRect(i)
                shapes.color = when (keys[i]) { "OK" -> cGood; "<" -> Color(0.9f, 0.5f, 0.3f, 1f); else -> cAccent }
                shapes.rect(r[0], r[1], r[2], r[3])
            }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText(heading, W / 2f, 650f, 1.2f)
            centerText(prompt, W / 2f, 626f, 0.82f)
            if (showClues) {
                val cl = collectedClues()
                if (cl.isNotEmpty()) { font.color = cGood; centerText(cl.joinToString("   "), W / 2f, 540f, 0.78f) }
            }
            font.color = cInk
            centerText(if (entered.isEmpty()) "_" else entered.toString(), W / 2f, 468f, 1.3f)
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

        private fun cardRect(i: Int) = floatArrayOf(56f, 516f - i * 108f, 368f, 92f)

        override fun onDown(p: Vector2) {
            display.indices.forEach { j ->
                if (inRect(p, cardRect(j))) {
                    val step = display[j]
                    if (step in seq) seq.clear() // tap a placed card to start over
                    else {
                        seq.add(step)
                        if (seq.size == steps.size && seq != steps.indices.toList()) { wrongFlash = 1f; seq.clear() }
                    }
                }
            }
        }

        override val complete: Boolean get() = seq == steps.indices.toList()

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            display.indices.forEach { j ->
                val r = cardRect(j)
                val placed = display[j] in seq
                shapes.color = if (placed) cGood else cOpen
                shapes.rect(r[0], r[1], r[2], r[3])
                shapes.color = if (placed) Color.WHITE else cAccent
                shapes.circle(r[0] + 28f, r[1] + r[3] / 2f, 18f)
            }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText("Robot Helper", W / 2f, 652f, 1.2f)
            centerText("Teach the robot to spot cats", W / 2f, 628f, 0.78f)
            display.indices.forEach { j ->
                val r = cardRect(j)
                val placed = display[j] in seq
                font.color = if (placed) cGood else cAccent
                val badge = if (placed) (seq.indexOf(display[j]) + 1).toString() else "?"
                centerText(badge, r[0] + 28f, r[1] + r[3] / 2f + 6f, 1.0f)
                font.color = cInk
                wrapText(steps[display[j]], r[0] + 56f + (r[2] - 70f) / 2f, r[1] + r[3] / 2f + 22f, r[2] - 80f, 0.68f)
            }
            font.color = cInk
            centerText(instruction, W / 2f, 168f, 0.78f)
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

        private fun legendRect(k: Int): FloatArray {
            val c = k % 4; val r = k / 4
            val cellW = 104f; val cellH = 40f
            val x0 = (W - 4 * cellW) / 2f
            return floatArrayOf(x0 + c * cellW, 598f - r * (cellH + 6f), cellW - 8f, cellH)
        }

        private fun tileRect(i: Int): FloatArray {
            val cols = 5; val c = i % cols; val r = i / cols
            val b = 56f; val gx = 12f; val gy = 10f
            val x0 = (W - (cols * b + (cols - 1) * gx)) / 2f
            return floatArrayOf(x0 + c * (b + gx), 376f - r * (b + gy), b, b)
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
            // Legend: glyph = letter.
            legendLetters.indices.forEach { k ->
                val r = legendRect(k)
                shapes.color = cOpen
                shapes.rect(r[0], r[1], r[2], r[3])
                drawGlyph(k, r[0] + 20f, r[1] + r[3] / 2f, 12f)
            }
            // Coded message glyphs.
            coded.forEachIndexed { i, k ->
                drawGlyph(k, (W - coded.size * 56f) / 2f + i * 56f + 28f, 472f, 16f)
            }
            // Answer slots.
            for (i in answer.indices) {
                shapes.color = if (i < typed.length) cGood else cSlot
                shapes.rect((W - answer.length * 50f) / 2f + i * 50f + 4f, 410f, 42f, 42f)
            }
            // Letter tiles.
            tileLetters.indices.forEach { i ->
                val r = tileRect(i)
                shapes.color = if (tileLetters[i] == '<') Color(0.9f, 0.5f, 0.3f, 1f) else cAccent
                shapes.rect(r[0], r[1], r[2], r[3])
            }
            shapes.end()

            batch.begin()
            font.color = cInk
            centerText("Symbol Decoder", W / 2f, 652f, 1.1f)
            legendLetters.indices.forEach { k ->
                val r = legendRect(k)
                font.color = cInk
                centerText("= ${legendLetters[k]}", r[0] + r[2] - 26f, r[1] + r[3] / 2f + 6f, 0.85f)
            }
            font.color = cInk
            centerText("Secret word:", W / 2f, 506f, 0.72f)
            font.color = cGood
            typed.forEachIndexed { i, ch ->
                centerText(ch.toString(), (W - answer.length * 50f) / 2f + i * 50f + 25f, 431f + 6f, 1.1f)
            }
            tileLetters.indices.forEach { i ->
                val r = tileRect(i)
                font.color = Color.WHITE
                centerText(tileLetters[i].toString(), r[0] + r[2] / 2f, r[1] + r[3] / 2f + 6f, 1.0f)
            }
            if (wrongFlash > 0f) { font.color = Color(0.96f, 0.34f, 0.34f, 1f); centerText("Not quite!", W / 2f, 150f, 0.9f) }
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
        private val x0 = 84f
        private val top = 582f
        private val found = HashSet<String>()
        private val foundCells = HashSet<Int>()
        private var startCell = -1
        private var dragCell = -1

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
                if (match != null && match !in found && isWordRevealed(match)) {
                    found.add(match); foundCells.addAll(cells)
                }
            }
            startCell = -1; dragCell = -1
        }

        override val complete: Boolean get() = found.size == words.size

        override fun draw() {
            shapes.begin(ShapeRenderer.ShapeType.Filled)
            drawDimAndPanel()
            for (row in 0 until n) for (col in 0 until n) {
                val idx = row * n + col
                shapes.color = when {
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
                font.color = if (idx in foundCells || (complete && row == crossRow && col == crossCol)) Color.WHITE else cInk
                centerText(grid[row][col].toString(), cx(col), cy(row) + 6f, 0.85f)
            }
            // Words to find: masked until their machine lights them up.
            font.color = cInk
            val list = words.joinToString("   ") {
                when { it in found -> "[$it]"; isWordRevealed(it) -> it; else -> "?".repeat(it.length) }
            }
            centerText(list, W / 2f, 196f, 0.8f)
            if (complete) {
                font.color = cGood
                centerText("They cross at Column ${crossCol + 1}, Row ${crossRow + 1}!", W / 2f, 166f, 0.78f)
            } else {
                centerText(instruction, W / 2f, 166f, 0.7f)
            }
            drawBackLabel()
            batch.end()
        }
    }

    /* ------------------------------ world ------------------------------ */

    // A room's *content*, independent of where it sits on screen. The grid layout
    // (cell rects, machine positions, walls) is computed per orientation so the
    // same six rooms reflow between a 2x3 (portrait) and 3x2 (landscape) grid.
    private inner class RoomDef(
        val id: String, val title: String, val label: String?,
        val floor: Color, val nodeColor: Color, val puzzle: Puzzle?,
        val requires: String? = null, val clue: String? = null,
    )
    private data class RoomCell(val title: String, val x: Float, val y: Float, val w: Float, val h: Float, val floor: Color)

    // Order matters: rooms fill the grid row-major from the bottom-left, so the
    // Entrance (spawn) is always the first cell and the Exit Chamber the last.
    private val roomDefs = listOf(
        RoomDef("entrance", "Entrance", null, Color(0.30f, 0.34f, 0.46f, 1f), Color.WHITE, null),
        RoomDef("panel", "Control Panel", "Control Panel", Color(0.26f, 0.36f, 0.50f, 1f), Color(0.20f, 0.62f, 0.98f, 1f),
            NumberLock("6", "Control Panel", "Count the robots", icons = ROBOT_FIELD),
            clue = "Display: ROBOT"),
        RoomDef("decoder", "Symbol Decoder", "Symbol Decoder", Color(0.34f, 0.30f, 0.48f, 1f), Color(0.66f, 0.48f, 0.92f, 1f),
            Cipher(
                legendLetters = listOf('G', 'E', 'A', 'R', 'S', 'T', 'N', 'O'),
                coded = listOf(0, 1, 2, 3), // glyph kinds -> G,E,A,R
                answer = "GEAR",
            ),
            clue = "Display: GEAR"),
        RoomDef("robot", "Robot Helper", "Robot Helper", Color(0.40f, 0.34f, 0.30f, 1f), Color(1f, 0.58f, 0.20f, 1f),
            Order(listOf(
                "Show the robot lots of cat photos",
                "The robot spots the pattern",
                "The robot guesses 'cat!' on a new photo",
            )),
            clue = "Display: LEARN"),
        RoomDef("poster", "Word Display", "Word Display", Color(0.28f, 0.42f, 0.36f, 1f), cGood,
            WordSearch(
                words = listOf("ROBOT", "LEARN", "GEAR"),
                grid = arrayOf(
                    "ZXQKVWYJ".toCharArray(),
                    "GPDLHUFM".toCharArray(),
                    "CEVEKXQZ".toCharArray(),
                    "WYAAJPDH".toCharArray(),
                    "KVQROBOT".toCharArray(),
                    "XZJNCWYF".toCharArray(),
                    "MPUDKVQX".toCharArray(),
                    "HFCZJWYP".toCharArray(),
                ),
                crossRow = 4, crossCol = 3, // ROBOT->, LEARN(down), GEAR(diag) cross at the R
            ),
            clue = "Door code: 45"),
        RoomDef("keypad", "Exit Chamber", "Exit Keypad", Color(0.30f, 0.40f, 0.42f, 1f), cGood,
            NumberLock("45", "Exit Keypad", "Enter the door code", showClues = true),
            requires = "poster"),
    )
    private val totalStations = roomDefs.count { it.puzzle != null }

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

    /** Recompute the room grid, walls and HUD anchors for the current orientation. */
    private fun buildLayout() {
        val cols: Int; val rows: Int
        if (landscape) {
            cols = 3; rows = 2
            worldW = 800f; worldH = 480f
            areaX = 22f; areaY = 32f; areaW = worldW - 44f; areaH = worldH - 92f
            titlePos.set(worldW / 2f, worldH - 18f)
            solvedPos.set(worldW / 2f, worldH - 42f)
            actionCenter.set(worldW - 58f, 58f)
            closeCenter.set(48f, worldH - 40f)
            cluesAnchor.set(worldW - 88f, worldH - 24f)
        } else {
            cols = 2; rows = 3
            worldW = 480f; worldH = 800f
            areaX = 22f; areaY = 56f; areaW = worldW - 44f; areaH = 638f
            titlePos.set(worldW / 2f, 768f)
            solvedPos.set(worldW / 2f, 735f)
            actionCenter.set(410f, 90f)
            closeCenter.set(42f, 752f)
            cluesAnchor.set(worldW / 2f, 62f)
        }
        val cw = areaW / cols; val ch = areaH / rows
        cells = roomDefs.indices.map { i ->
            val c = i % cols; val r = i / cols
            RoomCell(roomDefs[i].title, areaX + c * cw, areaY + r * ch, cw, ch, roomDefs[i].floor)
        }
        stationPos = cells.map { Vector2(it.x + it.w / 2f, it.y + it.h / 2f + 8f) }
        val last = cells.last()
        exitDoor.set(last.x + last.w * 0.8f, last.y + last.h / 2f)
        walls = buildWalls(cols, rows, cw, ch)
    }

    /** Outer border + interior dividers, each with a doorway gap so rooms connect. */
    private fun buildWalls(cols: Int, rows: Int, cw: Float, ch: Float): List<FloatArray> {
        val w = ArrayList<FloatArray>()
        val t = 8f; val gap = 72f
        // Outer border.
        w.add(floatArrayOf(areaX - t, areaY - t, t, areaH + 2 * t))
        w.add(floatArrayOf(areaX + areaW, areaY - t, t, areaH + 2 * t))
        w.add(floatArrayOf(areaX - t, areaY - t, areaW + 2 * t, t))
        w.add(floatArrayOf(areaX - t, areaY + areaH, areaW + 2 * t, t))
        // Vertical dividers between columns (doorway per row).
        for (c in 1 until cols) {
            val x = areaX + c * cw
            for (r in 0 until rows) {
                val y0 = areaY + r * ch; val gc = y0 + ch / 2f
                val below = gc - gap / 2f - y0; val above = (y0 + ch) - (gc + gap / 2f)
                if (below > 0) w.add(floatArrayOf(x - t / 2f, y0, t, below))
                if (above > 0) w.add(floatArrayOf(x - t / 2f, gc + gap / 2f, t, above))
            }
        }
        // Horizontal dividers between rows (doorway per column).
        for (r in 1 until rows) {
            val y = areaY + r * ch
            for (c in 0 until cols) {
                val x0 = areaX + c * cw; val gc = x0 + cw / 2f
                val left = gc - gap / 2f - x0; val right = (x0 + cw) - (gc + gap / 2f)
                if (left > 0) w.add(floatArrayOf(x0, y - t / 2f, left, t))
                if (right > 0) w.add(floatArrayOf(gc + gap / 2f, y - t / 2f, right, t))
            }
        }
        return w
    }

    private fun currentRoomIndex(): Int {
        val i = cells.indexOfFirst { pos.x >= it.x && pos.x < it.x + it.w && pos.y >= it.y && pos.y < it.y + it.h }
        return if (i < 0) 0 else i
    }

    /** Index of an interactable (unsolved) machine in the room the player is in. */
    private fun activeMachine(): Int? {
        val i = currentRoomIndex()
        val rd = roomDefs[i]
        if (rd.puzzle == null || rd.id in solved) return null
        val p = stationPos[i]
        return if (dst(pos.x, pos.y, p.x, p.y) < 80f) i else null
    }

    private fun nearExit() =
        currentRoomIndex() == roomDefs.lastIndex && dst(pos.x, pos.y, exitDoor.x, exitDoor.y) < 64f

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
    private val layout = GlyphLayout()

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
    private val touch = Vector2()

    override fun create() {
        worldCam = OrthographicCamera()
        puzzleCam = OrthographicCamera()
        shapes = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont()
        font.setUseIntegerPositions(false)
        buildLayout()
        worldViewport = FitViewport(worldW, worldH, worldCam)
        puzzleViewport = FitViewport(W, H, puzzleCam)
        pos.set(cells[0].x + cells[0].w / 2f, cells[0].y + cells[0].h / 2f)
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
    private val backCenter = Vector2(PANEL_X + PANEL_W - 34f, PANEL_Y + PANEL_H - 30f)
    private val backR = 24f

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

    private fun isLocked(i: Int) = roomDefs[i].requires?.let { it !in solved } ?: false
    private fun labelOf(id: String) = roomDefs.firstOrNull { it.id == id }?.title ?: id
    private fun collectedClues() = roomDefs
        .filter { it.id in solved && it.clue != null }
        .map { it.clue!! }

    /** One of 8 simple shape pictograms (stand-ins for the source's emoji). */
    private fun drawGlyph(kind: Int, cx: Float, cy: Float, s: Float) {
        shapes.color = glyphColors[kind % glyphColors.size]
        when (kind % 8) {
            0 -> shapes.circle(cx, cy, s)
            1 -> shapes.rect(cx - s, cy - s, 2 * s, 2 * s)
            2 -> shapes.triangle(cx - s, cy - s, cx + s, cy - s, cx, cy + s)
            3 -> { shapes.triangle(cx - s, cy, cx + s, cy, cx, cy + s); shapes.triangle(cx - s, cy, cx + s, cy, cx, cy - s) }
            4 -> { shapes.circle(cx, cy, s); shapes.color = Color.WHITE; shapes.circle(cx, cy, s * 0.5f) }
            5 -> { shapes.rect(cx - s * 0.35f, cy - s, s * 0.7f, 2 * s); shapes.rect(cx - s, cy - s * 0.35f, 2 * s, s * 0.7f) }
            6 -> { shapes.rectLine(cx - s, cy - s, cx + s, cy + s, s * 0.5f); shapes.rectLine(cx - s, cy + s, cx + s, cy - s, s * 0.5f) }
            7 -> { shapes.circle(cx, cy, s * 0.5f); shapes.rectLine(cx, cy - s, cx, cy + s, s * 0.4f); shapes.rectLine(cx - s, cy, cx + s, cy, s * 0.4f) }
        }
    }

    /** A little robot pictogram for the counting field. */
    private fun drawRobot(cx: Float, cy: Float, s: Float) {
        shapes.color = Color(0.30f, 0.78f, 0.78f, 1f)
        shapes.rectLine(cx, cy + s, cx, cy + s * 1.5f, 3f)
        shapes.circle(cx, cy + s * 1.5f, 3f)
        shapes.rect(cx - s, cy - s, 2 * s, 2 * s)
        shapes.color = cInk
        shapes.circle(cx - s * 0.4f, cy + s * 0.1f, s * 0.22f)
        shapes.circle(cx + s * 0.4f, cy + s * 0.1f, s * 0.22f)
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
                if (justDown) {
                    val p = unprojectTouch()
                    if (within(p, backCenter, backR)) closePuzzle() else pz?.onDown(p)
                } else if (touched) {
                    pz?.onDrag(unprojectTouch())
                }
                if (justUp) pz?.onUp(unprojectTouch())
                if (pz != null && pz.complete) { activeStationId?.let { solved.add(it) }; closePuzzle() }
            }
            Phase.WON -> if (justDown) onFinish(starsForWin())
        }

        if (wrongFlash > 0f) wrongFlash -= dt
        prevTouched = touched
    }

    private fun doAction() {
        val mi = activeMachine()
        if (mi != null) {
            val rd = roomDefs[mi]
            if (isLocked(mi)) { flashMsg = "Locked — solve \"${labelOf(rd.requires!!)}\" first"; wrongFlash = 1.4f; return }
            activePuzzle = rd.puzzle; activeStationId = rd.id; rd.puzzle!!.onOpen(); wrongFlash = 0f; phase = Phase.PUZZLE; return
        }
        if (nearExit()) {
            if (solved.size >= totalStations) phase = Phase.WON
            else { flashMsg = "Open the Exit Keypad first!"; wrongFlash = 1.2f }
        }
    }

    private fun closePuzzle() {
        activePuzzle = null; activeStationId = null; phase = Phase.PLAYING; joyActive = false; wrongFlash = 0f
    }

    private fun starsForWin() = 5

    /* ----------------------------- render ----------------------------- */

    override fun render() {
        val dt = min(Gdx.graphics.deltaTime, 1f / 30f)
        handleInput(dt)

        Gdx.gl.glClearColor(0.06f, 0.06f, 0.10f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)

        when (phase) {
            Phase.PUZZLE -> { usePuzzleCam(); activePuzzle?.draw() }
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

    private fun drawScene() {
        val curIdx = currentRoomIndex()
        val mi = activeMachine()
        val nearEx = nearExit()
        val exitOpen = solved.size >= totalStations

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        // Backdrop + every room's floor + the walls.
        shapes.color = wallColor
        shapes.rect(0f, 0f, worldW, worldH)
        cells.forEach { c -> shapes.color = c.floor; shapes.rect(c.x, c.y, c.w, c.h) }
        shapes.color = wallColor
        walls.forEach { shapes.rect(it[0], it[1], it[2], it[3]) }

        // Exit door (sits in the Exit Chamber — the last room).
        if (curIdx == roomDefs.lastIndex) {
            shapes.color = if (exitOpen) Color(0.42f, 0.29f, 0.17f, 1f) else Color(0.4f, 0.2f, 0.2f, 1f)
            shapes.rect(exitDoor.x - 18f, exitDoor.y - 28f, 36f, 56f)
            if (nearEx) { shapes.color = Color.WHITE; shapes.rect(exitDoor.x - 22f, exitDoor.y + 28f, 44f, 4f) }
        }

        // Only the current room's machine is drawn (others are hidden anyway).
        roomDefs[curIdx].puzzle?.let {
            val p = stationPos[curIdx]
            val done = roomDefs[curIdx].id in solved
            val locked = isLocked(curIdx)
            shapes.color = when { done -> cGood; locked -> Color(0.5f, 0.5f, 0.56f, 1f); else -> roomDefs[curIdx].nodeColor }
            shapes.circle(p.x, p.y, 30f)
            shapes.color = Color.WHITE
            shapes.circle(p.x, p.y, 14f)
            if (locked) { shapes.color = cInk; shapes.rect(p.x - 6f, p.y - 4f, 12f, 11f) }
            if (mi == curIdx) { shapes.color = Color.WHITE; shapes.circle(p.x, p.y + 46f, 5f) }
        }

        // Fog of war — cover every room the player isn't standing in.
        shapes.color = Color(0.05f, 0.05f, 0.10f, 0.95f)
        cells.forEachIndexed { idx, c -> if (idx != curIdx) shapes.rect(c.x, c.y, c.w, c.h) }

        // Player (drawn over the fog so it's never clipped at doorways).
        shapes.color = Color(0.98f, 0.80f, 0.16f, 1f)
        shapes.circle(pos.x, pos.y, CHAR_R)
        shapes.color = cInk
        shapes.circle(pos.x - 7f, pos.y + 4f, 4f)
        shapes.circle(pos.x + 7f, pos.y + 4f, 4f)

        if (joyActive) {
            shapes.color = Color(1f, 1f, 1f, 0.25f)
            shapes.circle(joyOrigin.x, joyOrigin.y, JOY_RADIUS)
            shapes.color = cAccent
            shapes.circle(joyKnob.x, joyKnob.y, 30f)
        }

        val canAct = mi != null || nearEx
        shapes.color = if (canAct) cAccent else Color(1f, 1f, 1f, 0.25f)
        shapes.circle(actionCenter.x, actionCenter.y, actionR)

        shapes.color = Color.WHITE
        shapes.circle(closeCenter.x, closeCenter.y, closeR)
        shapes.end()

        batch.begin()
        font.color = Color.WHITE
        roomDefs[curIdx].label?.let { centerText(it, stationPos[curIdx].x, stationPos[curIdx].y - 40f, 0.9f) }
        if (curIdx == roomDefs.lastIndex) centerText(if (exitOpen) "EXIT" else "LOCKED", exitDoor.x, exitDoor.y - 40f, 0.75f)
        font.color = Color.WHITE
        val actLabel = when {
            mi != null && isLocked(mi) -> "LOCK"
            mi != null -> "USE"
            nearEx -> if (exitOpen) "EXIT" else "LOCK"
            else -> ""
        }
        centerText(actLabel, actionCenter.x, actionCenter.y + 6f, 0.85f)
        font.color = cInk
        centerText("X", closeCenter.x, closeCenter.y + 6f, 1.1f)
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
        centerText("X", backCenter.x, backCenter.y + 6f, 1f)
    }

    private fun drawWin() {
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.6f)
        shapes.rect(0f, 0f, W, H)
        shapes.color = cAccent
        shapes.rect(50f, 280f, W - 100f, 240f)
        shapes.end()
        batch.begin()
        font.color = Color.WHITE
        centerText("You escaped!", W / 2f, 460f, 1.8f)
        centerText("+5 stars", W / 2f, 410f, 1.3f)
        centerText("Tap to go back", W / 2f, 340f, 1f)
        batch.end()
    }

    private fun drawHud() {
        batch.begin()
        font.color = Color.WHITE
        centerText(cells[currentRoomIndex()].title, titlePos.x, titlePos.y, 1.2f)
        centerText("${solved.size}/$totalStations solved", solvedPos.x, solvedPos.y, 0.9f)
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

    override fun resize(width: Int, height: Int) {
        // Reflow the room grid for the new orientation, keeping the player in the
        // same room (moved to its new centre).
        val keepRoom = currentRoomIndex()
        landscape = width > height
        buildLayout()
        worldViewport.worldWidth = worldW
        worldViewport.worldHeight = worldH
        worldViewport.update(width, height, true)
        puzzleViewport.update(width, height, true)
        val c = cells[keepRoom]
        pos.set(c.x + c.w / 2f, c.y + c.h / 2f)
    }

    override fun dispose() {
        shapes.dispose(); batch.dispose(); font.dispose()
    }
}
