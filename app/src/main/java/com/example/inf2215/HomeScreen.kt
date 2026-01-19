package com.example.inf2215

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onLogout: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Welcome!", style = MaterialTheme.typography.titleLarge)
        Text("Landing page (Home).")

        Button(onClick = { /* TODO: Start run */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Start a run")
        }
        Button(onClick = { /* TODO: View profile */ }, modifier = Modifier.fillMaxWidth()) {
            Text("View profile")
        }
        Button(onClick = { /* TODO: Message admin */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Message admin")
        }

        Button(
            onClick = {
                FirebaseAuth.getInstance().signOut()
                onLogout() //go back to login screen
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout")
        }
    }
}
