package com.example.inf2215

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.inf2215.ui.theme.INF2215Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

enum class Screen {
    Login, Register, Home, Profile, CreatePost, TrackRun, Pending, Community, AdminDashboard
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
                var userRole by remember { mutableStateOf("public") }
                var showPostTypeDialog by remember { mutableStateOf(false) }

                val auth = FirebaseAuth.getInstance()
                val db = FirebaseFirestore.getInstance()

                // Fetch user role when logged in
                LaunchedEffect(auth.currentUser) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                            userRole = doc.getString("role") ?: "public"
                        }
                    } else {
                        userRole = "public"
                    }
                }

                val navItems = listOf(
                    NavItem(Screen.Home, "Home", Icons.Default.Home),
                    NavItem(Screen.Pending, "Pending", Icons.Default.Pending),
                    NavItem(Screen.CreatePost, "Post", Icons.Default.Add),
                    NavItem(Screen.Community, "Community", Icons.Default.Group),
                    NavItem(Screen.Profile, "Profile", Icons.Default.Person)
                )

                val showBars = screen !in listOf(Screen.Login, Screen.Register)

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (showBars) {
                            CenterAlignedTopAppBar(
                                navigationIcon = {
                                    if (userRole == "admin") {
                                        TextButton(
                                            onClick = { screen = Screen.AdminDashboard },
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Default.AdminPanelSettings,
                                                    contentDescription = "Admin"
                                                )
                                                Text(
                                                    text = "Admin",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                },
                                title = {
                                    Text(
                                        when (screen) {
                                            Screen.Home -> "Home"
                                            Screen.Profile -> "Profile"
                                            Screen.TrackRun -> "Record Run"
                                            Screen.CreatePost -> "New Post"
                                            Screen.Pending -> "Pending"
                                            Screen.Community -> "Community"
                                            Screen.AdminDashboard -> "Admin Dashboard"
                                            else -> ""
                                        }
                                    )
                                },
                                actions = {
                                    if (screen == Screen.Profile) {
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
                                        onClick = {
                                            if (item.screen == Screen.CreatePost) {
                                                showPostTypeDialog = true
                                            } else {
                                                screen = item.screen
                                            }
                                        },
                                        label = { Text(item.label) },
                                        icon = { Icon(item.icon, contentDescription = item.label) }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
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

                            Screen.AdminDashboard -> {
                                AdminDashboardScreen()
                            }
                        }
                    }

                    // Post Dialog
                    if (showPostTypeDialog) {
                        AlertDialog(
                            onDismissRequest = { showPostTypeDialog = false },
                            title = { Text("Create New") },
                            text = { Text("What would you like to post?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showPostTypeDialog = false
                                    screen = Screen.TrackRun
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.DirectionsRun, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Track Run")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showPostTypeDialog = false
                                    screen = Screen.CreatePost
                                }) {
                                    Icon(Icons.Default.Edit, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Text Post")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}