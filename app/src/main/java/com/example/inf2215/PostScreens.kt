package com.example.inf2215

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import java.util.UUID

// Data class for group selection dialog
data class GroupSelection(val id: String, val name: String)

// Upload Image Function
fun uploadImageToStorage(uri: Uri, onSuccess: (String) -> Unit, onError: () -> Unit) {
    val storageRef = FirebaseStorage.getInstance().reference
    val imageRef = storageRef.child("post_images/${UUID.randomUUID()}.jpg")

    imageRef.putFile(uri)
        .addOnSuccessListener {
            imageRef.downloadUrl.addOnSuccessListener { uri ->
                onSuccess(uri.toString())
            }
        }
        .addOnFailureListener { onError() }
}

// SCREEN 1: CREATE TEXT/IMAGE POST
@Composable
fun CreatePostScreen(
    modifier: Modifier = Modifier,
    onPostSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isPosting by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> selectedImageUri = uri }

    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("New Post", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        // Title Input
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title *") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // Body Input
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("What's on your mind?") },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        // Image Preview
        if (selectedImageUri != null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp)
            ) {
                AsyncImage(
                    model = selectedImageUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = { selectedImageUri = null },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Default.Close, "Remove Image", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Buttons Row
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Default.AddPhotoAlternate, "Add Image")
            }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = onCancel) { Text("Cancel") }

            Button(
                enabled = !isPosting && title.isNotBlank() && (text.isNotBlank() || selectedImageUri != null),
                onClick = {
                    isPosting = true
                    val user = auth.currentUser ?: run {
                        isPosting = false
                        return@Button
                    }

                    val savePost = { imageUrl: String? ->
                        db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                            val safeName = doc.getString("displayName") ?: "Runner"

                            val postData: HashMap<String, Any?> = hashMapOf(
                                "userId" to user.uid,
                                "displayName" to safeName,
                                "title" to title,
                                "text" to text,
                                "imageUrl" to imageUrl,
                                "type" to "NORMAL",
                                "visibility" to "PUBLIC",
                                "createdAt" to Timestamp.now()
                            )

                            db.collection("posts").add(postData).addOnSuccessListener {
                                isPosting = false
                                onPostSuccess()
                            }.addOnFailureListener {
                                isPosting = false
                            }
                        }.addOnFailureListener {
                            isPosting = false
                        }
                    }

                    if (selectedImageUri != null) {
                        uploadImageToStorage(
                            selectedImageUri!!,
                            onSuccess = { url -> savePost(url) },
                            onError = { isPosting = false }
                        )
                    } else {
                        savePost(null)
                    }
                }
            ) {
                Text(if (isPosting) "Posting..." else "Post")
            }
        }
    }
}

