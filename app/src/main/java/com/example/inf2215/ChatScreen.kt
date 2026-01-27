package com.example.inf2215

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

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

        // Ensure chat doc exists (so inbox sorting works)
        ensureChatDocExists(db, myUid, otherUserId)

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
            items(messages) { m ->
                val isMe = m.senderId == myUid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = m.text,
                            modifier = Modifier.padding(10.dp),
                            textAlign = if (isMe) TextAlign.End else TextAlign.Start
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message ${otherDisplayName}...") }
            )
            Spacer(Modifier.width(8.dp))
            Button(
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
                }
            ) { Text("Send") }
        }
    }
}

private fun ensureChatDocExists(db: FirebaseFirestore, myUid: String, otherUid: String) {
    val chatId = if (myUid < otherUid) "${myUid}_${otherUid}" else "${otherUid}_${myUid}"
    val chatRef = db.collection("chats").document(chatId)

    chatRef.get().addOnSuccessListener { doc ->
        if (doc.exists()) return@addOnSuccessListener

        db.collection("users").document(myUid).get().addOnSuccessListener { myDoc ->
            val myName = myDoc.getString("displayName") ?: "Runner"

            db.collection("users").document(otherUid).get().addOnSuccessListener { otherDoc ->
                val otherName = otherDoc.getString("displayName") ?: "Runner"

                val data = hashMapOf(
                    "participants" to listOf(myUid, otherUid),
                    "createdAt" to Timestamp.now(),
                    "lastMessage" to "",
                    "lastTimestamp" to Timestamp.now(),
                    "otherName_$myUid" to otherName,
                    "otherName_$otherUid" to myName
                )
                chatRef.set(data)
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
            "lastTimestamp" to now
        ))
    }
}
