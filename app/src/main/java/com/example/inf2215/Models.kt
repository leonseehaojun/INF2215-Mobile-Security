package com.example.inf2215

import androidx.compose.ui.graphics.vector.ImageVector
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp

enum class Screen {
    Login, Register, Home, Profile, CreatePost, TrackRun, Pending, Community, 
    AdminAnnouncements, AdminReports, AdminLogs, AdminProfile, Notifications,
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
    val type: String = "NORMAL",
    val runDistance: String? = null,
    val runDuration: String? = null,
    val route: List<LatLng> = emptyList(),
    val createdAt: Timestamp? = null,
    val likes: List<String> = emptyList(),
    val commentsCount: Int = 0
)

data class Comment(
    val id: String = "",
    val postId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val text: String = "",
    val timestamp: Timestamp? = null,
    val parentId: String? = null // For replies
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
