package com.example.filemanager

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.example.filemanager.ui.screens.DriveScreen
import com.example.filemanager.ui.screens.FileManagerScreen
import com.example.filemanager.ui.screens.NetworkStorageScreen
import com.example.filemanager.ui.screens.PermissionScreen
import com.example.filemanager.ui.screens.SplashScreen
import com.example.filemanager.ui.theme.FileManagerTheme
import com.example.filemanager.viewmodel.DriveViewModel
import com.example.filemanager.viewmodel.FileManagerViewModel
import com.example.filemanager.viewmodel.NetworkStorageViewModel

enum class AppScreen { SPLASH, PERMISSION, FILE_MANAGER, GOOGLE_DRIVE, NETWORK_STORAGE }

class MainActivity : ComponentActivity() {
    private val fileManagerViewModel: FileManagerViewModel by viewModels()
    private val driveViewModel: DriveViewModel by viewModels()
    private val networkViewModel: NetworkStorageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FileManagerTheme {
                var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }

                when (currentScreen) {
                    AppScreen.SPLASH -> {
                        SplashScreen {
                            currentScreen = if (Environment.isExternalStorageManager()) {
                                AppScreen.FILE_MANAGER
                            } else {
                                AppScreen.PERMISSION
                            }
                        }
                    }
                    AppScreen.PERMISSION -> PermissionScreen()
                    AppScreen.FILE_MANAGER -> {
                        FileManagerScreen(
                            vm = fileManagerViewModel,
                            onOpenDrive = { currentScreen = AppScreen.GOOGLE_DRIVE },
                            onOpenNetwork = { currentScreen = AppScreen.NETWORK_STORAGE }
                        )
                    }
                    AppScreen.GOOGLE_DRIVE -> {
                        DriveScreen(
                            vm = driveViewModel,
                            onBack = { currentScreen = AppScreen.FILE_MANAGER }
                        )
                    }
                    AppScreen.NETWORK_STORAGE -> {
                        NetworkStorageScreen(
                            vm = networkViewModel,
                            onBack = { currentScreen = AppScreen.FILE_MANAGER }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }
}