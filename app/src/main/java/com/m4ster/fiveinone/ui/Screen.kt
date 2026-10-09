package com.m4ster.fiveinone.ui

/* The whole app is nine game screens plus home and settings. No navigation
   library: a single state in MainActivity switches screens, which keeps the
   back-stack trivial (every screen has an explicit back button). */

sealed interface Screen {
    data object Home : Screen
    data object Guessing : Screen
    data object Snake : Screen
    data object Ladders : Screen
    data object Ludo : Screen
    data object TicTacToe : Screen
    data object FourInARow : Screen
    data object Memory : Screen
    data object Twenty48 : Screen
    data object Mines : Screen
    data object Settings : Screen
}
