package sg.com.tertiarycourses.ai4kids.ui.activities

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.model.Activity
import sg.com.tertiarycourses.ai4kids.ui.components.CelebrationView
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.StarBadge
import sg.com.tertiarycourses.ai4kids.ui.components.softShadow
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

private class Card(val face: String) {
    var matched by mutableStateOf(false)
}

private val FACES = listOf("🐶", "🐱", "🦊", "🐼", "🦁", "🐸", "🐵", "🐯")

/**
 * Brain Games — a classic memory match. Cards are dealt face-down; the child
 * flips two at a time to find matching emoji pairs. Matching all pairs earns
 * stars scaled to how few moves it took. Android port of the iOS `BrainGamesView`.
 */
@Composable
fun BrainGamesScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val progress = LocalProgressStore.current
    val scope = rememberCoroutineScope()

    val cards = remember { mutableStateListOf<Card>() }
    val flipped = remember { mutableStateListOf<Int>() }
    var moves by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }

    fun deal() {
        showCelebration = false
        flipped.clear()
        moves = 0
        busy = false
        val dealt = (FACES + FACES).map { Card(it) }.shuffled()
        cards.clear()
        cards.addAll(dealt)
    }

    LaunchedEffect(Unit) { deal() }

    fun finish() {
        // Fewer moves → more stars (3 for a great game, down to 1).
        val stars = if (moves <= 12) 3 else if (moves <= 18) 2 else 1
        progress.award(stars, Activity.BRAIN)
        showCelebration = true
    }

    fun flip(index: Int) {
        if (busy || flipped.contains(index) || cards[index].matched) return
        flipped.add(index)
        if (flipped.size == 2) {
            moves += 1
            val a = flipped[0]; val b = flipped[1]
            if (cards[a].face == cards[b].face) {
                cards[a].matched = true
                cards[b].matched = true
                flipped.clear()
                if (cards.all { it.matched }) finish()
            } else {
                busy = true
                scope.launch {
                    delay(800)
                    flipped.clear()
                    busy = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.Background),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter)
                .padding(28.dp),
        ) {
            // Top bar.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CloseButton(onClick = onClose)
                Spacer(Modifier.weight(1f))
                Text("Brain Games", color = Theme.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                StarBadge(count = progress.stars(Activity.BRAIN))
            }

            Text(
                "Find all the matching pairs!",
                color = Theme.Ink.copy(alpha = 0.75f),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                itemsIndexed(cards) { index, card ->
                    CardTile(
                        card = card,
                        faceUp = card.matched || flipped.contains(index),
                        enabled = !card.matched && !busy,
                        onClick = { flip(index) },
                    )
                }
            }

            Text("Moves: $moves", color = Theme.Ink.copy(alpha = 0.7f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            KidButton(title = "New Game", icon = Icons.Filled.Shuffle, color = Theme.Green, onClick = { deal() })
        }

        if (showCelebration) {
            CelebrationView(message = "All matched in $moves moves!", onDismiss = { deal() })
        }
    }
}

@Composable
private fun CardTile(card: Card, faceUp: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (faceUp) 0f else 180f,
        animationSpec = tween(durationMillis = 250),
        label = "cardFlip",
    )
    val shape = RoundedCornerShape(18.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                rotationY = rotation
                alpha = if (card.matched) 0.45f else 1f
            }
            .softShadow(shape)
            .clip(shape)
            .background(if (faceUp) Color.White else Theme.Purple)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        if (faceUp) {
            Text(card.face, fontSize = 40.sp)
        } else {
            Icon(Icons.Filled.QuestionMark, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}
