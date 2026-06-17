package sg.com.tertiarycourses.ai4kids.ui.activities

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.model.Activity
import sg.com.tertiarycourses.ai4kids.ui.components.CelebrationView
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.StarBadge
import sg.com.tertiarycourses.ai4kids.ui.components.softShadow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

private enum class Step(val glyph: String) { UP("↑"), DOWN("↓"), LEFT("←"), RIGHT("→") }

private data class Level(
    val size: Int,
    val start: Pair<Int, Int>,
    val goal: Pair<Int, Int>,
    val walls: Set<Pair<Int, Int>>,
)

private val LEVELS = listOf(
    Level(4, 0 to 0, 3 to 0, emptySet()),
    Level(4, 0 to 3, 3 to 0, setOf(2 to 2, 2 to 1)),
    Level(5, 0 to 0, 4 to 4, setOf(2 to 2, 3 to 2, 1 to 3)),
)

/**
 * Code Puzzles — a tiny "sequence the steps" game that teaches algorithmic
 * thinking without real code execution. The child taps direction arrows to plan
 * a path that walks the robot 🤖 to the goal ⭐️ on a small grid, then runs it.
 * Android port of the iOS `CodePuzzlesView`.
 */
@Composable
fun CodePuzzlesScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val progress = LocalProgressStore.current

    var levelIndex by remember { mutableStateOf(0) }
    val level = LEVELS[levelIndex]

    var program by remember { mutableStateOf<List<Step>>(emptyList()) }
    var robot by remember { mutableStateOf(level.start) }
    var running by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Plan the robot's path to the star!") }
    var showCelebration by remember { mutableStateOf(false) }

    fun resetLevel() {
        program = emptyList()
        robot = level.start
        running = false
        message = "Plan the robot's path to the star!"
    }

    // Re-seed the robot whenever the level changes.
    LaunchedEffect(levelIndex) { resetLevel() }

    // Drive the robot along the planned program, one step every 0.4s.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        robot = level.start
        for (step in program) {
            delay(400)
            var (x, y) = robot
            when (step) {
                Step.UP -> y += 1
                Step.DOWN -> y -= 1
                Step.LEFT -> x -= 1
                Step.RIGHT -> x += 1
            }
            // Clamp to grid and respect walls (illegal move = stay put).
            if (x in 0 until level.size && y in 0 until level.size && (x to y) !in level.walls) {
                robot = x to y
            }
        }
        delay(400)
        running = false
        if (robot == level.goal) {
            progress.award(2, Activity.CODE)
            showCelebration = true
        } else {
            message = "Almost! Try a new plan. 🔁"
            delay(400)
            resetLevel()
        }
    }

    val onStep: (Step) -> Unit = { if (!running) program = program + it }
    val onUndo = { if (!running && program.isNotEmpty()) program = program.dropLast(1) }
    val onRun = { if (!running && program.isNotEmpty()) running = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.Background),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // In landscape the screen is short, so put the grid beside the
            // controls (instead of stacked) and shrink the cells a touch. Both
            // layouts scroll as a safety net on very small screens.
            val landscape = maxWidth > maxHeight
            val cell = if (landscape) 46.dp else 60.dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = if (landscape) 1000.dp else 720.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                // Top bar.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    CloseButton(onClick = onClose)
                    Spacer(Modifier.weight(1f))
                    Text("Code Puzzles  •  Level ${levelIndex + 1}", color = Theme.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    StarBadge(count = progress.stars(Activity.CODE))
                }

                if (landscape) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Grid(level = level, robot = robot, side = cell)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            MessageText(message)
                            ProgramBar(program = program)
                            Controls(running = running, onStep = onStep, onUndo = onUndo, onRun = onRun)
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        MessageText(message)
                        Grid(level = level, robot = robot, side = cell)
                        ProgramBar(program = program)
                        Controls(running = running, onStep = onStep, onUndo = onUndo, onRun = onRun)
                    }
                }
            }
        }

        if (showCelebration) {
            CelebrationView(message = "Solved it! 🤖⭐️", onDismiss = {
                showCelebration = false
                levelIndex = (levelIndex + 1) % LEVELS.size
            })
        }
    }
}

@Composable
private fun MessageText(message: String) {
    Text(
        message,
        color = Theme.Ink.copy(alpha = 0.75f),
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Grid(level: Level, robot: Pair<Int, Int>, side: Dp = 60.dp) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .softShadow(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(10.dp),
    ) {
        for (y in (level.size - 1) downTo 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (x in 0 until level.size) {
                    val isRobot = robot == (x to y)
                    val isGoal = level.goal == (x to y)
                    val isWall = (x to y) in level.walls
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(side)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isWall) Theme.Ink.copy(alpha = 0.8f) else Theme.Blue.copy(alpha = 0.12f)),
                    ) {
                        val glyph = (side.value * 0.56f).sp
                        if (isGoal) Text("⭐️", fontSize = glyph)
                        if (isRobot) Text("🤖", fontSize = glyph)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramBar(program: List<Step>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .softShadow(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (program.isEmpty()) {
            Text("Your steps appear here →", color = Theme.Ink.copy(alpha = 0.4f), fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        program.forEach { step ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Theme.Blue),
            ) {
                Text(step.glyph, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun Controls(
    running: Boolean,
    onStep: (Step) -> Unit,
    onUndo: () -> Unit,
    onRun: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Step.entries.forEach { step ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(66.dp)
                        .softShadow(CircleShape)
                        .clip(CircleShape)
                        .background(Theme.Blue)
                        .clickable(enabled = !running) { onStep(step) },
                ) {
                    Text(step.glyph, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            KidButton(title = "Undo", icon = Icons.AutoMirrored.Filled.Undo, color = Theme.Ink.copy(alpha = 0.5f), onClick = onUndo)
            KidButton(title = "Run", icon = Icons.Filled.PlayArrow, color = Theme.Green, onClick = onRun)
        }
    }
}
