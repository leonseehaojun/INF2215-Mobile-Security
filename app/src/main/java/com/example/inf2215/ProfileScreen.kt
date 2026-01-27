package com.example.inf2215

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "ProfileScreen"
private enum class ProfileTab { Profile, Friends }

data class SimpleUser(
    val uid: String = "",
    val displayName: String = "User"
)

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onStartChat: (otherUid: String, otherName: String) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val uid = auth.currentUser?.uid

    var tab by remember { mutableStateOf(ProfileTab.Profile) }

    var displayName by remember { mutableStateOf("Loading...") }
    var email by remember { mutableStateOf("") }
    var age by remember { mutableStateOf<Int?>(null) }
    var heightCm by remember { mutableStateOf<Int?>(null) }
    var weightKg by remember { mutableStateOf<Double?>(null) }

    var recentRuns by remember { mutableStateOf(listOf<ProfileRunItem>()) }
    var status by remember { mutableStateOf("") }

    var friends by remember { mutableStateOf(listOf<SimpleUser>()) }
    var suggested by remember { mutableStateOf(listOf<SimpleUser>()) }
    var incomingRequests by remember { mutableStateOf(listOf<SimpleUser>()) }

    var pendingOutIds by remember { mutableStateOf(setOf<String>()) }

    var friendStatus by remember { mutableStateOf("") }

    LaunchedEffect(uid) {
        if (uid == null) {
            status = "Not logged in"
            return@LaunchedEffect
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                displayName = doc.getString("displayName") ?: ""
                email = doc.getString("email") ?: ""
                age = doc.getLong("age")?.toInt()
                heightCm = doc.getLong("heightCm")?.toInt()
                weightKg = doc.getDouble("weightKg")
            }
            .addOnFailureListener { e -> Log.e(TAG, "Profile load failed", e) }

        db.collection("posts")
            .whereEqualTo("userId", uid)
            .whereEqualTo("type", "RUN")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    status = "Error loading runs: ${e.message}"
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.map { doc ->
                    val rawRoute = doc.get("route") as? List<Map<String, Double>>
                    val parsedRoute = rawRoute?.mapNotNull {
                        if (it.containsKey("lat") && it.containsKey("lng")) {
                            LatLng(it["lat"]!!, it["lng"]!!)
                        } else null
                    } ?: emptyList()

                    ProfileRunItem(
                        id = doc.id,
                        title = doc.getString("title") ?: "Untitled Run",
                        distanceStr = doc.getString("runDistance") ?: "0 km",
                        durationStr = doc.getString("runDuration") ?: "00:00",
                        route = parsedRoute,
                        timestamp = doc.getTimestamp("createdAt")
                    )
                } ?: emptyList()

                recentRuns = list
            }

        db.collection("users").document(uid)
            .collection("friends")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    friendStatus = "Error loading friends: ${e.message}"
                    return@addSnapshotListener
                }
                friends = snap?.documents?.map { d ->
                    SimpleUser(
                        uid = d.id,
                        displayName = d.getString("displayName") ?: "User"
                    )
                } ?: emptyList()
            }

        db.collection("users").document(uid)
            .collection("friend_requests_in")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    friendStatus = "Error loading requests: ${e.message}"
                    return@addSnapshotListener
                }
                incomingRequests = snap?.documents?.map { d ->
                    SimpleUser(
                        uid = d.id,
                        displayName = d.getString("displayName") ?: "User"
                    )
                } ?: emptyList()
            }

        db.collection("users").document(uid)
            .collection("friend_requests_out")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    friendStatus = "Error loading outgoing: ${e.message}"
                    return@addSnapshotListener
                }
                pendingOutIds = snap?.documents?.map { it.id }?.toSet() ?: emptySet()
            }

        db.collection("users")
            .limit(50)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    friendStatus = "Error loading suggested: ${e.message}"
                    return@addSnapshotListener
                }

                val all = snap?.documents?.mapNotNull { d ->
                    val otherId = d.id
                    if (otherId == uid) return@mapNotNull null
                    SimpleUser(
                        uid = otherId,
                        displayName = d.getString("displayName") ?: "User"
                    )
                } ?: emptyList()

                val friendIds = friends.map { it.uid }.toSet()
                val inIds = incomingRequests.map { it.uid }.toSet()
                val outIds = pendingOutIds

                suggested = all.filter { u ->
                    u.uid !in friendIds && u.uid !in inIds && u.uid !in outIds
                }.take(10)
            }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(100.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile Picture",
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(text = displayName, style = MaterialTheme.typography.headlineSmall)
            Text(text = email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)

            Spacer(Modifier.height(12.dp))

            TabRow(selectedTabIndex = if (tab == ProfileTab.Profile) 0 else 1) {
                Tab(
                    selected = tab == ProfileTab.Profile,
                    onClick = { tab = ProfileTab.Profile },
                    text = { Text("Profile") }
                )
                Tab(
                    selected = tab == ProfileTab.Friends,
                    onClick = { tab = ProfileTab.Friends },
                    text = { Text("Friends") }
                )
            }
        }

        when (tab) {
            ProfileTab.Profile -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatColumn("Age", age?.toString() ?: "-")
                            StatDivider()
                            StatColumn("Height", if (heightCm != null) "${heightCm}cm" else "-")
                            StatDivider()
                            StatColumn("Weight", if (weightKg != null) "${weightKg}kg" else "-")
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Recent Activity", style = MaterialTheme.typography.titleLarge)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    if (status.isNotBlank()) {
                        Text(status, color = MaterialTheme.colorScheme.error)
                    }

                    if (recentRuns.isEmpty()) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text("No runs recorded yet.", color = MaterialTheme.colorScheme.secondary)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            items(recentRuns) { run ->
                                RunHistoryCard(run)
                            }
                        }
                    }
                }
            }

            ProfileTab.Friends -> {
                FriendsTabContent(
                    uid = uid,
                    friends = friends,
                    incomingRequests = incomingRequests,
                    suggested = suggested,
                    pendingOutIds = pendingOutIds,
                    statusText = friendStatus,
                    onSendRequestOptimistic = { targetUid ->
                        pendingOutIds = pendingOutIds + targetUid
                    },
                    onStartChat = { otherUid, otherName ->
                        onStartChat(otherUid, otherName)
                    }
                )
            }
        }
    }
}

