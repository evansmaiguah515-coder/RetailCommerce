
package com.example.retailcommerce

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

private val AuthDark = Color(0xFF111310)
private val AuthPanel = Color(0xFF1C1F1A)
private val AuthLime = Color(0xFFC5F467)
private val AuthMuted = Color(0xFF9DA397)
private val AuthWhite = Color(0xFFF5F6F0)

@Composable
fun RetailCommerceApp() {
    val auth = remember { FirebaseAuth.getInstance() }

    var isSignedIn by remember {
        mutableStateOf(auth.currentUser != null)
    }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            isSignedIn = firebaseAuth.currentUser != null
        }

        auth.addAuthStateListener(listener)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    if (isSignedIn) {
        RetailStore()
    } else {
        AuthScreen()
    }
}

@Composable
fun AuthScreen() {
    val auth = remember { FirebaseAuth.getInstance() }

    var isRegistering by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AuthDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AuthDark)
                .padding(horizontal = 26.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AuthLime
            ) {
                Text(
                    text = "RC",
                    modifier = Modifier.padding(
                        horizontal = 15.dp,
                        vertical = 11.dp
                    ),
                    color = AuthDark,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = if (isRegistering) {
                    "Create your\naccount."
                } else {
                    "Welcome\nback."
                },
                color = AuthWhite,
                fontSize = 36.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = if (isRegistering) {
                    "Join Retail Commerce and discover everyday essentials."
                } else {
                    "Sign in to continue shopping."
                },
                color = AuthMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = null
                    infoMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                singleLine = true,
                label = { Text("Email address") },
                placeholder = { Text("you@example.com") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email
                ),
                shape = RoundedCornerShape(14.dp),
                colors = authFieldColors()
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = null
                    infoMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
                singleLine = true,
                label = { Text("Password") },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(
                        onClick = {
                            passwordVisible = !passwordVisible
                        },
                        enabled = !loading
                    ) {
                        Text(
                            text = if (passwordVisible) "Hide" else "Show",
                            color = AuthLime
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                shape = RoundedCornerShape(14.dp),
                colors = authFieldColors()
            )

            if (isRegistering) {
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !loading,
                    singleLine = true,
                    label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = authFieldColors()
                )
            }

            errorMessage?.let { message ->
                Spacer(Modifier.height(12.dp))

                Text(
                    text = message,
                    color = Color(0xFFFF7777),
                    fontSize = 13.sp
                )
            }

            infoMessage?.let { message ->
                Spacer(Modifier.height(12.dp))

                Text(
                    text = message,
                    color = AuthLime,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(22.dp))

            Button(
                onClick = {
                    val cleanEmail = email.trim()

                    when {
                        cleanEmail.isBlank() || password.isBlank() -> {
                            errorMessage =
                                "Enter your email and password."
                        }

                        !android.util.Patterns.EMAIL_ADDRESS
                            .matcher(cleanEmail).matches() -> {
                            errorMessage =
                                "Enter a valid email address."
                        }

                        password.length < 6 -> {
                            errorMessage =
                                "Your password must contain at least 6 characters."
                        }

                        isRegistering && password != confirmPassword -> {
                            errorMessage = "The passwords do not match."
                        }

                        else -> {
                            loading = true
                            errorMessage = null
                            infoMessage = null

                            val task = if (isRegistering) {
                                auth.createUserWithEmailAndPassword(
                                    cleanEmail,
                                    password
                                )
                            } else {
                                auth.signInWithEmailAndPassword(
                                    cleanEmail,
                                    password
                                )
                            }

                            task.addOnCompleteListener { result ->
                                loading = false

                                if (result.isSuccessful) {
                                    infoMessage = if (isRegistering) {
                                        "Account created successfully."
                                    } else {
                                        "Signed in successfully."
                                    }
                                } else {
                                    errorMessage = when {
                                        result.exception?.message
                                            ?.contains(
                                                "network",
                                                ignoreCase = true
                                            ) == true -> {
                                            "Network error. Check your internet connection."
                                        }

                                        else -> {
                                            result.exception
                                                ?.localizedMessage
                                                ?: "Authentication failed. Please try again."
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = !loading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AuthLime,
                    contentColor = AuthDark
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = AuthDark,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isRegistering) {
                            "Create account"
                        } else {
                            "Sign in"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRegistering) {
                        "Already have an account?"
                    } else {
                        "New to Retail Commerce?"
                    },
                    color = AuthMuted,
                    fontSize = 13.sp
                )

                TextButton(
                    enabled = !loading,
                    onClick = {
                        isRegistering = !isRegistering
                        errorMessage = null
                        infoMessage = null
                    }
                ) {
                    Text(
                        text = if (isRegistering) {
                            "Sign in"
                        } else {
                            "Register"
                        },
                        color = AuthLime,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AuthWhite,
    unfocusedTextColor = AuthWhite,
    focusedBorderColor = AuthLime,
    unfocusedBorderColor = Color(0xFF393D35),
    focusedLabelColor = AuthLime,
    unfocusedLabelColor = AuthMuted,
    focusedPlaceholderColor = AuthMuted,
    unfocusedPlaceholderColor = AuthMuted,
    cursorColor = AuthLime,
    focusedContainerColor = AuthPanel,
    unfocusedContainerColor = AuthPanel
)
