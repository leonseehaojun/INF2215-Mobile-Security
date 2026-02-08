package com.example.inf2215

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp

enum class Screen {
    Login,
    Register,

    Home,
    Profile,
    CreatePost,
    TrackRun,

    ChatInbox,
    ChatRoom,

    Community,
    CreateGroup,
    GroupDetail,

    CreateGroupThread,
    GroupThreadDetail,

    AdminAnnouncements,
    AdminCreateAnnouncement,
    AdminReports,
    AdminLogs,
    AdminProfile,

    Notifications,
    AnnouncementDetail,
    PostDetail
}

data class NavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

data class FeedPost(
    val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val title: String = "",
    val text: String = "",
    val imageUrl: String? = null,
    val type: String = "NORMAL", // "NORMAL", "RUN", "THREAD"
    val runDistance: String? = null,
    val runDuration: String? = null,
    val runPace: String? = null,
    val route: List<LatLng> = emptyList(),
    val createdAt: Timestamp? = null,
    val likes: List<String> = emptyList(),
    val commentsCount: Int = 0,
    val groupId: String? = null,
    val runId: String? = null
)

data class Comment(
    val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val text: String = "",
    val timestamp: Timestamp? = null,
    val parentId: String? = null
)

data class ReportItem(
    val id: String = "",
    val targetUserId: String = "",
    val targetType: Int = 1, // 0: User, 1: Post, 2: Comment
    val attachedId: String? = null,
    val title: String = "",
    val category: String = "",
    val reason: String = "",
    val description: String = "",
    val reportedBy: String = "",
    val timestamp: Timestamp? = null
)

data class ProfileRunItem(
    val id: String = "",
    val title: String = "",
    val distanceStr: String = "",
    val durationStr: String = "",
    val route: List<LatLng> = emptyList(),
    val timestamp: Timestamp? = null
)

data class ChatThread(
    val chatId: String = "",
    val otherUserId: String = "",
    val otherName: String = "Chat",
    val lastMessage: String = "",
    val lastTimestamp: Timestamp? = null,
    val unreadCount: Int = 0
)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null
)

data class GroupThread(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val createdById: String = "",
    val createdByName: String = "Unknown",
    val createdAt: Timestamp? = null,
    val commentsCount: Int = 0,

    val type: String = "NORMAL", // "NORMAL" or "RUN"
    val runDistance: String? = null,
    val runDuration: String? = null,
    val runPace: String? = null,
    val route: List<LatLng> = emptyList()
)

data class GroupThreadComment(
    val id: String = "",
    val userId: String = "",
    val displayName: String = "User",
    val text: String = "",
    val createdAt: Timestamp? = null
)

enum class AnnouncementType {
    News, SystemNotice, ServiceAlert
}

data class Announcement(
    val id: String = "",
    val title: String = "",
    val type: String = "News", // AnnouncementType
    val description: String = "",
    val isIndefinite: Boolean = false,
    val startDate: Timestamp? = null,
    val endDate: Timestamp? = null,
    val createdAt: Timestamp? = null,
    val createdBy: String = "",
    val readBy: List<String> = emptyList()
)

fun getCategoryStyle(type: String) = when (type) {
    "News" -> Icons.Default.Circle to Color(0xFF4CAF50)
    "System Notice" -> Icons.Default.Circle to Color(0xFFFFC107)
    "Service Alert" -> Icons.Default.PriorityHigh to Color(0xFFF44336)
    else -> Icons.Default.Info to Color.Gray
}
