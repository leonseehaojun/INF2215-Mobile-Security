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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.inf2215.ui.theme.INF2215Theme
import com.google.firebase.Timestamp
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
                var selectedAnnouncementId by remember { mutableStateOf<String?>(null) }

                // Admin Review States
                var selectedReportItem by remember { mutableStateOf<ReportItem?>(null) }
                var selectedCommentId by remember { mutableStateOf<String?>(null) }
                var isAdminReviewMode by remember { mutableStateOf(false) }

                // For 1-to-1 chat
                var chatOtherUid by remember { mutableStateOf<String?>(null) }
                var chatOtherName by remember { mutableStateOf<String?>(null) }

                // Unread state
                var hasUnreadAnnouncements by remember { mutableStateOf(false) }
                var hasUnreadMessages by remember { mutableStateOf(false) }
                var hasUnreadCommunity by remember { mutableStateOf(false) }

                val auth = FirebaseAuth.getInstance()
                val db = FirebaseFirestore.getInstance()

                // Fetch user role and check for unread items
                LaunchedEffect(auth.currentUser) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                            userRole = doc.getString("role") ?: "public"
                        }

                        // Listen for unread announcements
                        db.collection("announcements").addSnapshotListener { snapshot, _ ->
                            val announcements = snapshot?.documents ?: emptyList()
                            val now = Timestamp.now()
                            hasUnreadAnnouncements = announcements.any { doc ->
                                val ann = doc.toObject(Announcement::class.java)
                                if (ann != null) {
                                    val isWithinDateRange = if (ann.isIndefinite) {
                                        true
                                    } else {
                                        val start = ann.startDate
                                        val end = ann.endDate
                                        (start == null || start <= now) && (end == null || end >= now)
                                    }
                                    isWithinDateRange && !ann.readBy.contains(uid)
                                } else false
                            }
                        }

                        // Listen for unread chat messages
                        db.collection("chats")
                            .whereArrayContains("participants", uid)
                            .addSnapshotListener { snapshot, _ ->
                                if (snapshot != null) {
                                    hasUnreadMessages = snapshot.documents.any { doc ->
                                        val unreadCount = doc.getLong("unreadCount_$uid") ?: 0
                                        unreadCount > 0
                                    }
                                }
                            }

                        // Listen for unread community thread replies
                        db.collection("groups")
                            .whereArrayContains("memberIds", uid)
                            .addSnapshotListener { snapshot, _ ->
                                if (snapshot != null) {
                                    hasUnreadCommunity = snapshot.documents.any { doc ->
                                        val unreadCount = doc.getLong("unreadCount_$uid") ?: 0
                                        unreadCount > 0
                                    }
                                }
                            }

                    } else {
                        userRole = "public"
                        hasUnreadAnnouncements = false
                        hasUnreadMessages = false
                        hasUnreadCommunity = false
                    }
                }

                // Bottom nav
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
                    Screen.AdminLogs, Screen.AdminProfile, Screen.AdminCreateAnnouncement
                ) || (isAdminReviewMode && screen == Screen.PostDetail)

                val showBars = screen !in listOf(Screen.Login, Screen.Register)

                // New logic to hide Bars for specific detail views
                val isViewingAdminReportDetail = screen == Screen.AdminReports && selectedReportItem != null
                val isViewingAnnouncementDetail = screen == Screen.AnnouncementDetail
                val hideBarsInternally = isViewingAdminReportDetail || isViewingAnnouncementDetail

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (showBars && !hideBarsInternally) {
                            CenterAlignedTopAppBar(
                                navigationIcon = {
                                    val needsBack = screen in listOf(
                                        Screen.PostDetail,
                                        Screen.GroupDetail,
                                        Screen.CreateGroupThread,
                                        Screen.GroupThreadDetail,
                                        Screen.ChatRoom,
                                        Screen.CreateGroup,
                                        Screen.AdminCreateAnnouncement
                                    )

                                    if (needsBack) {
                                        IconButton(onClick = {
                                            when (screen) {
                                                Screen.AdminCreateAnnouncement -> screen = Screen.AdminAnnouncements
                                                Screen.GroupDetail -> screen = Screen.Community
                                                Screen.CreateGroup -> screen = Screen.Community
                                                Screen.GroupThreadDetail -> screen = Screen.GroupDetail
                                                Screen.CreateGroupThread -> screen = Screen.GroupDetail
                                                Screen.PostDetail -> {
                                                    if (isAdminReviewMode) {
                                                        screen = Screen.AdminReports
                                                        isAdminReviewMode = false
                                                    } else {
                                                        screen = previousScreen
                                                    }
                                                }
                                                else -> screen = previousScreen
                                            }
                                        }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back"
                                            )
                                        }
                                    } else if (userRole == "admin") {
                                        TextButton(
                                            onClick = {
                                                if (isAdminMode) {
                                                    screen = Screen.Home
                                                    isAdminReviewMode = false
                                                    selectedReportItem = null
                                                }
                                                else screen = Screen.AdminAnnouncements
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
                                            Screen.CreateGroupThread -> "Create Thread"
                                            Screen.GroupThreadDetail -> "Thread"
                                            Screen.AdminAnnouncements -> "Admin Announcements"
                                            Screen.AdminCreateAnnouncement -> if (selectedAnnouncementId == null) "New Announcement" else "Edit Announcement"
                                            Screen.AdminReports -> "Admin Reports"
                                            Screen.AdminLogs -> "Admin Logs"
                                            Screen.Notifications -> "Notifications"
                                            Screen.PostDetail -> if (isAdminReviewMode) "Review Content" else "Post Details"
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
                                                        if (hasUnreadAnnouncements) {
                                                            Badge(
                                                                modifier = Modifier
                                                                    .size(8.dp)
                                                                    .offset(x = (-7).dp, y = 9.dp),
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
                        val hideBottomBarOnly = screen in listOf(
                            Screen.CreatePost,
                            Screen.PostDetail,
                            Screen.ChatRoom,
                            Screen.CreateGroup,
                            Screen.CreateGroupThread,
                            Screen.GroupThreadDetail,
                            Screen.AdminCreateAnnouncement
                        )

                        if (showBars && !hideBottomBarOnly && !hideBarsInternally) {
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
                                                if (isAdminMode) {
                                                    selectedReportItem = null
                                                    isAdminReviewMode = false
                                                }
                                            }
                                        },
                                        label = { Text(item.label) },
                                        icon = {
                                            val showBadge = (item.screen == Screen.ChatInbox && hasUnreadMessages) || 
                                                           (item.screen == Screen.Community && hasUnreadCommunity)
                                            val badgeColor = Color(0xFF0D47A1) // Unified Dark blue
                                            
                                            BadgedBox(
                                                badge = {
                                                    if (showBadge) {
                                                        Badge(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .offset(x = (5).dp, y = -7.dp),
                                                            containerColor = badgeColor
                                                        )
                                                    }
                                                }
                                            ) {
                                                Icon(item.icon, contentDescription = item.label)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(if (hideBarsInternally) PaddingValues(0.dp) else innerPadding)) {
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
                                },
                                onNavigateToThread = { groupId, threadId ->
                                    selectedGroupId = groupId
                                    selectedThreadId = threadId
                                    previousScreen = Screen.Home
                                    screen = Screen.GroupThreadDetail
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

                            Screen.Notifications -> NotificationScreen(
                                onAnnouncementClick = { announcementId ->
                                    selectedAnnouncementId = announcementId
                                    previousScreen = Screen.Notifications
                                    screen = Screen.AnnouncementDetail
                                }
                            )

                            Screen.AnnouncementDetail -> {
                                selectedAnnouncementId?.let { id ->
                                    AnnouncementDetailScreen(
                                        announcementId = id,
                                        onBack = { screen = previousScreen },
                                        onEdit = if (userRole == "admin") {
                                            { screen = Screen.AdminCreateAnnouncement }
                                        } else null
                                    )
                                }
                            }

                            Screen.ChatInbox -> ChatInboxScreen(
                                onOpenChat = { otherUid, otherName ->
                                    chatOtherUid = otherUid
                                    chatOtherName = otherName
                                    previousScreen = Screen.ChatInbox
                                    screen = Screen.ChatRoom
                                }
                            )

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
                                val gid = selectedGroupId
                                if (gid != null) {
                                    GroupDetailScreen(
                                        groupId = gid,
                                        onBack = { screen = Screen.Community },
                                        onCreateThread = { groupIdFromScreen ->
                                            selectedGroupId = groupIdFromScreen
                                            previousScreen = Screen.GroupDetail
                                            screen = Screen.CreateGroupThread
                                        },
                                        onOpenThread = { groupIdFromScreen, threadIdFromScreen ->
                                            selectedGroupId = groupIdFromScreen
                                            selectedThreadId = threadIdFromScreen
                                            previousScreen = Screen.GroupDetail
                                            screen = Screen.GroupThreadDetail
                                        }
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No group selected.")
                                    }
                                }
                            }

                            Screen.CreateGroupThread -> {
                                val gid = selectedGroupId
                                if (gid != null) {
                                    CreateGroupThreadScreen(
                                        groupId = gid,
                                        onCreated = { newTid ->
                                            selectedThreadId = newTid
                                            previousScreen = Screen.GroupDetail
                                            screen = Screen.GroupThreadDetail
                                        },
                                        onCancel = { screen = Screen.GroupDetail }
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No group selected.")
                                    }
                                }
                            }

                            Screen.GroupThreadDetail -> {
                                val gid = selectedGroupId
                                val tid = selectedThreadId
                                if (gid != null && tid != null) {
                                    GroupThreadDetailScreen(
                                        groupId = gid,
                                        threadId = tid,
                                        onBack = { screen = previousScreen }
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No thread selected.")
                                    }
                                }
                            }

                            Screen.AdminAnnouncements -> AdminAnnouncementsScreen(
                                onNavigateToCreate = { 
                                    selectedAnnouncementId = null
                                    screen = Screen.AdminCreateAnnouncement 
                                },
                                onAnnouncementClick = { id ->
                                    selectedAnnouncementId = id
                                    previousScreen = Screen.AdminAnnouncements
                                    screen = Screen.AnnouncementDetail
                                }
                            )

                            Screen.AdminCreateAnnouncement -> CreateAnnouncementScreen(
                                onBack = { screen = Screen.AdminAnnouncements },
                                announcementId = selectedAnnouncementId
                            )

                            Screen.AdminReports -> AdminReportsScreen(
                                selectedReport = selectedReportItem,
                                onReportSelected = { selectedReportItem = it },
                                onReviewPost = { postId, reportId ->
                                    selectedPostId = postId
                                    isAdminReviewMode = true
                                    previousScreen = Screen.AdminReports
                                    screen = Screen.PostDetail
                                },
                                onReviewComment = { postId, commentId, reportId ->
                                    selectedPostId = postId
                                    selectedCommentId = commentId
                                    isAdminReviewMode = true
                                    previousScreen = Screen.AdminReports
                                    screen = Screen.PostDetail
                                }
                            )

                            Screen.AdminLogs -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Admin Logs Content")
                                }
                            }

                            Screen.PostDetail -> {
                                selectedPostId?.let { postId ->
                                    PostDetailScreen(
                                        postId = postId,
                                        onBack = { 
                                            if (isAdminReviewMode) {
                                                screen = Screen.AdminReports
                                                isAdminReviewMode = false
                                            } else {
                                                screen = previousScreen
                                            }
                                        },
                                        highlightCommentId = selectedCommentId,
                                        isAdminReview = isAdminReviewMode,
                                        reportId = selectedReportItem?.id
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
