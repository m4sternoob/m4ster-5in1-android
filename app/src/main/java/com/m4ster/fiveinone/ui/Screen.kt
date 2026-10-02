package com.m4ster.fiveinone.ui

/* The whole app is five game screens plus home. No navigation library:
   a single state in MainActivity switches screens, which keeps the
   back-stack trivial (every game screen has an explicit back button). */

sealed interface Screen {
    data object Home : Screen
    data object Guessing : Screen
    data object Snake : Screen
    data object Ladders : Screen
    data object Ludo : Screen
    data object TicTacToe : Screen
}
