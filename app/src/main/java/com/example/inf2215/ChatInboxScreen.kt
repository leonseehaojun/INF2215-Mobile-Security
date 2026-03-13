package com.example.inf2215

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

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
                    ChatThread(
                        chatId = d.id,
                        otherUserId = otherId,
                        otherName = otherName,
                        lastMessage = d.getString("lastMessage") ?: "",
                        lastTimestamp = d.getTimestamp("lastTimestamp")
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
                Text("No chats yet. Start one from Profile → Friends.")
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
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(c.otherName, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (c.lastMessage.isBlank()) "Tap to start chatting" else c.lastMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
