# 5IN1 — Android

Six classic games in one native Android app. Clean, readable Kotlin +
Jetpack Compose (Material3) code, no exotic dependencies. Fully offline:
no accounts, ads, analytics, or network permission.

Package: `com.m4ster.fiveinone` · portrait only · minSdk 26 · targetSdk 34

## The six games

| Game | How it plays |
|---|---|
| Number Guessing | Difficulties (Easy 1–50 / Medium 1–100 / Hard 1–200), custom keypad, hot/cold meter, guess history, Give Up. Three cryptic hints per round in the with-hints edition. Wins/best/streak persisted. |
| Snake | Chill / Normal / Insane speeds, solid or wrap-around walls, D-pad + swipe, pulsing food, golden food with combo multiplier up to x5. High score persisted. |
| Snakes & Ladders | 1P vs CPU or 2–4 player pass-and-play. Tumbling dice, square-by-square token hops, portal flashes. Rolling a 6 earns another roll. Session tally. |
| Ludo | Vs CPU or 2-player pass-and-play. Tumbling dice, gliding tokens, capture flash. Session tally. |
| Tic-Tac-Toe | Vs CPU (Easy random / Hard minimax) or 2-player. Classic or Misère (3 in a row loses) rules. Scoreboard, win-line highlight, round counter. |
| Memory Match | Flip pairs of cards in as few moves as possible. Easy 4×3 / Medium 4×4 / Hard 6×4 grids. Fewest-moves best persisted. |

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
│   ├── ui/home/HomeScreen.kt      # the 6 game cards + live stats
│   ├── ui/guessing/GuessingScreen.kt
│   ├── ui/snake/SnakeScreen.kt    # canvas + coroutine game loop
│   ├── ui/ladders/LaddersScreen.kt    # 10×10 boustrophedon canvas board
│   ├── ui/ludo/LudoScreen.kt      # 24-cell track, tap-to-move tokens
│   ├── ui/tictactoe/TicTacToeScreen.kt  # minimax CPU, classic + misere
│   └── ui/memory/MemoryScreen.kt  # flip-pair matching + MemoryGame deck
└── res/                           # strings, theme, vector launcher icon
```

## Controls

- **Guessing**: type a number, tap Guess (or Done on the keyboard).
- **Snake**: swipe anywhere on the board to steer.
- **Snakes & Ladders / Ludo**: tap Roll; in Ludo tap a glowing token to move it.
- **Tic-Tac-Toe**: tap a square.
- **Memory Match**: tap a card to flip it.

## License

MIT — see [LICENSE](LICENSE).
