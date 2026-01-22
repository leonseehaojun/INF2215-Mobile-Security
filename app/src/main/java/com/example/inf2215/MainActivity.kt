package com.example.inf2215

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.inf2215.ui.theme.INF2215Theme
import com.google.firebase.auth.FirebaseAuth

enum class Screen {
    Login, Register, Home, Profile, CreatePost, TrackRun, Pending, Community
}

data class NavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            INF2215Theme {
                var screen by remember { mutableStateOf(Screen.Login) }

                val navItems = listOf(
                    NavItem(Screen.Home, "Home", Icons.Default.Home),
                    NavItem(Screen.Pending, "Pending", Icons.Default.Pending),
                    NavItem(Screen.TrackRun, "Record", Icons.Default.DirectionsRun),
                    NavItem(Screen.Community, "Community", Icons.Default.Group),
                    NavItem(Screen.Profile, "Profile", Icons.Default.Person)
                )

                val showBars = screen !in listOf(Screen.Login, Screen.Register)

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (showBars) {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        when (screen) {
                                            Screen.Home -> "Home"
                                            Screen.Profile -> "Profile"
                                            Screen.TrackRun -> "Record Run"
                                            Screen.CreatePost -> "New Post"
                                            Screen.Pending -> "Pending"
                                            Screen.Community -> "Community"
                                            else -> ""
                                        }
                                    )
                                },
                                actions = {
                                    TextButton(
                                        onClick = {
                                            FirebaseAuth.getInstance().signOut()
                                            screen = Screen.Login
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                                contentDescription = "Log Out"
                                            )
                                            Text(
                                                text = "Log Out",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    },
                    bottomBar = {
                        if (showBars && screen != Screen.CreatePost) {
                            NavigationBar {
                                navItems.forEach { item ->
                                    NavigationBarItem(
                                        selected = screen == item.screen,
                                        onClick = { screen = item.screen },
                                        label = { Text(item.label) },
                                        icon = { Icon(item.icon, contentDescription = item.label) }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        when (screen) {
                            Screen.Login -> LoginScreen(
                                onLoginSuccess = { screen = Screen.Home },
                                onGoRegister = { screen = Screen.Register }
                            )

                            Screen.Register -> RegisterScreen(
                                onRegistered = { screen = Screen.Home },
                                onBackToLogin = { screen = Screen.Login }
                            )

                            Screen.Home -> HomeScreen(
                                onLogout = { screen = Screen.Login },
                                onGoProfile = { screen = Screen.Profile },
                                onNavigateToCreatePost = { screen = Screen.CreatePost },
                                onNavigateToTrackRun = { screen = Screen.TrackRun }
                            )

                            Screen.Profile -> ProfileScreen(
                                onBack = { screen = Screen.Home },
                                onLogout = { screen = Screen.Login }
                            )

                            Screen.CreatePost -> CreatePostScreen(
                                onPostSuccess = { screen = Screen.Home },
                                onCancel = { screen = Screen.Home }
                            )

                            Screen.TrackRun -> TrackRunScreen(
                                onRunFinished = { screen = Screen.Home },
                                onCancel = { screen = Screen.Home }
                            )

                            Screen.Pending -> {
                                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("Pending Screen - Coming Soon")
                                }
                            }

                            Screen.Community -> {
                                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("Community Screen - Coming Soon")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
