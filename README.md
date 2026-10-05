# 5IN1 — Android

Five classic games in one native Android app. Clean, readable Kotlin +
Jetpack Compose (Material3) code, no exotic dependencies, fully offline.

Portrait only · minSdk 26 · targetSdk 34 · version 1.3.0

## The five games

| Game | How it plays |
|---|---|
| Number Guessing | Three difficulties (1–50, 1–100, 1–200) with attempt limits, custom digit keypad, animated hot/cold meter, guess-history chips, and persisted wins / best / streak. The with-hints build adds cryptic wordplay hints — 3 per round, never naming the number outright. |
| Snake | Canvas snake with Chill / Normal / Insane speeds, solid or wrap-around walls, D-pad plus swipe steering, pulsing food, rare golden food, combo multiplier up to x5, persisted high score with a "new best" celebration. |
| Snakes & Ladders | 1 player vs CPU or 2–4 players pass-and-play. 10×10 boustrophedon board, tumbling dice, tokens that hop square by square, ladders and snakes with portal flashes. Exact roll to finish. |
| Ludo | Simplified: you vs CPU or 2 players pass-and-play. Two tokens each, roll a 6 to leave base, green squares are safe, captures send tokens back with a flash, extra turn on a 6, exact roll to finish. Dice tumbles, tokens glide. |
| Tic-Tac-Toe | Vs CPU (Easy random / Hard full minimax — Hard never loses) or 2-player pass-and-play. Win-line highlight, popping marks, named players, scoreboard cards, round counter, animated CPU thinking dots. |

Every win ends with a confetti celebration, and key interactions have haptic
feedback. Stats (guessing wins/streak, snake high score) persist on-device;
the Settings screen can wipe them.

## Two builds

| Flavor | Package | Hints |
|---|---|---|
| with-hints | `com.m4ster.fiveinone` | Cryptic hints in Number Guessing |
| no-hints | `com.m4ster.fiveinone.nohints` | Pure guessing, no Hint button |

Both install side by side. Get them from the
[releases page](https://github.com/m4sternoob/m4ster-5in1-android/releases).

## Controls

- **Guessing**: digit keypad, Guess to submit, Give up to reveal.
- **Snake**: swipe on the board or use the D-pad. Pause any time.
- **Snakes & Ladders / Ludo**: tap Roll; in Ludo tap a glowing token to move it.
- **Tic-Tac-Toe**: tap a square.

## Build locally

You need Android Studio (Hedgehog or newer) **or** a JDK 17 + the Android SDK.

```bash
# Android Studio: File → Open → this folder, then Run ▶

# Command line (SDK must be installed; point local.properties at it):
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleDebug
# APKs land at: app/build/outputs/apk/withHints/debug/ and .../noHints/debug/
```

Install a debug APK straight onto a Pixel — no signing setup needed:

```bash
adb install -r app/build/outputs/apk/withHints/debug/app-withHints-debug.apk
```

## CI

`.github/workflows/build-apk.yml` builds both flavor APKs on every push to
`main` (plus manual dispatch) with `ubuntu-latest`, Temurin JDK 17, and
`./gradlew assembleDebug`, then uploads them as the `5in1-apks` artifact.

## Project structure

```
app/src/main/
├── AndroidManifest.xml              # portrait-only launcher activity
├── java/com/m4ster/fiveinone/
│   ├── MainActivity.kt              # screen switcher (no nav library)
│   ├── ui/Screen.kt                 # the seven screens
│   ├── ui/theme/Theme.kt            # dark arcade Material3 palette
│   ├── ui/components/
│   │   ├── GameScaffold.kt          # title bar + back button shell
│   │   └── Celebration.kt           # confetti win overlay
│   ├── ui/home/HomeScreen.kt        # game cards with live stats
│   ├── ui/settings/SettingsScreen.kt# stats reset + about
│   ├── ui/stats/StatsStore.kt       # DataStore persistence
│   ├── ui/guessing/GuessingScreen.kt
│   ├── ui/guessing/HintEngine.kt    # cryptic hint generator
│   ├── ui/snake/SnakeScreen.kt      # canvas + coroutine game loop
│   ├── ui/ladders/LaddersScreen.kt  # 10×10 boustrophedon canvas board
│   ├── ui/ludo/LudoScreen.kt        # 24-cell track, tap-to-move tokens
│   └── ui/tictactoe/TicTacToeScreen.kt  # minimax CPU
└── res/                             # strings, theme, vector launcher icon
```

## Privacy

Fully offline: no accounts, no analytics, no ads, no network permission.
Stats never leave the device.

## License

MIT — see [LICENSE](LICENSE).
