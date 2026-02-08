package com.example.inf2215

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatInboxScreen(
    onOpenChat: (otherUid: String, otherName: String) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = auth.currentUser?.uid

    var chats by remember { mutableStateOf(listOf<ChatThread>()) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(uid) {
        if (uid == null) {
            status = "Not logged in"
            return@LaunchedEffect
        }

        db.collection("chats")
            .whereArrayContains("participants", uid)
            .orderBy("lastTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    status = "Error loading chats: ${e.message}"
                    return@addSnapshotListener
                }

                chats = snap?.documents?.mapNotNull { d ->
                    val parts = d.get("participants") as? List<String> ?: return@mapNotNull null
                    val otherId = parts.firstOrNull { it != uid } ?: return@mapNotNull null
                    val otherName = d.getString("otherName_$uid") ?: "Chat"
                    val unread = d.getLong("unreadCount_$uid")?.toInt() ?: 0
                    
                    ChatThread(
                        chatId = d.id,
                        otherUserId = otherId,
                        otherName = otherName,
                        lastMessage = d.getString("lastMessage") ?: "",
                        lastTimestamp = d.getTimestamp("lastTimestamp"),
                        unreadCount = unread
                    )
                } ?: emptyList()
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (uid == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Please login first.")
            }
            return@Column
        }

        if (chats.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No chats yet. Start one from Profile.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(chats) { c ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onOpenChat(c.otherUserId, c.otherName)
                        },
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(c.otherName, fontWeight = FontWeight.Bold)
                                    
                                    val timeStr = c.lastTimestamp?.toDate()?.let {
                                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                        sdf.format(it)
                                    } ?: ""
                                    Text(
                                        text = timeStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                
                                Spacer(Modifier.height(4.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (c.lastMessage.isBlank()) "Tap to start chatting" else c.lastMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    if (c.unreadCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .size(26.dp)
                                                .background(Color(0xFF0D47A1), CircleShape), // Darker blue
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = c.unreadCount.toString(),
                                                color = Color.White,
                                                fontSize = 12.sp, // Larger font
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.offset(y = (-0.5).dp) // Fine-tune centering
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
