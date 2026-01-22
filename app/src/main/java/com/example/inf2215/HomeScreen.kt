package com.example.inf2215

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

private const val TAG = "HomeScreen"

data class FeedPost(
    val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit,
    onGoProfile: () -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var posts by remember { mutableStateOf(listOf<FeedPost>()) }
    var status by remember { mutableStateOf("") }
    var isSeeding by remember { mutableStateOf(false) }

    // Live feed listener
    LaunchedEffect(Unit) {
        db.collection("posts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    status = "Feed error: ${e.message}"
                    Log.e(TAG, "Feed listener error", e)
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.map { doc ->
                    FeedPost(
                        id = doc.id,
                        userId = doc.getString("userId") ?: "",
                        displayName = doc.getString("displayName") ?: "Unknown",
                        text = doc.getString("text") ?: "",
                        createdAt = doc.getTimestamp("createdAt")?.let { Timestamp(it.seconds, it.nanoseconds) }
                    )
                } ?: emptyList()

                posts = list
            }
    }

    fun seedDemoPosts(count: Int = 5) {
        val user = auth.currentUser
        if (user == null) {
            status = "Not logged in"
            return
        }

        isSeeding = true
        status = "Seeding demo posts..."

        // Use current user's displayName from Firestore if available
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                val myName = userDoc.getString("displayName") ?: "Demo User"

                val demoTexts = listOf(
                    "First run of the week",
                    "Tried a new route today!",
                    "Morning jog",
                    "5K done — feeling good.",
                    "Any runners around my area?"
                )

                val batch = db.batch()
                for (i in 0 until count) {
                    val postRef = db.collection("posts").document()
                    val postData = hashMapOf(
                        "userId" to user.uid,
                        "displayName" to myName,
                        "text" to demoTexts[i % demoTexts.size],
                        "createdAt" to Timestamp.now()
                    )
                    batch.set(postRef, postData)
                }

                batch.commit()
                    .addOnSuccessListener {
                        status = "Seeded $count demo posts!"
                        isSeeding = false
                    }
                    .addOnFailureListener { e ->
                        status = "Seed failed: ${e.message}"
                        isSeeding = false
                        Log.e(TAG, "Seed failed", e)
                    }
            }
            .addOnFailureListener { e ->
                status = "Could not load profile: ${e.message}"
                isSeeding = false
            }
    }

    fun clearMyPosts() {
        val user = auth.currentUser ?: run {
            status = "⚠Not logged in"
            return
        }

        status = "Deleting your posts..."
        db.collection("posts")
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { snap ->
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit()
                    .addOnSuccessListener { status = "Deleted your posts" }
                    .addOnFailureListener { e -> status = "Delete failed: ${e.message}" }
            }
            .addOnFailureListener { e ->
                status = "Query failed: ${e.message}"
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onGoProfile, modifier = Modifier.weight(1f)) { Text("Profile") }

            Button(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    onLogout()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Logout") }
        }

        Text("Feed", style = MaterialTheme.typography.titleLarge)
        if (status.isNotBlank()) Text(status)

        // Temporary test controls (so you can test without teammate's posting UI)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { seedDemoPosts(5) },
                enabled = !isSeeding,
                modifier = Modifier.weight(1f)
            ) { Text(if (isSeeding) "Seeding..." else "Seed demo posts") }

            OutlinedButton(
                onClick = { clearMyPosts() },
                modifier = Modifier.weight(1f)
            ) { Text("Clear my posts") }
        }

        Divider()

        if (posts.isEmpty()) {
            Text("No posts yet. Tap 'Seed demo posts' to test the feed.")
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(posts) { post ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(post.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(post.text)
                        }
                    }
                }
            }
        }
    }
}
