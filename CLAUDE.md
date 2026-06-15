# CLAUDE.md

Guidance for working in this repository.

## What this is

Native **Android** port of the AI4Kids iOS app
([alfredang/ai4kidsapp](https://github.com/alfredang/ai4kidsapp)). A fully
offline, no-login educational activity app for ages 4–16, built with **Kotlin +
Jetpack Compose + Material 3**.

Core principles (carried over from iOS): **no internet, no accounts, no ads, no
data collection.** Do not add networking, analytics, or third-party SDKs. The
`AndroidManifest.xml` intentionally requests **no** internet permission.

## Layout

- `app/src/main/java/sg/com/tertiarycourses/ai4kids/` — all Kotlin source
  - `model/Activity.kt` — the four activities (single source of truth for title,
    color, age band, icon)
  - `data/ProgressStore.kt` — local star tally in `SharedPreferences`, exposed as
    Compose state via `LocalProgressStore`
  - `ui/theme/Theme.kt` — brand palette/shapes/shadows (mirrors iOS `Theme.swift`)
  - `ui/RootScreen.kt`, `ui/ParentsCornerSheet.kt`
  - `ui/components/SharedUI.kt` — `KidButton`, `StarBadge`, `CloseButton`,
    `CelebrationView`, `kidCard`/`softShadow` modifiers
  - `ui/activities/` — one screen per activity

## Conventions

- Keep parity with the iOS source where practical; each file notes its iOS
  counterpart.
- UI is 100% Compose; the XML theme only styles the window/status bar pre-Compose.
- Award stars through `ProgressStore.award(count, activity)` so totals persist.

## Status

- **Phonics** is currently a placeholder ("Coming soon") page — the activity is
  being redesigned. The other three activities are fully implemented.
