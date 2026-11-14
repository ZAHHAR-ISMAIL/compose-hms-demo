package com.demo.hmscomposeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.demo.hmscomposeapp.ui.theme.HmsComposeAppTheme
import com.demo.maps.MapsInitializer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapsInitializer.initialize(applicationContext)
        setContent {
            HmsComposeAppTheme {
                MapDemo()
            }
        }
    }
}
