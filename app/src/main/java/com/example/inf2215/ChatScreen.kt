package com.example.inf2215

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ChatScreen(
    otherUserId: String,
    otherDisplayName: String,
    onBack: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val myUid = auth.currentUser?.uid

    var chatId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(myUid, otherUserId) {
        if (myUid == null) {
            status = "Not logged in"
            return@LaunchedEffect
        }

        chatId = if (myUid < otherUserId) "${myUid}_${otherUserId}" else "${otherUserId}_${myUid}"
        val cid = chatId!!

        // Ensure chat doc exists and has all fields
        ensureChatDocExists(db, myUid, otherUserId)

        // Clear unread count for me initially
        db.collection("chats").document(cid).update("unreadCount_$myUid", 0)

        db.collection("chats").document(cid)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    status = "Error loading messages: ${e.message}"
                    return@addSnapshotListener
                }

                messages = snap?.documents?.map { d ->
                    ChatMessage(
                        id = d.id,
                        senderId = d.getString("senderId") ?: "",
                        text = d.getString("text") ?: "",
                        createdAt = d.getTimestamp("createdAt")
                    )
                } ?: emptyList()
            }
    }

    // Clear unread count when new messages arrive while viewing the chat
    LaunchedEffect(messages) {
        if (messages.isNotEmpty() && messages.last().senderId != myUid) {
            chatId?.let { cid ->
                db.collection("chats").document(cid).update("unreadCount_$myUid", 0)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {

        if (status.isNotBlank()) {
            Text(status, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (myUid == null || chatId == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Please login first.")
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(messages) { index, m ->
                // Date Separator Logic
                val showDate = if (index == 0) {
                    true
                } else {
                    val prev = messages[index - 1].createdAt?.toDate()
                    val curr = m.createdAt?.toDate()
                    if (prev != null && curr != null) {
                        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                        sdf.format(prev) != sdf.format(curr)
                    } else false
                }

                if (showDate) {
                    val dateStr = m.createdAt?.toDate()?.let {
                        SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault()).format(it)
                    } ?: ""
                    if (dateStr.isNotBlank()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = dateStr,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                val isMe = m.senderId == myUid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        tonalElevation = 2.dp,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isMe) 16.dp else 4.dp,
                            bottomEnd = if (isMe) 4.dp else 16.dp
                        ),
                        color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                        ) {
                            Text(
                                text = m.text,
                                textAlign = if (isMe) TextAlign.End else TextAlign.Start
                            )
                            val timeStr = m.createdAt?.toDate()?.let {
                                SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
                            } ?: ""
                            Text(
                                text = timeStr,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message ${otherDisplayName}...") },
                shape = RoundedCornerShape(24.dp)
            )
            
            FilledIconButton(
                enabled = input.isNotBlank(),
                onClick = {
                    val text = input.trim()
                    input = ""
                    sendMessage(
                        db = db,
                        chatId = chatId!!,
                        myUid = myUid,
                        otherUid = otherUserId,
                        text = text
                    )
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

private fun ensureChatDocExists(db: FirebaseFirestore, myUid: String, otherUid: String) {
    val chatId = if (myUid < otherUid) "${myUid}_${otherUid}" else "${otherUid}_${myUid}"
    val chatRef = db.collection("chats").document(chatId)

    chatRef.get().addOnSuccessListener { doc ->
        val needsInit = !doc.exists() || !doc.contains("unreadCount_$myUid") || !doc.contains("otherName_$myUid")
        
        if (needsInit) {
            db.collection("users").document(myUid).get().addOnSuccessListener { myDoc ->
                val myName = myDoc.getString("displayName") ?: "Runner"

                db.collection("users").document(otherUid).get().addOnSuccessListener { otherDoc ->
                    val otherName = otherDoc.getString("displayName") ?: "Runner"

                    val data = hashMapOf(
                        "participants" to listOf(myUid, otherUid),
                        "lastMessage" to (doc.getString("lastMessage") ?: ""),
                        "lastTimestamp" to (doc.getTimestamp("lastTimestamp") ?: Timestamp.now()),
                        "otherName_$myUid" to otherName,
                        "otherName_$otherUid" to myName,
                        "unreadCount_$myUid" to (doc.getLong("unreadCount_$myUid") ?: 0L),
                        "unreadCount_$otherUid" to (doc.getLong("unreadCount_$otherUid") ?: 0L)
                    )
                    chatRef.set(data, SetOptions.merge())
                }
            }
        }
    }
}

private fun sendMessage(
    db: FirebaseFirestore,
    chatId: String,
    myUid: String,
    otherUid: String,
    text: String
) {
    val now = Timestamp.now()
    val chatRef = db.collection("chats").document(chatId)
    val msgRef = chatRef.collection("messages").document()

    val msgData = hashMapOf(
        "senderId" to myUid,
        "text" to text,
        "createdAt" to now
    )

    db.runBatch { batch ->
        batch.set(msgRef, msgData)
        batch.update(chatRef, mapOf(
            "lastMessage" to text,
            "lastTimestamp" to now,
            "unreadCount_$otherUid" to FieldValue.increment(1)
        ))
    }
}
