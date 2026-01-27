package com.example.inf2215

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
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
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import java.util.UUID

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

    // Public vs Friend tab
    val visibilityOptions = listOf("PUBLIC", "FRIENDS")
    var visibility by remember { mutableStateOf("PUBLIC") }

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

        Spacer(Modifier.height(8.dp))

        // NEW: Visibility Segmented Buttons
        Text("Who can see this?", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            visibilityOptions.forEachIndexed { index, opt ->
                SegmentedButton(
                    selected = (visibility == opt),
                    onClick = { visibility = opt },
                    shape = SegmentedButtonDefaults.itemShape(index, visibilityOptions.size)
                ) {
                    Text(opt)
                }
            }
        }

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
                                "visibility" to visibility,
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

    // Run Logic States
    var isRunning by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0L) }
    var distanceKm by remember { mutableStateOf(0.0) }

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

    // Ask for Location Permission on Start
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

    // Real-time Location Tracking Logic
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
                            // results[0] is distance in meters
                            val distanceInMeters = results[0]

                            // Filter out tiny GPS jumps (e.g. less than 1 meter movement)
                            if (distanceInMeters > 1.0) {
                                distanceKm += (distanceInMeters / 1000.0)
                                pathPoints = pathPoints + newLatLng
                                currentLocation = newLatLng
                                cameraPositionState.position = CameraPosition.fromLatLngZoom(newLatLng, 18f)
                            }
                        } else {
                            // First point
                            pathPoints = listOf(newLatLng)
                            currentLocation = newLatLng
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(newLatLng, 18f)
                        }
                    }
                }
            }

            try {
                @SuppressLint("MissingPermission")
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            } catch (e: Exception) {
                e.printStackTrace()
            }

            onDispose {
                client.removeLocationUpdates(callback)
            }
        } else {
            onDispose { }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!isFinished) {
            // VIEW 1: ACTIVE RUNNER
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
                        Spacer(Modifier.height(8.dp))
                        Text("Acquiring GPS...", modifier = Modifier.padding(top = 40.dp))
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
                    }
                }
            }

            // Controls Surface
            Surface(shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!hasLocationPermission) {
                        Text("Enable location to track run", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("Grant Permission") }
                    } else if (!isRunning && seconds == 0L) {
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
            // VIEW 2: REVIEW & SAVE
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Run Summary", style = MaterialTheme.typography.headlineMedium)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Text(formatTime(seconds), style = MaterialTheme.typography.titleLarge)
                        Text(String.format("%.2f km", distanceKm), style = MaterialTheme.typography.titleLarge)
                    }
                }

                // Show Static Map Preview in Review
                if (pathPoints.isNotEmpty()) {
                    Box(modifier = Modifier.height(200.dp).fillMaxWidth().padding(vertical = 8.dp)) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = rememberCameraPositionState {
                                position = CameraPosition.fromLatLngZoom(pathPoints.last(), 15f)
                            },
                            googleMapOptionsFactory = {
                                GoogleMapOptions().liteMode(true)
                            },
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
                Spacer(Modifier.weight(1f))
                if (isSaving) {
                    CircularProgressIndicator()
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Discard run and go home
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Discard") }

                        // Save Button
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = runTitle.isNotBlank(),    // Force user to add a title
                            onClick = {
                                isSaving = true
                                saveRunToFirestore(seconds, distanceKm, runTitle, runDescription, pathPoints, onSuccess = onRunFinished)
                            }
                        ) { Text("Save") }
                    }
                }
            }
        }
    }
}

// Helper Function to save to Firestore
fun saveRunToFirestore(
    seconds: Long,
    distanceKm: Double,
    title: String,
    description: String,
    pathPoints: List<LatLng>,
    onSuccess: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser ?: return

    val durationMin = seconds / 60.0

    // Convert List<LatLng> to List<Map> for Firestore
    val routeData = pathPoints.map {
        hashMapOf("lat" to it.latitude, "lng" to it.longitude)
    }

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
                "route" to routeData,
                "createdAt" to Timestamp.now()
            )

            db.collection("posts").add(postData).addOnSuccessListener { onSuccess() }
        }
    }
}

fun formatTime(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}