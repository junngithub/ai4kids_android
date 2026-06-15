# ai4kids_android

Native **Android** app for [AI4Kids](https://ai4kids.tertiarycourses.com.sg) — a
fully on-device, no-login activity app for young learners (ages 4–16). This is the
Android counterpart of the iOS/iPadOS app
([alfredang/ai4kidsapp](https://github.com/alfredang/ai4kidsapp)), rebuilt with
**Kotlin + Jetpack Compose + Material 3**.

> Plays fully offline — **no internet, no accounts, no ads, no data collection.**
> Progress (stars) is stored locally on the device only.

## Activities

| Activity | Ages | What it does |
| --- | --- | --- |
| 🔤 **Phonics Playground** | 4–6 | Match letters & sounds — *placeholder while the new design is finalized* |
| 📖 **Story Builder** | 7–9 | Pick a hero, place & magic item; the app weaves a short illustrated story |
| 🧩 **Code Puzzles** | 10–12 | Sequence arrow steps to walk a robot 🤖 to the star ⭐️ (algorithmic thinking) |
| 🧠 **Brain Games** | All | Classic emoji memory match; fewer moves earn more stars |

Kids earn ⭐️ stars for completing rounds. The home screen shows a running total,
and a **Parents' Corner** explains the privacy stance and can reset progress.

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Min SDK:** 24 (Android 7.0) · **Target/Compile SDK:** 34
- **Build:** Gradle (Kotlin DSL), Android Gradle Plugin 8.5.2, Kotlin 2.0.21
- **Persistence:** `SharedPreferences` (JSON) — no network, no third-party SDKs

## Project structure

```
app/src/main/java/sg/com/tertiarycourses/ai4kids/
├── MainActivity.kt              # App entry point; provides the shared ProgressStore
├── model/Activity.kt            # The four activities (title, color, age band, icon)
├── data/ProgressStore.kt        # Local star tally, persisted to SharedPreferences
├── ui/
│   ├── theme/Theme.kt           # Brand palette, shapes, shadows, background gradient
│   ├── RootScreen.kt            # Home grid of activity cards + header
│   ├── ParentsCornerSheet.kt    # Privacy info + reset progress
│   ├── components/SharedUI.kt   # KidButton, StarBadge, CloseButton, CelebrationView
│   └── activities/
│       ├── PhonicsScreen.kt     # Placeholder (redesign in progress)
│       ├── StoryBuilderScreen.kt
│       ├── CodePuzzlesScreen.kt
│       └── BrainGamesScreen.kt
```

## Building & running

Open the project in **Android Studio** (Koala or newer), let it sync Gradle, then
run the `app` configuration on an emulator or device. From the command line:

```bash
./gradlew assembleDebug      # build a debug APK
./gradlew installDebug       # install on a connected device/emulator
```

> If the Gradle wrapper jar is missing, run `gradle wrapper` once (or let Android
> Studio regenerate it) to produce `gradle/wrapper/gradle-wrapper.jar`.

## License

Educational sample mirroring the AI4Kids iOS app.
