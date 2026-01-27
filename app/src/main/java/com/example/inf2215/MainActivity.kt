package com.example.inf2215

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                var previousScreen by remember { mutableStateOf(Screen.Home) }

                var userRole by remember { mutableStateOf("public") }
                var showPostTypeDialog by remember { mutableStateOf(false) }

                var selectedPostId by remember { mutableStateOf<String?>(null) }
                var selectedGroupId by remember { mutableStateOf<String?>(null) }
                var selectedThreadId by remember { mutableStateOf<String?>(null) }

                // For 1-to-1 chat
                var chatOtherUid by remember { mutableStateOf<String?>(null) }
                var chatOtherName by remember { mutableStateOf<String?>(null) }

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

                // ✅ Bottom nav: use ChatInbox as the tab (NOT direct Chat)
                val userNavItems = listOf(
                    NavItem(Screen.Home, "Home", Icons.Default.Home),
                    NavItem(Screen.ChatInbox, "Chat", Icons.Default.Chat),
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
                                    // Show back icon for detail-type screens
                                    val needsBack = screen in listOf(
                                        Screen.PostDetail,
                                        Screen.GroupDetail,
                                        Screen.ThreadDetail,
                                        Screen.ChatRoom,
                                        Screen.CreateGroup,
                                        Screen.CreateThread
                                    )

                                    if (needsBack) {
                                        IconButton(onClick = { screen = previousScreen }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                        }
                                    } else if (userRole == "admin") {
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
                                            } else Modifier
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin")
                                                Text("Admin", style = MaterialTheme.typography.labelSmall)
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

                                            Screen.ChatInbox -> "Chats"
                                            Screen.ChatRoom -> (chatOtherName ?: "Chat")

                                            Screen.Community -> "Community"
                                            Screen.CreateGroup -> "Create Group"
                                            Screen.GroupDetail -> "Group"
                                            Screen.CreateThread -> "Create Thread"
                                            Screen.ThreadDetail -> "Thread"

                                            Screen.AdminAnnouncements -> "Admin Announcements"
                                            Screen.AdminReports -> "Admin Reports"
                                            Screen.AdminLogs -> "Admin Logs"

                                            Screen.Notifications -> "Alerts"
                                            Screen.PostDetail -> "Post Details"
                                            else -> ""
                                        }
                                    )
                                },
                                actions = {
                                    if (screen in listOf(Screen.Home, Screen.ChatInbox, Screen.Community, Screen.Notifications)) {
                                        val isNotifActive = screen == Screen.Notifications
                                        val alertActiveColor = Color(0xFFF57C00)
                                        val alertContainerColor = Color(0xFFFFF3E0)

                                        TextButton(
                                            onClick = {
                                                if (isNotifActive) {
                                                    screen = Screen.Home
                                                } else {
                                                    previousScreen = screen
                                                    screen = Screen.Notifications
                                                    hasNewNotifications = false
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = if (isNotifActive) alertActiveColor else Color.Gray
                                            ),
                                            modifier = if (isNotifActive) {
                                                Modifier.background(alertContainerColor, RoundedCornerShape(12.dp))
                                            } else Modifier
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
                                                    Icon(Icons.Default.Notifications, contentDescription = "Notice")
                                                }
                                                Text("Notice", style = MaterialTheme.typography.labelSmall)
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
                                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log Out")
                                                Text("Log Out", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    },
                    bottomBar = {
                        // ✅ hide bottom bar on CreatePost + detail screens for cleaner UX
                        val hideBottomBar = screen in listOf(
                            Screen.CreatePost,
                            Screen.PostDetail,
                            Screen.ChatRoom,
                            Screen.GroupDetail,
                            Screen.ThreadDetail,
                            Screen.CreateGroup,
                            Screen.CreateThread
                        )

                        if (showBars && !hideBottomBar) {
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
                                onNavigateToTrackRun = { screen = Screen.TrackRun },
                                onNavigateToPostDetail = { postId ->
                                    previousScreen = Screen.Home
                                    selectedPostId = postId
                                    screen = Screen.PostDetail
                                }
                            )

                            Screen.Profile -> ProfileScreen(
                                onBack = { screen = Screen.Home },
                                onLogout = { screen = Screen.Login },
                                onStartChat = { otherUid, otherName ->
                                    chatOtherUid = otherUid
                                    chatOtherName = otherName
                                    previousScreen = Screen.Profile
                                    screen = Screen.ChatRoom
                                }
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

                            //Chat tab goes here (inbox/list of chats)
                            Screen.ChatInbox -> ChatInboxScreen(
                                onOpenChat = { otherUid, otherName ->
                                    chatOtherUid = otherUid
                                    chatOtherName = otherName
                                    previousScreen = Screen.ChatInbox
                                    screen = Screen.ChatRoom
                                }
                            )

                            // 1-to-1 chat screen (opened from inbox/friends)
                            Screen.ChatRoom -> {
                                val otherUid = chatOtherUid
                                val otherName = chatOtherName
                                if (otherUid != null && otherName != null) {
                                    ChatScreen(
                                        otherUserId = otherUid,
                                        otherDisplayName = otherName,
                                        onBack = { screen = previousScreen }
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No chat selected.")
                                    }
                                }
                            }

                            Screen.Community -> CommunityScreen(
                                onCreateGroup = {
                                    previousScreen = Screen.Community
                                    screen = Screen.CreateGroup
                                },
                                onOpenGroup = { groupId ->
                                    selectedGroupId = groupId
                                    previousScreen = Screen.Community
                                    screen = Screen.GroupDetail
                                },
                                onCreateThread = {
                                    previousScreen = Screen.Community
                                    screen = Screen.CreateThread
                                },
                                onOpenThread = { threadId ->
                                    selectedThreadId = threadId
                                    previousScreen = Screen.Community
                                    screen = Screen.ThreadDetail
                                }
                            )

                            Screen.CreateGroup -> CreateGroupScreen(
                                onCreated = { newGroupId ->
                                    selectedGroupId = newGroupId
                                    previousScreen = Screen.Community
                                    screen = Screen.GroupDetail
                                },
                                onCancel = { screen = Screen.Community }
                            )

                            Screen.GroupDetail -> {
                                selectedGroupId?.let { gid ->
                                    GroupDetailScreen(
                                        groupId = gid,
                                        onBack = { screen = Screen.Community }
                                    )
                                }
                            }

                            Screen.CreateThread -> CreateThreadScreen(
                                onCreated = { newThreadId ->
                                    selectedThreadId = newThreadId
                                    previousScreen = Screen.Community
                                    screen = Screen.ThreadDetail
                                },
                                onCancel = { screen = Screen.Community }
                            )

                            Screen.ThreadDetail -> {
                                selectedThreadId?.let { tid ->
                                    ThreadDetailScreen(
                                        threadId = tid,
                                        onBack = { screen = Screen.Community }
                                    )
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

                            Screen.PostDetail -> {
                                selectedPostId?.let { postId ->
                                    PostDetailScreen(
                                        postId = postId,
                                        onBack = { screen = previousScreen }
                                    )
                                }
                            }

                            else -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Screen not implemented.")
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
                                    previousScreen = Screen.Home
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
                                    previousScreen = Screen.Home
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
