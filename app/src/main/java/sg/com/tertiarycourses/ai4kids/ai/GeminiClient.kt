package sg.com.tertiarycourses.ai4kids.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import sg.com.tertiarycourses.ai4kids.BuildConfig
import java.util.concurrent.TimeUnit

/**
 * Tiny client for Google's Gemini API, used to power the Phonics "Buddy" — short,
 * kid-friendly hints and praise. The API key comes from `BuildConfig` (set via
 * local.properties), never hard-coded. If no key is configured or the call
 * fails, callers fall back gracefully and the games still work offline.
 */
object GeminiClient {

    /** Fast, low-cost model — plenty for one-sentence kid feedback. */
    private const val MODEL = "gemini-2.5-flash"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** True when an API key is present, so the UI can show the AI affordances. */
    fun isConfigured(): Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    /**
     * Generate a short reply for [prompt]. Returns the trimmed text, or null on
     * any failure (no key, no network, bad response) so callers degrade safely.
     */
    suspend fun generate(prompt: String, maxTokens: Int = 64): String? = withContext(Dispatchers.IO) {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) return@withContext null

        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))),
                ),
            )
            .put(
                "generationConfig",
                JSONObject().put("maxOutputTokens", maxTokens).put("temperature", 0.9),
            )

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody(JSON))
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@use null
                JSONObject(text)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }
}
