package com.example.inf2215

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

private enum class CommunityTab { MyGroups, FindGroups }

data class GroupCardModel(
    val id: String,
    val name: String,
    val area: String,
    val days: List<String>,
    val time: String,
    val membersCount: Int,
    val memberIds: List<String> = emptyList(),
    val unreadCount: Int = 0
)

@Composable
fun CommunityScreen(
    onCreateGroup: () -> Unit,
    onOpenGroup: (String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val myUid = auth.currentUser?.uid

    var tab by remember { mutableStateOf(CommunityTab.MyGroups) }
    
    var groups by remember { mutableStateOf(listOf<GroupCardModel>()) }
    var status by remember { mutableStateOf("") }
    val isLoading = remember { mutableStateOf(true) }

    LaunchedEffect(myUid) {
        db.collection("groups")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                isLoading.value = false
                if (e != null) {
                    status = "Error loading groups: ${e.message}"
                    return@addSnapshotListener
                }

                groups = snap?.documents?.map { doc ->
                    val unread = if (myUid != null) doc.getLong("unreadCount_$myUid")?.toInt() ?: 0 else 0
                    GroupCardModel(
                        id = doc.id,
                        name = doc.getString("name") ?: "Unnamed Group",
                        area = doc.getString("area") ?: "",
                        days = doc.get("days") as? List<String> ?: emptyList(),
                        time = doc.getString("time") ?: "",
                        membersCount = (doc.getLong("membersCount") ?: 1L).toInt(),
                        memberIds = doc.get("memberIds") as? List<String> ?: emptyList(),
                        unreadCount = unread
                    )
                } ?: emptyList()
            }
    }

    val displayGroups = remember(tab, groups, myUid) {
        when (tab) {
            CommunityTab.MyGroups -> groups.filter { myUid in it.memberIds }
            CommunityTab.FindGroups -> groups.filter { myUid !in it.memberIds }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                TabRow(
                    selectedTabIndex = if (tab == CommunityTab.MyGroups) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[if (tab == CommunityTab.MyGroups) 0 else 1])
                        )
                    }
                ) {
                    Tab(
                        selected = tab == CommunityTab.MyGroups,
                        onClick = { tab = CommunityTab.MyGroups },
                        text = { 
                            Text(
                                "My Groups", 
                                fontWeight = if (tab == CommunityTab.MyGroups) FontWeight.Bold else FontWeight.Normal
                            ) 
                        }
                    )
                    Tab(
                        selected = tab == CommunityTab.FindGroups,
                        onClick = { tab = CommunityTab.FindGroups },
                        text = { 
                            Text(
                                "Find Groups", 
                                fontWeight = if (tab == CommunityTab.FindGroups) FontWeight.Bold else FontWeight.Normal
                            ) 
                        }
                    )
                }
            }

            if (status.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = status, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp))
                }
            }

            if (isLoading.value) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (displayGroups.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    
                                    val emptyText = if (tab == CommunityTab.MyGroups) {
                                        "You haven't joined any groups yet. Find groups of your interest under 'Find Groups'!"
                                    } else {
                                        "No new groups found. Check back later!"
                                    }
                                    
                                    Text(
                                        text = emptyText,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp
                                    )
                                    
                                    if (tab == CommunityTab.MyGroups) {
                                        Button(
                                            onClick = { tab = CommunityTab.FindGroups },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Go to Find Groups")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(displayGroups) { g ->
                        GroupCard(group = g, onClick = { onOpenGroup(g.id) })
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateGroup,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            elevation = FloatingActionButtonDefaults.elevation(8.dp)
        ) {
            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Create Group")
                Text("Create Group", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun GroupCard(group: GroupCardModel, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = group.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (group.area.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Text(text = group.area, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (group.days.isNotEmpty() || group.time.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Column {
                                if (group.days.isNotEmpty()) {
                                    Text(text = group.days.joinToString(", "), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                }
                                if (group.time.isNotBlank()) {
                                    Text(text = group.time, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(imageVector = Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(text = "${group.membersCount} ${if (group.membersCount == 1) "member" else "members"}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (group.unreadCount > 0) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp),
                shape = CircleShape,
                color = Color(0xFF0D47A1), // Unified Dark blue
                tonalElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = group.unreadCount.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
