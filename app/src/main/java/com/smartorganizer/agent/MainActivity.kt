package com.smartorganizer.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.smartorganizer.agent.ui.HomeRoute
import com.smartorganizer.agent.ui.theme.SmartOrganizerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartOrganizerTheme {
                HomeRoute()
            }
        }
    }
}
