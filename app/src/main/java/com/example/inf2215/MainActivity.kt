package com.example.inf2215

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.inf2215.ui.theme.INF2215Theme

enum class Screen {
    Login, Register, Home, Profile, CreatePost, TrackRun
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            INF2215Theme {
                var screen by remember { mutableStateOf(Screen.Login) } // always start on login

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (screen) {
                        Screen.Login -> LoginScreen(
                            modifier = Modifier.padding(innerPadding),
                            onLoginSuccess = { screen = Screen.Home },
                            onGoRegister = { screen = Screen.Register }
                        )

                        Screen.Register -> RegisterScreen(
                            modifier = Modifier.padding(innerPadding),
                            onRegistered = { screen = Screen.Home },
                            onBackToLogin = { screen = Screen.Login }
                        )

                        Screen.Home -> HomeScreen(
                            modifier = Modifier.padding(innerPadding),
                            onLogout = { screen = Screen.Login },
                            onGoProfile = { screen = Screen.Profile },
                            // Pass navigation callbacks to Home
                            onNavigateToCreatePost = { screen = Screen.CreatePost },
                            onNavigateToTrackRun = { screen = Screen.TrackRun }
                        )

                        Screen.Profile -> ProfileScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBack = { screen = Screen.Home },  //back to home
                            onLogout = { screen = Screen.Login }    //logout to login
                        )

                        Screen.CreatePost -> CreatePostScreen(
                            modifier = Modifier.padding(innerPadding),
                            onPostSuccess = { screen = Screen.Home },
                            onCancel = { screen = Screen.Home }
                        )

                        Screen.TrackRun -> TrackRunScreen(
                            modifier = Modifier.padding(innerPadding),
                            onRunFinished = { screen = Screen.Home },
                            onCancel = { screen = Screen.Home }
                        )
                    }
                }
            }
        }
    }
}