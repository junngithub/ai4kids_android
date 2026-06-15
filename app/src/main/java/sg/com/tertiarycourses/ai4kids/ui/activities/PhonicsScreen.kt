package sg.com.tertiarycourses.ai4kids.ui.activities

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.model.Activity
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.StarBadge
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * Phonics Playground — placeholder.
 *
 * The phonics experience is being redesigned, so this is a temporary "coming
 * soon" page. The full letter-and-sound matching game will be reinstated once
 * the new design is finalized.
 */
@Composable
fun PhonicsScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val progress = LocalProgressStore.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.Background),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CloseButton(onClick = onClose)
                Spacer(Modifier.weight(1f))
                Text("Phonics Playground", color = Theme.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                StarBadge(count = progress.stars(Activity.PHONICS))
            }

            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .kidCard()
                    .padding(36.dp),
            ) {
                Text("🔤", fontSize = 80.sp)
                Text(
                    "Coming soon!",
                    color = Theme.Pink,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "We're building a brand-new Phonics Playground. Check back soon to match letters and sounds!",
                    color = Theme.Ink.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}
