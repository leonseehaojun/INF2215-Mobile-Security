package com.example.inf2215

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdminAnnouncementsScreen(
    onNavigateToCreate: () -> Unit,
    onAnnouncementClick: (String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var announcements by remember { mutableStateOf(listOf<Announcement>()) }
    var selectedTypeFilter by remember { mutableStateOf<String?>(null) }
    var expandedFilter by remember { mutableStateOf(false) }

    val typeOptions = listOf("News", "System Notice", "Service Alert")

    LaunchedEffect(selectedTypeFilter) {
        var query: Query = db.collection("announcements").orderBy("createdAt", Query.Direction.DESCENDING)
        if (selectedTypeFilter != null) {
            query = query.whereEqualTo("type", selectedTypeFilter)
        }

        query.addSnapshotListener { snapshot, _ ->
            announcements = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Announcement::class.java)?.copy(id = doc.id)
            } ?: emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filter Dropdown
            Box {
                OutlinedCard(
                    onClick = { expandedFilter = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedTypeFilter != null) {
                            val (icon, color) = getCategoryStyle(selectedTypeFilter!!)
                            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(selectedTypeFilter ?: "All Types", fontSize = 14.sp)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(expanded = expandedFilter, onDismissRequest = { expandedFilter = false }) {
                    DropdownMenuItem(
                        text = { Text("All Types") },
                        onClick = { selectedTypeFilter = null; expandedFilter = false }
                    )
                    typeOptions.forEach { type ->
                        DropdownMenuItem(
                            text = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val (icon, color) = getCategoryStyle(type)
                                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(type)
                                }
                            },
                            onClick = { selectedTypeFilter = type; expandedFilter = false }
                        )
                    }
                }
            }

            Button(onClick = onNavigateToCreate) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Create")
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(announcements) { announcement ->
                AnnouncementCard(
                    announcement = announcement,
                    onClick = { onAnnouncementClick(announcement.id) }
                )
            }
        }
    }
}

@Composable
fun AnnouncementCard(
    announcement: Announcement,
    onClick: () -> Unit
) {
    val (icon, color) = getCategoryStyle(announcement.type)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = announcement.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            val dateStr = announcement.createdAt?.toDate()?.let {
                SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(it)
            } ?: ""
            Text(
                dateStr,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAnnouncementScreen(
    onBack: () -> Unit,
    announcementId: String? = null
) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("News") }
    var description by remember { mutableStateOf("") }
    var isIndefinite by remember { mutableStateOf(false) }

    var startDate by remember { mutableStateOf<Calendar?>(null) }
    var endDate by remember { mutableStateOf<Calendar?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }

    val typeOptions = listOf("News", "System Notice", "Service Alert")
    var typeExpanded by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(announcementId != null) }

    LaunchedEffect(announcementId) {
        if (announcementId != null) {
            db.collection("announcements").document(announcementId).get().addOnSuccessListener { doc ->
                val ann = doc.toObject(Announcement::class.java)
                if (ann != null) {
                    title = ann.title
                    type = ann.type
                    description = ann.description
                    isIndefinite = ann.isIndefinite
                    ann.startDate?.let {
                        val cal = Calendar.getInstance()
                        cal.time = it.toDate()
                        startDate = cal
                    }
                    ann.endDate?.let {
                        val cal = Calendar.getInstance()
                        cal.time = it.toDate()
                        endDate = cal
                    }
                }
                isLoading = false
            }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (announcementId == null) "Create Announcement" else "Edit Announcement",
                style = MaterialTheme.typography.headlineMedium
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = !typeExpanded }
            ) {
                OutlinedTextField(
                    value = type,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    leadingIcon = {
                        val (icon, color) = getCategoryStyle(type)
                        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    }
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    typeOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val (icon, color) = getCategoryStyle(option)
                                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(option)
                                }
                            },
                            onClick = { type = option; typeExpanded = false }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isIndefinite, onCheckedChange = { isIndefinite = it })
                Text("Indefinite Announcement")
            }

            if (!isIndefinite) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(startDate?.let { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it.time) } ?: "Select")
                        }
                    }
                    
                    Text("-", modifier = Modifier.padding(top = 16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text("End", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(endDate?.let { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it.time) } ?: "Select")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5
            )

            Button(
                onClick = {
                    val user = auth.currentUser ?: return@Button
                    val announcementData = hashMapOf(
                        "title" to title,
                        "type" to type,
                        "description" to description,
                        "isIndefinite" to isIndefinite,
                        "startDate" to if (isIndefinite) null else startDate?.let { Timestamp(it.time) },
                        "endDate" to if (isIndefinite) null else endDate?.let { Timestamp(it.time) },
                        "updatedAt" to Timestamp.now(),
                        "createdBy" to user.uid
                    )
                    
                    if (announcementId == null) {
                        announcementData["createdAt"] = Timestamp.now()
                        db.collection("announcements").add(announcementData).addOnSuccessListener { onBack() }
                    } else {
                        db.collection("announcements").document(announcementId)
                            .update(announcementData as Map<String, Any>)
                            .addOnSuccessListener { onBack() }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank() && description.isNotBlank() && (isIndefinite || (startDate != null && endDate != null))
            ) {
                Text(if (announcementId == null) "Publish Announcement" else "Update Announcement")
            }
        }
    }

    if (showDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = startDate?.timeInMillis,
            initialSelectedEndDateMillis = endDate?.timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateRangePickerState.selectedStartDateMillis?.let { start ->
                        val calStart = Calendar.getInstance()
                        calStart.timeInMillis = start
                        startDate = calStart
                    }
                    dateRangePickerState.selectedEndDateMillis?.let { end ->
                        val calEnd = Calendar.getInstance()
                        calEnd.timeInMillis = end
                        endDate = calEnd
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
