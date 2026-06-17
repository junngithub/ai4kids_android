package sg.com.tertiarycourses.ai4kids.escape

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sg.com.tertiarycourses.ai4kids.cards.LoginScreen
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

private const val ROOM_SLUG = "robot-lab"

// Must match the order of `levels` in EscapeGdxGame. Only level 0 (Robot Lab) is
// built out and supports co-op; the rest are empty layouts (solo walk-throughs).
val ESCAPE_LEVELS = listOf("Robot Lab", "The Vault", "The Tower", "The Annex", "The Big Hall")

private enum class Step { CHOOSE, SOLO, LOGIN, COOP, JOIN }

/**
 * Pre-game lobby for the Escape Room. Lets a learner play solo (offline, no
 * login — and pick which room) or team up: host a room (get a share code) or
 * join one by code. Once the host starts — or a joined player taps Enter —
 * [onPlay] launches the game with the session code (null = solo) and level index.
 */
@Composable
fun EscapeLobbyScreen(onClose: () -> Unit, onPlay: (code: String?, host: Boolean, level: Int) -> Unit) {
    var step by remember { mutableStateOf(Step.CHOOSE) }
    var lobby by remember { mutableStateOf<EscapeState?>(null) }
    var isHost by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler { if (lobby != null) { lobby = null } else if (step != Step.CHOOSE) { step = Step.CHOOSE } else onClose() }

    fun host() {
        busy = true; error = null
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { EscapeApi.create(ROOM_SLUG) } }
            busy = false
            r.onSuccess { lobby = it; isHost = true }.onFailure { error = it.message }
        }
    }

    fun join() {
        if (joinCode.isBlank()) return
        busy = true; error = null
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { EscapeApi.join(joinCode, ROOM_SLUG) } }
            busy = false
            r.onSuccess { lobby = it; isHost = false }.onFailure { error = it.message }
        }
    }

    // In a lobby room (created or joined) — wait for the team, then enter.
    lobby?.let { st ->
        LobbyRoom(
            code = st.code, host = isHost, initial = st,
            onLeave = { lobby = null; step = Step.CHOOSE },
            onEnter = { onPlay(st.code, isHost, 0) }, // co-op is always the Robot Lab (level 0)
        )
        return
    }

    if (step == Step.LOGIN) {
        LoginScreen(onClose = { step = Step.CHOOSE }, onLoggedIn = { step = Step.COOP })
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Theme.Background)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CloseButton(onClick = onClose)
                Spacer(Modifier.weight(1f))
                Text("🚪 Escape Room", color = Theme.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.widthIn(min = 48.dp))
            }

            when (step) {
                Step.CHOOSE -> {
                    Hero("Escape Room", "Explore the rooms, solve the puzzles, escape!")
                    KidButton("Play solo", color = Theme.Teal, onClick = { step = Step.SOLO }, modifier = Modifier.fillMaxWidth())
                    KidButton(
                        title = "Play with friends",
                        color = Theme.Purple,
                        onClick = { step = if (EscapeApi.isLoggedIn()) Step.COOP else Step.LOGIN },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Solo plays offline. Playing with friends needs a kid sign-in.",
                        color = Theme.Ink.copy(alpha = 0.55f), fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Step.SOLO -> {
                    Hero("Pick a room", "Robot Lab is the full game — the rest are new layouts.")
                    ESCAPE_LEVELS.forEachIndexed { i, name ->
                        KidButton(
                            title = name,
                            color = if (i == 0) Theme.Teal else Theme.Purple,
                            onClick = { onPlay(null, false, i) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Step.COOP -> {
                    Hero("Team up", "Solve the lab together — progress is shared.")
                    KidButton(
                        title = if (busy) "Starting…" else "Host a room",
                        color = if (busy) Theme.Ink.copy(alpha = 0.3f) else Theme.Green,
                        enabled = !busy, onClick = { host() }, modifier = Modifier.fillMaxWidth(),
                    )
                    KidButton("Join with a code", color = Theme.Orange, onClick = { step = Step.JOIN }, modifier = Modifier.fillMaxWidth())
                }
                Step.JOIN -> {
                    Hero("Join a room", "Enter the code your friend shared.")
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = { joinCode = it.uppercase().take(12) },
                        singleLine = true,
                        label = { Text("Room code") },
                        placeholder = { Text("e.g. ROBOT42") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KidButton(
                        title = if (busy) "Joining…" else "Join",
                        color = if (busy || joinCode.isBlank()) Theme.Ink.copy(alpha = 0.3f) else Theme.Green,
                        enabled = !busy && joinCode.isNotBlank(), onClick = { join() }, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Step.LOGIN -> Unit
            }

            error?.let { Text(it, color = Theme.Red, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun Hero(title: String, subtitle: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().kidCard().padding(24.dp),
    ) {
        Text("🤖", fontSize = 52.sp)
        Text(title, color = Theme.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = Theme.Ink.copy(alpha = 0.6f), fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

/** The shared waiting room: shows the code + roster, polls for teammates. */
@Composable
private fun LobbyRoom(code: String, host: Boolean, initial: EscapeState, onLeave: () -> Unit, onEnter: () -> Unit) {
    var state by remember(code) { mutableStateOf(initial) }
    var starting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Poll the roster; a joined player auto-enters once the host starts.
    LaunchedEffectPoll(code) { s ->
        state = s
        if (!host && s.playing && !starting) { starting = true; onEnter() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Theme.Background)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CloseButton(onClick = onLeave)
                Spacer(Modifier.weight(1f))
                Text("Robot Lab", color = Theme.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.widthIn(min = 48.dp))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().kidCard().padding(24.dp),
            ) {
                Text("Room code", color = Theme.Ink.copy(alpha = 0.5f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(code, color = Theme.Purple, fontSize = 40.sp, fontWeight = FontWeight.Black)
                Text("Share this with your friends to let them join.", color = Theme.Ink.copy(alpha = 0.55f), fontSize = 14.sp, textAlign = TextAlign.Center)
            }

            Text("Players (${state.players.size})", color = Theme.Ink, fontSize = 18.sp, fontWeight = FontWeight.Black)
            state.players.forEach { p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().kidCard().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(p.avatar ?: "🧒", fontSize = 28.sp)
                    Text(p.name, color = Theme.Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (p.isHost) Text("Host", color = Theme.Purple, fontSize = 13.sp, fontWeight = FontWeight.Black)
                }
            }

            if (host) {
                KidButton(
                    title = if (starting) "Starting…" else "Start adventure!",
                    color = if (starting) Theme.Ink.copy(alpha = 0.3f) else Theme.Green,
                    enabled = !starting,
                    onClick = {
                        if (!starting) {
                            starting = true
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { EscapeApi.start(code) } }
                                onEnter()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    "Waiting for the host to start…",
                    color = Theme.Ink.copy(alpha = 0.6f), fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                KidButton("Enter now", color = Theme.Teal, onClick = onEnter, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Poll [EscapeApi.sync] every ~1s while composed, delivering each state. */
@Composable
private fun LaunchedEffectPoll(code: String, onState: (EscapeState) -> Unit) {
    androidx.compose.runtime.LaunchedEffect(code) {
        while (isActive) {
            val s = runCatching { withContext(Dispatchers.IO) { EscapeApi.sync(code) } }.getOrNull()
            if (s != null) onState(s)
            delay(1000)
        }
    }
}
