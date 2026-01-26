package com.example.inf2215

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.inf2215.ui.theme.INF2215Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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
                
                // Placeholder for unread notifications state
                var hasNewNotifications by remember { mutableStateOf(true) }

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

                val userNavItems = listOf(
                    NavItem(Screen.Home, "Home", Icons.Default.Home),
                    NavItem(Screen.Pending, "Pending", Icons.Default.Pending),
                    NavItem(Screen.CreatePost, "Post", Icons.Default.Add),
                    NavItem(Screen.Community, "Community", Icons.Default.Group),
                    NavItem(Screen.Profile, "Profile", Icons.Default.Person)
                )

                val adminNavItems = listOf(
                    NavItem(Screen.AdminAnnouncements, "Announce", Icons.Default.Campaign),
                    NavItem(Screen.AdminReports, "Reports", Icons.Default.Report),
                    NavItem(Screen.AdminLogs, "Logs", Icons.Default.History),
                    NavItem(Screen.AdminProfile, "Profile", Icons.Default.Person)
                )

                val isAdminMode = screen in listOf(
                    Screen.AdminAnnouncements, Screen.AdminReports, 
                    Screen.AdminLogs, Screen.AdminProfile
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
                                            onClick = { 
                                                if (isAdminMode) {
                                                    screen = Screen.Home
                                                } else {
                                                    screen = Screen.AdminAnnouncements
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = if (isAdminMode) MaterialTheme.colorScheme.primary else Color.Gray
                                            ),
                                            modifier = if (isAdminMode) {
                                                Modifier.background(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                            } else {
                                                Modifier
                                            }
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
                                            Screen.Profile, Screen.AdminProfile -> "Profile"
                                            Screen.TrackRun -> "Record Run"
                                            Screen.CreatePost -> "New Post"
                                            Screen.Pending -> "Pending"
                                            Screen.Community -> "Community"
                                            Screen.AdminAnnouncements -> "Admin Announcements"
                                            Screen.AdminReports -> "Admin Reports"
                                            Screen.AdminLogs -> "Admin Logs"
                                            Screen.Notifications -> "Alerts"
                                            else -> ""
                                        }
                                    )
                                },
                                actions = {
                                    if (screen in listOf(Screen.Home, Screen.Pending, Screen.Community, Screen.Notifications)) {
                                        val isNotifActive = screen == Screen.Notifications
                                        // Orange/Amber theme for Alerts
                                        val alertActiveColor = Color(0xFFF57C00) 
                                        val alertContainerColor = Color(0xFFFFF3E0)

                                        TextButton(
                                            onClick = { 
                                                if (isNotifActive) {
                                                    screen = Screen.Home
                                                } else {
                                                    screen = Screen.Notifications 
                                                    hasNewNotifications = false // Reset dot when clicked
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = if (isNotifActive) alertActiveColor else Color.Gray
                                            ),
                                            modifier = if (isNotifActive) {
                                                Modifier.background(
                                                    color = alertContainerColor,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                            } else {
                                                Modifier
                                            }
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                BadgedBox(
                                                    badge = {
                                                        if (hasNewNotifications) {
                                                            Badge(
                                                                modifier = Modifier
                                                                    .size(6.dp)
                                                                    .offset(x = (-4).dp, y = 3.dp),
                                                                containerColor = Color.Red
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Notifications,
                                                        contentDescription = "Notice"
                                                    )
                                                }
                                                Text(
                                                    text = "Notice",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    } else if (screen == Screen.Profile || screen == Screen.AdminProfile) {
                                        TextButton(
                                            onClick = {
                                                FirebaseAuth.getInstance().signOut()
                                                screen = Screen.Login
                                            },
                                            shape = RoundedCornerShape(12.dp),
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
                            val currentNavItems = if (isAdminMode) adminNavItems else userNavItems
                            NavigationBar {
                                currentNavItems.forEach { item ->
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

                            Screen.AdminProfile -> AdminProfileScreen()

                            Screen.CreatePost -> CreatePostScreen(
                                onPostSuccess = { screen = Screen.Home },
                                onCancel = { screen = Screen.Home }
                            )

                            Screen.TrackRun -> TrackRunScreen(
                                onRunFinished = { screen = Screen.Home },
                                onCancel = { screen = Screen.Home }
                            )

                            Screen.Notifications -> NotificationScreen()

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

                            Screen.AdminAnnouncements -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Admin Announcements Content")
                                }
                            }

                            Screen.AdminReports -> AdminReportsScreen()

                            Screen.AdminLogs -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Admin Logs Content")
                                }
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
