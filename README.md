# 5IN1 — Android

Five classic games in one native Android app. **Basic edition**: each game is
genuinely playable but deliberately simple — clean, readable Kotlin +
Jetpack Compose (Material3) code, no exotic dependencies.

Package: `com.m4ster.fiveinone` · portrait only · minSdk 26 · targetSdk 34

## The five games

| Game | How it plays |
|---|---|
| Number Guessing | Guess 1–100. Attempt counter, higher/lower + hot/cold distance hints. |
| Snake | 20×20 canvas snake. Swipe or D-pad to steer, solid green walls, red head, speed rises every 5 dots. |
| Snakes & Ladders | You vs CPU. Tap Roll, race to 100 (exact roll to finish), ladders up / snakes down. |
| Ludo | Simplified you vs CPU. Two tokens each, roll a 6 to leave base, green squares are safe, captures send tokens home, extra turn on a 6, exact roll to finish. Tap a glowing token to move it. |
| Tic-Tac-Toe | You (X) vs CPU (O) with full minimax + depth tie-breaking — the CPU never loses. |

## Build locally

You need Android Studio (Hedgehog or newer) **or** a JDK 17 + the Android SDK.

```bash
# Android Studio: File → Open → this folder, then Run ▶

# Command line (SDK must be installed; point local.properties at it):
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleDebug
# APK lands at: app/build/outputs/apk/debug/app-debug.apk
```

Install the debug APK straight onto a Pixel — no signing setup needed:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## CI

`.github/workflows/build-apk.yml` builds the debug APK on every push to
`main` (plus manual dispatch) with `ubuntu-latest`, Temurin JDK 17, and
`./gradlew assembleDebug`, then uploads `app-debug.apk` as the
`5in1-debug-apk` artifact. It assumes this project sits at the repo root.

## Project structure

```
app/src/main/
├── AndroidManifest.xml            # portrait-only launcher activity
├── java/com/m4ster/fiveinone/
│   ├── MainActivity.kt            # screen switcher (no nav library)
│   ├── ui/Screen.kt               # the six screens
│   ├── ui/theme/Theme.kt          # dark arcade Material3 palette
│   ├── ui/components/GameScaffold.kt  # title bar + back button shell
│   ├── ui/home/HomeScreen.kt      # the 5 game cards
│   ├── ui/guessing/GuessingScreen.kt
│   ├── ui/snake/SnakeScreen.kt    # canvas + coroutine game loop
│   ├── ui/ladders/LaddersScreen.kt    # 10×10 boustrophedon canvas board
│   ├── ui/ludo/LudoScreen.kt      # 24-cell track, tap-to-move tokens
│   └── ui/tictactoe/TicTacToeScreen.kt  # minimax CPU
└── res/                           # strings, theme, vector launcher icon
```

## Controls

- **Guessing**: type a number, tap Guess (or Done on the keyboard).
- **Snake**: swipe anywhere on the board to steer.
- **Snakes & Ladders / Ludo**: tap Roll; in Ludo tap a glowing token to move it.
- **Tic-Tac-Toe**: tap a square.

## License

MIT — see [LICENSE](LICENSE).
