package sg.com.tertiarycourses.ai4kids.gdx

import android.content.Intent
import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration

/**
 * Hosts the LibGDX [EscapeGdxGame] full-screen. Launched from the Compose home
 * grid; returns the stars earned (via the result intent) so the app can credit
 * them to the shared ProgressStore.
 */
class EscapeActivity : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            useAccelerometer = false
            useCompass = false
        }
        initialize(
            EscapeGdxGame { stars ->
                runOnUiThread {
                    setResult(RESULT_OK, Intent().putExtra(EXTRA_STARS, stars))
                    finish()
                }
            },
            config,
        )
    }

    companion object {
        const val EXTRA_STARS = "stars"
    }
}