@Composable
private fun FriendsTabContent(
    uid: String?,
    friends: List<SimpleUser>,
    incomingRequests: List<SimpleUser>,
    suggested: List<SimpleUser>,
    pendingOutIds: Set<String>,
    statusText: String,
    onSendRequestOptimistic: (String) -> Unit,
    onStartChat: (otherUid: String, otherName: String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        if (statusText.isNotBlank()) {
            Text(statusText, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (incomingRequests.isNotEmpty()) {
            Text("Friend Requests", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            incomingRequests.forEach { req ->
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(req.displayName, fontWeight = FontWeight.Bold)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (uid == null) return@OutlinedButton
                                    acceptFriendRequest(db, uid, req.uid)
                                }
                            ) { Text("Accept") }

                            TextButton(
                                onClick = {
                                    if (uid == null) return@TextButton
                                    declineFriendRequest(db, uid, req.uid)
                                }
                            ) { Text("Decline") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))
        }

        Text("My Friends (${friends.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (friends.isEmpty()) {
            Text("No friends yet.", color = MaterialTheme.colorScheme.secondary)
        } else {
            friends.forEach { f ->
                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(36.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.padding(6.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(f.displayName, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                if (uid == null) return@Button
                                createOrOpenChat(
                                    db = db,
                                    myUid = uid,
                                    otherUid = f.uid
                                ) {
                                    onStartChat(f.uid, f.displayName)
                                }
                            }
                        ) { Text("Chat") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(18.dp))

        Text("Suggested Friends", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (suggested.isEmpty()) {
            Text("No suggestions right now.", color = MaterialTheme.colorScheme.secondary)
        } else {
            suggested.forEach { s ->
                val isSent = s.uid in pendingOutIds

                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.displayName, fontWeight = FontWeight.Medium)

                        Button(
                            enabled = !isSent,
                            onClick = {
                                if (uid == null) return@Button
                                onSendRequestOptimistic(s.uid)

                                sendFriendRequest(
                                    db = db,
                                    fromUid = uid,
                                    toUid = s.uid,
                                    onError = { Log.e(TAG, "Failed to send friend request") }
                                )
                            }
                        ) {
                            Text(if (isSent) "Sent" else "Add")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun sendFriendRequest(
    db: FirebaseFirestore,
    fromUid: String,
    toUid: String,
    onError: () -> Unit = {}
) {
    db.collection("users").document(fromUid).get()
        .addOnSuccessListener { meDoc ->
            val myName = meDoc.getString("displayName") ?: "Runner"
            val now = Timestamp.now()

            val outData = hashMapOf("displayName" to myName, "requestedAt" to now)
            val inData = hashMapOf("displayName" to myName, "requestedAt" to now)

            db.collection("users").document(fromUid)
                .collection("friend_requests_out").document(toUid)
                .set(outData)
                .addOnFailureListener { onError() }

            db.collection("users").document(toUid)
                .collection("friend_requests_in").document(fromUid)
                .set(inData)
                .addOnFailureListener { onError() }
        }
        .addOnFailureListener { onError() }
}

private fun acceptFriendRequest(db: FirebaseFirestore, myUid: String, fromUid: String) {
    val now = Timestamp.now()

    db.collection("users").document(myUid).get().addOnSuccessListener { meDoc ->
        val myName = meDoc.getString("displayName") ?: "Runner"

        db.collection("users").document(fromUid).get().addOnSuccessListener { otherDoc ->
            val otherName = otherDoc.getString("displayName") ?: "Runner"

            val myFriendData = hashMapOf("displayName" to otherName, "addedAt" to now)
            val otherFriendData = hashMapOf("displayName" to myName, "addedAt" to now)

            val myRef = db.collection("users").document(myUid)
            val otherRef = db.collection("users").document(fromUid)

            db.runBatch { batch ->
                batch.set(myRef.collection("friends").document(fromUid), myFriendData)
                batch.set(otherRef.collection("friends").document(myUid), otherFriendData)

                batch.delete(myRef.collection("friend_requests_in").document(fromUid))
                batch.delete(otherRef.collection("friend_requests_out").document(myUid))
            }
        }
    }
}

private fun declineFriendRequest(db: FirebaseFirestore, myUid: String, fromUid: String) {
    val myRef = db.collection("users").document(myUid)
    val otherRef = db.collection("users").document(fromUid)

    db.runBatch { batch ->
        batch.delete(myRef.collection("friend_requests_in").document(fromUid))
        batch.delete(otherRef.collection("friend_requests_out").document(myUid))
    }
}

private fun chatIdFor(a: String, b: String): String =
    if (a < b) "${a}_${b}" else "${b}_${a}"

private fun createOrOpenChat(
    db: FirebaseFirestore,
    myUid: String,
    otherUid: String,
    onDone: () -> Unit
) {
    val chatId = chatIdFor(myUid, otherUid)
    val chatRef = db.collection("chats").document(chatId)

    chatRef.get().addOnSuccessListener { doc ->
        if (doc.exists()) {
            onDone()
        } else {
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

                    chatRef.set(data).addOnSuccessListener { onDone() }
                }
            }
        }
    }
}

/** Existing UI helpers **/
@Composable
fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun StatDivider() {
    Box(
        modifier = Modifier.width(1.dp).height(24.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
fun RunHistoryCard(run: ProfileRunItem) {
    val dateStr = remember(run.timestamp) {
        run.timestamp?.toDate()?.let {
            SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(it)
        } ?: "Unknown Date"
    }

    Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(text = dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = run.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (run.route.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = rememberCameraPositionState {
                            position = CameraPosition.fromLatLngZoom(run.route.last(), 14f)
                        },
                        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
                        uiSettings = MapUiSettings(zoomControlsEnabled = false)
                    ) {
                        Polyline(points = run.route, color = Color.Red, width = 8f)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = run.distanceStr, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Distance", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = run.durationStr, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Duration", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
