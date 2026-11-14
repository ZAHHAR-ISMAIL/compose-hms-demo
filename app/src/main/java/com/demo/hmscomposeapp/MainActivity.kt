package com.demo.hmscomposeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.demo.hmscomposeapp.ui.theme.HmsComposeAppTheme
import com.demo.hmscomposeapp.map.createMapBackend
import com.demo.maps.compose.ProvideMapBackend

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HmsComposeAppTheme {
                ProvideMapBackend(createMapBackend()) {
                    MapDemo()
                }
            }
        }
    }
}
