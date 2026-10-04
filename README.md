# 5IN1 — Android

Five games in one native Android app, written in Kotlin with Jetpack Compose (Material3). Everything runs on-device: no accounts, no network, no ads, no analytics.

## Download

Two builds, same games. Pick the Number Guessing experience you want; both install side by side.

| Build | APK | Package | Size | SHA-256 |
|---|---|---|---|---|
| With hints | [5IN1-Android-v1.1.0-with-hints.apk](https://github.com/m4sternoob/m4ster-5in1-android/releases/download/v1.1.0/5IN1-Android-v1.1.0-with-hints.apk) | `com.m4ster.fiveinone` | 14.9 MB | `5a0e538d8f9b71532ecae9fc40c3d57d8df148dab5bb29b338c5627df82b4ef5` |
| No hints | [5IN1-Android-v1.1.0-no-hints.apk](https://github.com/m4sternoob/m4ster-5in1-android/releases/download/v1.1.0/5IN1-Android-v1.1.0-no-hints.apk) | `com.m4ster.fiveinone.nohints` | 14.9 MB | `1785684eda35dc72cbc3d488175197a0885346978333b792c7609b2e6996951d` |

- **With hints:** Number Guessing ships a Hint button with 3 cryptic hints per round (riddles, digit wordplay, math clues, range hints). It never names the number outright.
- **No hints:** pure guessing. The hint UI is compiled out of this build entirely.
- Both APKs are debug-signed with the Android SDK debug key and install directly.
- Requires Android 8.0 (API 26) or later. Portrait only.

### Install

1. Download the APK on your phone.
2. Tap it. If asked, allow "install unknown apps" for your browser or files app.
3. Open **5IN1** (or **5IN1 No Hints**) from the app drawer.

## Games

### Number Guessing

Guess the number from 1 to 100. Every guess returns a Higher/Lower verdict plus a hot/cold proximity read based on distance bands. Wrong guesses are tracked and feed a range hint. Win and lose states with a New Game reset.

The with-hints build adds a Hint button (bulb icon): 3 hints per round from a dedicated riddle engine covering trivia associations (14 is Valentine's Day), digit-shape wordplay (8 is "a zero stacked on a zero"), math clues (prime, square, digit sum), and a range hint built from your wrong guesses.

Controls: on-screen keypad, Guess and New Game buttons, Hint button (with-hints build only).

### Snake

Classic canvas snake. Steer with swipe gestures or the on-screen D-pad (Up, Down, Left, Right). 180-degree reversals are ignored so you cannot kill the snake by accident. Eat to grow and score. Includes Start, Pause, and Restart.

Controls: swipe anywhere on the board, or tap the D-pad buttons.

### Snakes & Ladders

Race to square 100 on a 10x10 boustrophedon board. 7 ladders and 8 snakes drawn as colored links (green climbs, red slides). Two modes: **1 Player** against a CPU that rolls on its own beat, or **2 Players** pass-and-play on the same phone. You need an exact roll to finish; overshooting keeps you where you are. Tokens offset when both land on the same square.

Controls: Roll button, plus Restart and Mode buttons to reset or switch modes.

### Ludo

Simplified Ludo, you versus the CPU. 24-cell track with a base strip; safe squares tinted green. Roll a 6 to bring a token out of base and roll again. Tap a glowing token to move it. The CPU prefers captures, then any legal move, and chains its extra turns on its own beat.

Controls: Roll button, tap highlighted tokens.

### Tic-Tac-Toe

You (X) versus the CPU (O). Full minimax with depth tie-breaking: the CPU plays perfectly when a forced win exists and prefers faster wins.

Controls: tap a square, New Game to reset.

## Visual feedback

- The Snake, Ladders, and Ludo boards are drawn on Compose Canvas and redraw on every game tick or move. No pre-rendered sprites.
- The dice shows a die face per roll; tokens, highlights, and status messages update the moment state changes.
- The hot/cold meter and hint card in Number Guessing update inline as you play.
- Motion comes from real-time state-driven redraws. There are no canned animation timelines.

## Variants

The two APKs are Gradle product flavors of the same source:

- `withHints` sets `BuildConfig.HINTS_ENABLED = true`, package `com.m4ster.fiveinone`, app name "5IN1".
- `noHints` sets `BuildConfig.HINTS_ENABLED = false`, package `com.m4ster.fiveinone.nohints`, app name "5IN1 No Hints".

Different package IDs mean both install and run side by side with separate icons and data.

## Project structure

```
app/src/main/java/com/m4ster/fiveinone/
├── MainActivity.kt
└── ui/
    ├── Screen.kt                  # navigation destinations
    ├── components/GameScaffold.kt # shared title bar + back button shell
    ├── guessing/GuessingScreen.kt  # game UI, keypad, hint card
    ├── guessing/HintEngine.kt      # cryptic hint riddles (with-hints build)
    ├── home/HomeScreen.kt          # game picker
    ├── ladders/LaddersScreen.kt    # board, CPU + 2P modes
    ├── ludo/LudoScreen.kt          # track, tap-to-move, CPU
    ├── snake/SnakeScreen.kt        # canvas loop, swipe + D-pad
    ├── theme/Theme.kt              # shared dark arcade palette
    └── tictactoe/TicTacToeScreen.kt# minimax CPU
```

## Build

Requires JDK 17. From the repo root:

```
./gradlew assembleDebug
```

This builds both flavors. APKs land in `app/build/outputs/apk/withHints/debug/` and `app/build/outputs/apk/noHints/debug/`. CI builds both on every push to `main` and uploads them as the `5in1-apks` artifact.

## Privacy

Fully offline. The manifest requests no network permission and the source contains no network code: no accounts, no analytics, no ads, no tracking. Game state lives only in memory while you play.

## License

MIT, see [LICENSE](LICENSE).
