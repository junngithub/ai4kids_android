<div align="center">

# 🎨 AI4Kids — Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24%20(Android%207.0)-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Build](https://img.shields.io/badge/Build-Gradle%20KTS-02303A?logo=gradle&logoColor=white)](https://gradle.org)

**Play. Learn. Create.** — a bright, friendly activity app for young learners (ages 4–16)

[Website](https://ai4kids.tertiarycourses.com.sg) · [iOS counterpart](https://github.com/alfredang/ai4kidsapp) · [Report Bug](https://github.com/alfredang/ai4kids_android/issues)

</div>

## Screenshots

<div align="center">

| Home | Story Builder | Code Puzzles | Brain Arcade |
| :---: | :---: | :---: | :---: |
| <img src="docs/screenshots/home.png" width="200" /> | <img src="docs/screenshots/story_builder.png" width="200" /> | <img src="docs/screenshots/code_puzzles.png" width="200" /> | <img src="docs/screenshots/brain_arcade.png" width="200" /> |

</div>

## About

**AI4Kids for Android** is the native Kotlin + Jetpack Compose port of the
[AI4Kids iOS/iPadOS app](https://github.com/alfredang/ai4kidsapp). It's a colourful,
kid-first learning app built around four bright activity cards on a single home screen.
Kids earn ⭐️ stars for completing rounds, the home header keeps a running total, and a
**Parents' Corner** explains the privacy stance and can reset progress.

The three core learning activities run **fully offline** — no login, no network, no data
collection; progress is stored locally on the device. The newer **Brain Arcade** adds
**online multiplayer card games**, which require a sign-in and an internet connection.

### Activities

| Activity | Ages | What it does | Connectivity |
| --- | --- | --- | --- |
| 🔤 **Phonics Playground** | 4–6 | Match letters & sounds — *"Coming soon" placeholder while the new design is finalized* | Offline |
| 📖 **Story Builder** | 7–9 | Pick a hero, a place & a magic item; the app weaves a short illustrated story | Offline |
| 🧩 **Code Puzzles** | 10–12 | Sequence arrow steps to walk a robot 🤖 to the star ⭐️ (algorithmic thinking) | Offline |
| 🧠 **Brain Arcade** | All | Six multiplayer **card games** — play solo, co-op, or versus friends | Online |

### Brain Arcade — the six card games

Each game is created/joined with a room code and validated server-side (the backend is
authoritative). Modes vary per game: **Solo**, **Co-op**, and **Versus**.

| Game | Idea | Modes |
| --- | --- | --- |
| 🧠 **Memory Match** | Flip two cards, match each word with its picture | Solo · Co-op · Versus |
| 🃏 **Tower Tumble** | Stack cards higher on four piles; play a 10 to topple a tower | Solo · Versus |
| 🔢 **Number Hunt** | Discard cards that equal — or add/subtract to — the target number | Solo · Versus |
| 🎲 **Beat the Die** | Roll the die, then play cards that add up to at least the roll | Solo · Versus |
| ⭐ **Card Showdown** | Secretly play cards, reveal at once, clash for victory stars | Versus |
| 🌈 **Matching Colours** | Memorise colour→number, then race to tap the right colour | Versus |

## Tech Stack

| Category | Technology |
| --- | --- |
| **Language** | Kotlin 2.0.21 |
| **UI** | Jetpack Compose + Material 3 (Compose BOM 2024.09.03) |
| **Architecture** | Single-Activity, 100% Compose; shared state via `CompositionLocal` |
| **Networking** | OkHttp 4.12.0 (Brain Arcade only) |
| **Auth** | NextAuth credentials flow, session cookie persisted locally |
| **Persistence** | `SharedPreferences` (stars + best times + session cookie) |
| **Build** | Gradle (Kotlin DSL), Android Gradle Plugin 8.5.2 |
| **SDK** | Min SDK 24 (Android 7.0) · Target/Compile SDK 34 · JVM 17 |

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        MainActivity                            │
│   enableEdgeToEdge · CardApi.init · provides ProgressStore     │
└───────────────────────────────┬──────────────────────────────┘
                                 │
                          ┌──────▼───────┐
                          │  RootScreen  │  home grid + star total
                          └──────┬───────┘
          ┌──────────────┬───────┴───────┬───────────────┐
          ▼              ▼               ▼                ▼
   ┌────────────┐ ┌────────────┐ ┌─────────────┐ ┌──────────────────┐
   │  Phonics   │ │   Story    │ │    Code     │ │   Brain Arcade   │
   │ (offline)  │ │ (offline)  │ │  (offline)  │ │    (online)      │
   └────────────┘ └────────────┘ └─────────────┘ └────────┬─────────┘
        offline activities award stars via ProgressStore   │
                                                            ▼
                                              ┌──────────────────────────┐
                                              │          CardApi          │
                                              │  OkHttp + cookie session   │
                                              └─────────────┬─────────────┘
                                                            │ HTTPS
                                                            ▼
                                       ai4kids Next.js backend
                                  NextAuth · /api/learn/cards/{create,
                                       join, start, move, sync}
```

## Project Structure

```
app/src/main/java/sg/com/tertiarycourses/ai4kids/
├── MainActivity.kt              # Entry point; CardApi.init + shared ProgressStore
├── model/Activity.kt            # The offline activities (title, color, age band, icon)
├── data/ProgressStore.kt        # Local star tally, persisted to SharedPreferences
├── ui/
│   ├── theme/Theme.kt           # Brand palette, shapes, shadows, background gradient
│   ├── RootScreen.kt            # Home grid of activity cards + Brain Arcade tile
│   ├── ParentsCornerSheet.kt    # Privacy info + reset progress
│   ├── components/SharedUI.kt   # KidButton, StarBadge, CloseButton, CelebrationView
│   └── activities/
│       ├── PhonicsScreen.kt     # Placeholder ("Coming soon")
│       ├── StoryBuilderScreen.kt
│       └── CodePuzzlesScreen.kt
└── cards/                       # Online "Brain Arcade" card games
    ├── CardApi.kt               # OkHttp client: NextAuth login + 5 card endpoints
    ├── CardGameMeta.kt          # Catalogue of the six games + modes
    ├── CardModels.kt            # CardState and related models
    ├── CardBoards.kt            # Per-game board rendering
    ├── BrainArcadeScreen.kt     # Hub + lobby
    ├── CardGameScreen.kt        # In-game screen
    ├── LoginScreen.kt           # Sign-in for online play
    └── LocalSolo.kt             # Offline solo play + local best times
```

## Getting Started

### Prerequisites

- **Android Studio** (Koala or newer) with the Android SDK
- **JDK 17** (bundled with recent Android Studio)
- An Android device or emulator running **Android 7.0 (API 24)** or higher

### Clone & run

```bash
git clone https://github.com/alfredang/ai4kids_android.git
cd ai4kids_android
```

Open the project in **Android Studio**, let it sync Gradle, then run the `app`
configuration on an emulator or device.

From the command line:

```bash
./gradlew assembleDebug      # build a debug APK
./gradlew installDebug       # install on a connected device/emulator
```

> If the Gradle wrapper jar is missing, run `gradle wrapper` once (or let Android
> Studio regenerate it) to produce `gradle/wrapper/gradle-wrapper.jar`.

### Brain Arcade backend (optional)

The online card games default to the production backend
(`https://ai4kids.tertiarycourses.com.sg`). For local development against a Next.js dev
server, the app permits cleartext to `10.0.2.2`, `localhost`, and `127.0.0.1` (see
`res/xml/network_security_config.xml`); the base URL is stored in `SharedPreferences`.
A valid AI4Kids account is required to sign in for online play.

## Privacy

- The three offline activities request **no network access** and collect **no data**.
- `INTERNET` / `ACCESS_NETWORK_STATE` permissions exist **only** for Brain Arcade's
  online card games.
- The only persisted data is local: star progress, solo best times, and (when signed in)
  a session cookie for Brain Arcade.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing`)
5. Open a Pull Request

Conventions: keep parity with the iOS source where practical, keep the UI 100% Compose,
and award stars through `ProgressStore.award(count, activity)` so totals persist.

## Developed By

**Tertiary Infotech Academy Pte. Ltd.** — [tertiarycourses.com.sg](https://www.tertiarycourses.com.sg)

## Acknowledgements

- Built with [Jetpack Compose](https://developer.android.com/jetpack/compose) and
  [Material 3](https://m3.material.io)
- Networking by [OkHttp](https://square.github.io/okhttp/)
- Android port of the [AI4Kids iOS app](https://github.com/alfredang/ai4kidsapp)

---

<div align="center">

⭐️ Star this repo if AI4Kids helped a young learner play, learn, and create!

</div>
