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
import kotlin.math.min

/**
 * Robot Lab Escape — a top-down LibGDX mini-game. Drive the explorer with a
 * floating joystick around two connected rooms, walk up to each machine and
 * answer its puzzle, then leave through the unlocked exit. Rendered with simple
 * shapes + text for now (LibGDX makes it easy to swap in sprite/atlas art).
 *
 * [onFinish] is called with the stars earned (0 if the player just backs out),
 * which the hosting Activity returns to the Compose app.
 */
class EscapeGdxGame(private val onFinish: (Int) -> Unit) : ApplicationAdapter() {

    private companion object {
        const val W = 480f
        const val H = 800f
        const val SPEED = 230f          // world units / second
        const val JOY_RADIUS = 72f
        const val CHAR_R = 22f
        val MIN = Vector2(50f, 50f)
        val MAX = Vector2(430f, 650f)
    }

    private data class Puzzle(val prompt: String, val options: List<String>, val answer: Int)
    private data class Station(val id: String, val label: String, val x: Float, val y: Float, val color: Color, val puzzle: Puzzle)
    private data class Door(val x: Float, val y: Float, val label: String, val target: Int?, val isExit: Boolean = false)
    private data class Room(val title: String, val floor: Color, val wall: Color, val stations: List<Station>, val doors: List<Door>, val spawn: Vector2)

    private val rooms = listOf(
        Room(
            title = "Robot Lab",
            floor = Color(0.30f, 0.34f, 0.46f, 1f),
            wall = Color(0.16f, 0.18f, 0.32f, 1f),
            spawn = Vector2(170f, 150f),
            stations = listOf(
                Station("panel", "Control Panel", 130f, 520f, Color(0.20f, 0.62f, 0.98f, 1f),
                    Puzzle("Robots learn best from lots of...?", listOf("Data", "Sweets", "Sleep"), 0)),
                Station("shelf", "Tool Shelf", 350f, 520f, Color(1f, 0.58f, 0.20f, 1f),
                    Puzzle("What comes FIRST when learning?", listOf("Collect examples", "Take a guess", "Win a prize"), 0)),
            ),
            doors = listOf(Door(440f, 360f, "Control Room", target = 1)),
        ),
        Room(
            title = "Control Room",
            floor = Color(0.30f, 0.42f, 0.36f, 1f),
            wall = Color(0.08f, 0.29f, 0.23f, 1f),
            spawn = Vector2(120f, 150f),
            stations = listOf(
                Station("screen", "Brain Screen", 240f, 520f, Color(0.20f, 0.78f, 0.45f, 1f),
                    Puzzle("A computer that learns is called...?", listOf("AI", "TV", "Toy"), 0)),
            ),
            doors = listOf(
                Door(40f, 360f, "Robot Lab", target = 0),
                Door(440f, 200f, "EXIT", target = null, isExit = true),
            ),
        ),
    )
    private val totalStations = rooms.sumOf { it.stations.size }

    private enum class Phase { PLAYING, PUZZLE, WON }

    private lateinit var cam: OrthographicCamera
    private lateinit var viewport: FitViewport
    private lateinit var shapes: ShapeRenderer
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private val layout = GlyphLayout()

    private var roomIndex = 0
    private val solved = HashSet<String>()
    private val pos = Vector2()
    private var phase = Phase.PLAYING

    // Floating joystick.
    private var joyActive = false
    private val joyOrigin = Vector2()
    private val joyKnob = Vector2()

    private var activePuzzle: Puzzle? = null
    private var wrongFlash = 0f
    private val touch = Vector2()

    private val room get() = rooms[roomIndex]

    override fun create() {
        cam = OrthographicCamera()
        viewport = FitViewport(W, H, cam)
        shapes = ShapeRenderer()
        batch = SpriteBatch()
        font = BitmapFont()
        font.setUseIntegerPositions(false)
        pos.set(room.spawn)
        Gdx.input.setCatchKey(com.badlogic.gdx.Input.Keys.BACK, false)
    }

    /* ----------------------------- helpers ----------------------------- */

    private fun unprojectTouch(): Vector2 {
        touch.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
        return viewport.unproject(touch)
    }

    private val actionCenter = Vector2(410f, 90f)
    private val actionR = 46f
    private val closeCenter = Vector2(42f, 752f)
    private val closeR = 26f

    private fun nearestStation(): Station? =
        room.stations.filter { it.id !in solved }.minByOrNull { dst(pos.x, pos.y, it.x, it.y) }
            ?.takeIf { dst(pos.x, pos.y, it.x, it.y) < 70f }

