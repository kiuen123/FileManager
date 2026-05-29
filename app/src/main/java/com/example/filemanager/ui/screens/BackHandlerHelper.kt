package com.example.filemanager.ui.screens

import androidx.activity.compose.BackHandler as ComposeBackHandler
import androidx.compose.runtime.Composable

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    ComposeBackHandler(enabled = enabled, onBack = onBack)
}

