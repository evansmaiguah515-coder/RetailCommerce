package com.example.retailcommerce

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun FirestoreConnectionTestScreen() {
    var message by remember {
        mutableStateOf("Tap the button to test Firestore.")
    }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Firestore Connection Test",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = message,
            modifier = Modifier.padding(vertical = 20.dp)
        )

        Button(
            enabled = !loading,
            onClick = {
                val user = FirebaseAuth.getInstance().currentUser

                if (user == null) {
                    message = "Please sign in first, then run the test."
                    return@Button
                }

                loading = true
                message = "Connecting to Firestore..."

                FirebaseFirestore.getInstance()
                    .collection("orders")
                    .whereEqualTo("userId", user.uid)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { result ->
                        loading = false
                        message =
                            "Connection successful! " +
                                    "Your query returned ${result.size()} order(s)."
                    }
                    .addOnFailureListener { error ->
                        loading = false
                        message =
                            "Connection test failed: " +
                                    (error.localizedMessage ?: "Unknown error")
                    }
            }
        ) {
            Text("Test Firestore")
        }

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