    private fun nearestDoor(): Door? =
        room.doors.minByOrNull { dst(pos.x, pos.y, it.x, it.y) }
            ?.takeIf { dst(pos.x, pos.y, it.x, it.y) < 62f }

    private fun dst(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by; return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    /* ----------------------------- input ----------------------------- */

    private fun handleInput(dt: Float) {
        if (Gdx.input.justTouched()) {
            val p = unprojectTouch()
            when (phase) {
                Phase.PLAYING -> {
                    when {
                        within(p, closeCenter, closeR) -> onFinish(0)
                        within(p, actionCenter, actionR) -> doAction()
                        else -> { joyActive = true; joyOrigin.set(p); joyKnob.set(p) }
                    }
                }
                Phase.PUZZLE -> handlePuzzleTap(p)
                Phase.WON -> onFinish(starsForWin())
            }
        }

        if (phase == Phase.PLAYING && joyActive && Gdx.input.isTouched()) {
            val p = unprojectTouch()
            val dir = Vector2(p).sub(joyOrigin)
            if (dir.len() > JOY_RADIUS) dir.setLength(JOY_RADIUS)
            joyKnob.set(joyOrigin).add(dir)
            val v = Vector2(dir).scl(1f / JOY_RADIUS) // -1..1
            pos.x = (pos.x + v.x * SPEED * dt).coerceIn(MIN.x, MAX.x)
            pos.y = (pos.y + v.y * SPEED * dt).coerceIn(MIN.y, MAX.y)
        }
        if (!Gdx.input.isTouched()) joyActive = false
        if (wrongFlash > 0f) wrongFlash -= dt
    }

    private fun within(p: Vector2, c: Vector2, r: Float) = dst(p.x, p.y, c.x, c.y) < r

    private fun doAction() {
        val s = nearestStation()
        if (s != null) { activePuzzle = s.puzzle; activeStationId = s.id; phase = Phase.PUZZLE; return }
        val d = nearestDoor() ?: return
        when {
            d.isExit && solved.size >= totalStations -> phase = Phase.WON
            d.isExit -> wrongFlash = 1.2f // locked
            else -> { roomIndex = d.target!!; pos.set(room.spawn); joyActive = false }
        }
    }

    private var activeStationId: String? = null

    private fun handlePuzzleTap(p: Vector2) {
        val puzzle = activePuzzle ?: return
        puzzle.options.indices.forEach { i ->
            val r = optionRect(i)
            if (p.x >= r[0] && p.x <= r[0] + r[2] && p.y >= r[1] && p.y <= r[1] + r[3]) {
                if (i == puzzle.answer) {
                    activeStationId?.let { solved.add(it) }
                    activePuzzle = null; activeStationId = null; phase = Phase.PLAYING
                } else {
                    wrongFlash = 1f
                }
            }
        }
        // Tap outside the panel closes it.
        if (p.y < 150f || p.y > 650f) { activePuzzle = null; activeStationId = null; phase = Phase.PLAYING }
    }

    private fun starsForWin() = 5

    // Panel option button rects: [x, y, w, h].
    private fun optionRect(i: Int): FloatArray {
        val w = 360f; val x = (W - w) / 2f; val h = 56f
        val y = 420f - i * (h + 16f)
        return floatArrayOf(x, y, w, h)
    }

    /* ----------------------------- render ----------------------------- */

    override fun render() {
        val dt = min(Gdx.graphics.deltaTime, 1f / 30f)
        handleInput(dt)

        Gdx.gl.glClearColor(0.07f, 0.07f, 0.12f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        cam.update()
        shapes.projectionMatrix = cam.combined
        batch.projectionMatrix = cam.combined

        drawScene()
        if (phase == Phase.PUZZLE) drawPuzzle()
        if (phase == Phase.WON) drawWin()
        drawHud()
    }

    private fun drawScene() {
        val near = nearestStation()
        val nearDoor = if (near == null) nearestDoor() else null

        shapes.begin(ShapeRenderer.ShapeType.Filled)
        // Walls + floor.
        shapes.color = room.wall
        shapes.rect(0f, 0f, W, 700f)
        shapes.color = room.floor
        shapes.rect(24f, 24f, W - 48f, 700f - 48f)

        // Doors.
        room.doors.forEach { d ->
            val unlocked = !d.isExit || solved.size >= totalStations
            shapes.color = if (d.isExit && !unlocked) Color(0.4f, 0.2f, 0.2f, 1f) else Color(0.42f, 0.29f, 0.17f, 1f)
            shapes.rect(d.x - 26f, d.y - 34f, 52f, 68f)
            if (nearDoor === d) { shapes.color = Color.WHITE; shapes.rect(d.x - 30f, d.y - 38f, 60f, 4f) }
        }

        // Stations.
        room.stations.forEach { s ->
            val done = s.id in solved
            shapes.color = if (done) Color(0.20f, 0.78f, 0.45f, 1f) else s.color
            shapes.circle(s.x, s.y, 30f)
            shapes.color = Color.WHITE
            shapes.circle(s.x, s.y, 14f)
            if (near === s) { shapes.color = Color.WHITE; shapes.circle(s.x, s.y + 46f, 5f) }
        }

        // Explorer.
        shapes.color = Color(0.98f, 0.80f, 0.16f, 1f)
        shapes.circle(pos.x, pos.y, CHAR_R)
        shapes.color = Color(0.16f, 0.14f, 0.30f, 1f)
        shapes.circle(pos.x - 7f, pos.y + 4f, 4f)
        shapes.circle(pos.x + 7f, pos.y + 4f, 4f)

        // Floating joystick.
        if (joyActive) {
            shapes.color = Color(1f, 1f, 1f, 0.25f)
            shapes.circle(joyOrigin.x, joyOrigin.y, JOY_RADIUS)
            shapes.color = room.stations.firstOrNull()?.color ?: Color.SKY
            shapes.circle(joyKnob.x, joyKnob.y, 30f)
        }

        // Action button.
        val canAct = near != null || nearDoor != null
        shapes.color = if (canAct) Color(0.45f, 0.30f, 0.92f, 1f) else Color(1f, 1f, 1f, 0.25f)
        shapes.circle(actionCenter.x, actionCenter.y, actionR)

        // Close (X) button bg.
        shapes.color = Color.WHITE
        shapes.circle(closeCenter.x, closeCenter.y, closeR)
        shapes.end()

        // Text labels.
        batch.begin()
        font.color = Color.WHITE
        room.stations.forEach { s -> centerText(s.label, s.x, s.y - 40f, 1f) }
        room.doors.forEach { d -> centerText(d.label, d.x, d.y - 44f, 0.9f) }
        // Action label.
        font.color = Color.WHITE
        val actLabel = when {
            near != null -> "FIX"
            nearDoor != null && nearDoor.isExit -> if (solved.size >= totalStations) "EXIT" else "LOCK"
            nearDoor != null -> "GO"
            else -> ""
        }
        centerText(actLabel, actionCenter.x, actionCenter.y + 6f, 1.1f)
        // Close X glyph.
        font.color = Color(0.16f, 0.14f, 0.30f, 1f)
        centerText("X", closeCenter.x, closeCenter.y + 6f, 1.2f)
        batch.end()

        if (wrongFlash > 0f && phase == Phase.PLAYING) {
            batch.begin(); font.color = Color(0.96f, 0.34f, 0.34f, 1f)
            centerText("Fix all the machines first!", W / 2f, 120f, 1.1f)
            batch.end()
        }
    }

    private fun drawPuzzle() {
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.55f)
        shapes.rect(0f, 0f, W, H)
        shapes.color = Color.WHITE
        shapes.rect(40f, 150f, W - 80f, 500f)
        val puzzle = activePuzzle
        puzzle?.options?.indices?.forEach { i ->
            val r = optionRect(i)
            shapes.color = Color(0.45f, 0.30f, 0.92f, 1f)
            shapes.rect(r[0], r[1], r[2], r[3])
        }
        shapes.end()

        batch.begin()
        font.color = Color(0.16f, 0.14f, 0.30f, 1f)
        puzzle?.let { wrapText(it.prompt, W / 2f, 590f, W - 140f, 1.2f) }
        font.color = Color.WHITE
        puzzle?.options?.forEachIndexed { i, opt ->
            val r = optionRect(i)
            centerText(opt, r[0] + r[2] / 2f, r[1] + r[3] / 2f + 6f, 1.1f)
        }
        if (wrongFlash > 0f) { font.color = Color(0.96f, 0.34f, 0.34f, 1f); centerText("Try again!", W / 2f, 190f, 1.1f) }
        batch.end()
    }

    private fun drawWin() {
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        shapes.color = Color(0f, 0f, 0f, 0.6f)
        shapes.rect(0f, 0f, W, H)
        shapes.color = Color(0.45f, 0.30f, 0.92f, 1f)
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
        centerText(room.title, W / 2f, 768f, 1.3f)
        centerText("${solved.size}/$totalStations fixed", W / 2f, 735f, 1f)
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
        viewport.update(width, height, true)
    }

    override fun dispose() {
        shapes.dispose(); batch.dispose(); font.dispose()
    }
}
