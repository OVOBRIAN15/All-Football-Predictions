package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import com.example.data.firebase.AuthHelper
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.AllFootballPredictionsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val credentialManager = CredentialManager.create(this)
        AuthHelper.attemptAutoSignIn(
            context = this,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = lifecycleScope
        )

        setContent {
            AllFootballPredictionsTheme {
                AppNavigation()
            }
        }
    }
}
