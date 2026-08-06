package com.msi.gittool

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.msi.gittool.ui.navigation.NavGraph
import com.msi.gittool.ui.theme.MyApplicationTheme
import com.msi.gittool.ui.theme.ThemeViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        val container = (applicationContext as GitToolApplication).container
        
        // Handle OAuth callback intent on fresh launch/creation of MainActivity
        intent?.let {
            container.authManager.handleIntent(it)
        }
        
        setContent {
            val themeViewModel: ThemeViewModel = viewModel(
                factory = ThemeViewModel.Factory(container.themePreferences)
            )
            val themeMode by themeViewModel.themeMode.collectAsState()

            MyApplicationTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                
                Box(modifier = Modifier.fillMaxSize()) {
                    NavGraph(
                        navController = navController,
                        themeViewModel = themeViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val container = (applicationContext as GitToolApplication).container
        container.authManager.handleIntent(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val container = (applicationContext as GitToolApplication).container
        container.authManager.handleAuthorizationResponse(requestCode, resultCode, data)
    }
}
