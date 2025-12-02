package com.example.localchatbot

sealed class Screen {
    data object Chat : Screen()
    data object Settings : Screen()
}