// SCREEN 2: TRACK RUN SCREEN WITH LIVE LOCATION
@Composable
fun TrackRunScreen(
    modifier: Modifier = Modifier,
    onRunFinished: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser

    // Run Logic States
    var isRunning by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0L) }
    var distanceKm by remember { mutableStateOf(0.0) }

    // Group Selection States
    val myGroups = remember { mutableStateListOf<GroupSelection>() }
    var selectedGroupIds by remember { mutableStateOf(setOf<String>()) }
    var showGroupDialog by remember { mutableStateOf(false) }

    // Fetch Groups Real-time
    DisposableEffect(user) {
        if (user != null) {
            val listener = db.collectionGroup("members")
                .whereEqualTo("userId", user.uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.e("TrackRunScreen", "Error fetching groups", e)
                        return@addSnapshotListener
                    }

                    val currentIds = snapshot?.documents?.mapNotNull {
                        it.reference.parent.parent?.id
                    }?.toSet() ?: emptySet()

                    myGroups.removeAll { it.id !in currentIds }

                    currentIds.forEach { gid ->
                        if (myGroups.none { it.id == gid }) {
                            db.collection("groups").document(gid).get()
                                .addOnSuccessListener { gDoc ->
                                    if (gDoc.exists()) {
                                        val name = gDoc.getString("name") ?: "Group"
                                        if (myGroups.none { it.id == gid }) {
                                            myGroups.add(GroupSelection(gid, name))
                                        }
                                    }
                                }
                        }
                    }
                }
            onDispose { listener.remove() }
        } else {
            onDispose { }
        }
    }

    // Pace State
    val currentPace = remember(distanceKm, seconds) {
        if (distanceKm > 0.05) {
            val minutes = seconds / 60.0
            val paceVal = minutes / distanceKm
            val paceMin = paceVal.toInt()
            val paceSec = ((paceVal - paceMin) * 60).toInt()
            String.format("%d'%02d\" /km", paceMin, paceSec)
        } else {
            "-'--\" /km"
        }
    }

    // Map & Location State
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Default start location (will be overwritten by GPS)
    var currentLocation by remember { mutableStateOf(LatLng(1.3521, 103.8198)) }
    var pathPoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var isLocationLoaded by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLocation, 17f)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasLocationPermission = isGranted }

    // Review States
    var runTitle by remember { mutableStateOf("") }
    var runDescription by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    // Permission Check
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Fetch Initial Location
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            try {
                @SuppressLint("MissingPermission")
                val task = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                task.addOnSuccessListener { location ->
                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        currentLocation = latLng
                        if (pathPoints.isEmpty()) {
                            pathPoints = listOf(latLng)
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 18f)
                        }
                        isLocationLoaded = true
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Timer Logic
    LaunchedEffect(isRunning) {
        if (isRunning) {
            val startTime = System.currentTimeMillis() - (seconds * 1000)
            while (isRunning) {
                seconds = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        }
    }

    // Real-time Location Tracking
    DisposableEffect(isRunning) {
        if (isRunning && hasLocationPermission) {
            val client = LocationServices.getFusedLocationProviderClient(context)

            // Configure Request: High Accuracy, Update every 3 meters or 2 seconds
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateDistanceMeters(3f)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    for (location in result.locations) {
                        val newLatLng = LatLng(location.latitude, location.longitude)

                        // Calculate Distance from last point
                        if (pathPoints.isNotEmpty()) {
                            val lastPoint = pathPoints.last()
                            val results = FloatArray(1)
                            Location.distanceBetween(
                                lastPoint.latitude, lastPoint.longitude,
                                newLatLng.latitude, newLatLng.longitude,
                                results
                            )

                            // Filter out tiny GPS jumps (e.g. less than 1 meter movement)
                            if (results[0] > 1.0) {
                                distanceKm += (results[0] / 1000.0)
                                pathPoints = pathPoints + newLatLng
                                cameraPositionState.position = CameraPosition.fromLatLngZoom(newLatLng, 18f)
                            }
                        } else {
                            // First point
                            pathPoints = listOf(newLatLng)
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(newLatLng, 18f)
                        }
                    }
                }
            }
            try {
                @SuppressLint("MissingPermission")
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            } catch (e: Exception) { e.printStackTrace() }

            onDispose { client.removeLocationUpdates(callback) }
        } else {
            onDispose { }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!isFinished) {
            // VIEW 1: ACTIVE RUN
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLocationLoaded || !hasLocationPermission) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = true)
                    ) {
                        Polyline(points = pathPoints, color = Color.Red, width = 12f)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                // Stats Card
                Card(
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatTime(seconds), style = MaterialTheme.typography.titleLarge)
                            Text("Time", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(String.format("%.2f km", distanceKm), style = MaterialTheme.typography.titleLarge)
                            Text("Distance", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(currentPace, style = MaterialTheme.typography.titleLarge)
                            Text("Pace", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Controls
            Surface(shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!isRunning && seconds == 0L) {
                        // Initial State
                        Button(
                            onClick = { isRunning = true },
                            enabled = isLocationLoaded,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text(if (isLocationLoaded) "START RUN" else "WAITING FOR GPS...") }
                        TextButton(onClick = onCancel) { Text("Cancel") }
                    } else if (isRunning) {
                        // Running State
                        Button(onClick = { isRunning = false }, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("PAUSE") }
                    } else {
                        // Paused State
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedButton(onClick = { isRunning = true }, modifier = Modifier.weight(1f)) { Text("Resume") }
                            Button(onClick = { isFinished = true }, modifier = Modifier.weight(1f)) { Text("Finish") }
                        }
                    }
                }
            }
        } else {
            // VIEW 2: REVIEW SCREEN
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Run Summary", style = MaterialTheme.typography.headlineMedium)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatTime(seconds), style = MaterialTheme.typography.titleMedium)
                            Text("Time", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(String.format("%.2f km", distanceKm), style = MaterialTheme.typography.titleMedium)
                            Text("Distance", style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(currentPace, style = MaterialTheme.typography.titleMedium)
                            Text("Avg Pace", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Show Static Map Preview in Review
                if (pathPoints.isNotEmpty()) {
                    Box(modifier = Modifier.height(200.dp).fillMaxWidth().padding(vertical = 8.dp)) {
                        val cameraState = rememberCameraPositionState()
                        LaunchedEffect(pathPoints) {
                            val boundsBuilder = LatLngBounds.builder()
                            pathPoints.forEach { boundsBuilder.include(it) }
                            val bounds = boundsBuilder.build()
                            cameraState.move(CameraUpdateFactory.newLatLngBounds(bounds, 50))
                        }
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = cameraState,
                            googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
                            uiSettings = MapUiSettings(zoomControlsEnabled = false)
                        ) {
                            Polyline(points = pathPoints, color = Color.Red, width = 10f)
                        }
                    }
                }

                // Title Input
                OutlinedTextField(
                    value = runTitle,
                    onValueChange = { runTitle = it },
                    label = { Text("Title *") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Description Input
                OutlinedTextField(
                    value = runDescription,
                    onValueChange = { runDescription = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                if (myGroups.isNotEmpty()) {
                    OutlinedCard(
                        onClick = { showGroupDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (selectedGroupIds.isEmpty()) "Share to Groups (Optional)"
                                else "Sharing to ${selectedGroupIds.size} group(s)"
                            )
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (isSaving) {
                    CircularProgressIndicator()
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Discard") }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = runTitle.isNotBlank(),
                            onClick = {
                                isSaving = true
                                saveRunToFirestore(
                                    seconds = seconds,
                                    distanceKm = distanceKm,
                                    pace = currentPace,
                                    title = runTitle,
                                    description = runDescription,
                                    pathPoints = pathPoints,
                                    shareToGroupIds = selectedGroupIds.toList(),
                                    onSuccess = onRunFinished
                                )
                            }
                        ) { Text("Save") }
                    }
                }
            }
        }
    }

    if (showGroupDialog) {
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text("Select Groups") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(myGroups) { group ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedGroupIds.contains(group.id)) {
                                        selectedGroupIds = selectedGroupIds - group.id
                                    } else {
                                        selectedGroupIds = selectedGroupIds + group.id
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                if (selectedGroupIds.contains(group.id)) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(group.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGroupDialog = false }) { Text("Done") }
            }
        )
    }
}

// Save Function with Group Logic
fun saveRunToFirestore(
    seconds: Long,
    distanceKm: Double,
    pace: String,
    title: String,
    description: String,
    pathPoints: List<LatLng>,
    shareToGroupIds: List<String> = emptyList(),
    onSuccess: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser ?: return

    val durationMin = seconds / 60.0
    val routeData = pathPoints.map { hashMapOf("lat" to it.latitude, "lng" to it.longitude) }

    // Save raw run data
    val runData = hashMapOf(
        "userId" to user.uid,
        "distanceKm" to distanceKm,
        "durationMin" to durationMin,
        "route" to routeData,
        "createdAt" to Timestamp.now()
    )

    db.collection("runs").add(runData).addOnSuccessListener { runRef ->
        db.collection("users").document(user.uid).get().addOnSuccessListener { userDoc ->
            val displayName = userDoc.getString("displayName") ?: "Runner"
            val now = Timestamp.now()

            // Create the Post
            val postData = hashMapOf(
                "userId" to user.uid,
                "displayName" to displayName,
                "title" to title,
                "text" to description.ifBlank { "Completed a run!" },
                "type" to "RUN",
                "visibility" to "PUBLIC",
                "runId" to runRef.id,
                "runDistance" to String.format("%.2f km", distanceKm),
                "runDuration" to formatTime(seconds),
                "runPace" to pace,
                "route" to routeData,
                "createdAt" to now
            )

            db.collection("posts").add(postData).addOnSuccessListener {
                if (shareToGroupIds.isNotEmpty()) {
                    val batch = db.batch()
                    shareToGroupIds.forEach { gid ->
                        val threadRef = db.collection("groups").document(gid).collection("threads").document()
                        val threadData = hashMapOf(
                            "title" to title,
                            "body" to description.ifBlank { "Shared a run" },
                            "createdById" to user.uid,
                            "createdByName" to displayName,
                            "createdAt" to now,
                            "lastActivityAt" to now,
                            "commentsCount" to 0,
                            "type" to "RUN",
                            "runId" to runRef.id,
                            "runDistance" to String.format("%.2f km", distanceKm),
                            "runDuration" to formatTime(seconds),
                            "runPace" to pace,
                            "route" to routeData
                        )
                        batch.set(threadRef, threadData)
                    }
                    batch.commit().addOnSuccessListener { onSuccess() }
                } else {
                    onSuccess()
                }
            }
        }
    }
}

fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}