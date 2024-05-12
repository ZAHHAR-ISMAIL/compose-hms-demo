package com.demo.hmscomposeapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.demo.hmscomposeapp.map.HmsMap
import com.demo.hmscomposeapp.ui.theme.HmsComposeAppTheme
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {

        val scope = CoroutineScope(CoroutineName("ScopeOne"))

        super.onCreate(savedInstanceState)

        val hmsInit = HmsInit(this)
        hmsInit.hmsMapInit()
        // Permission already granted, proceed with your coroutines here
        if (hmsInit.checkLocationPermissions()) {
            // Init HMS
            hmsInit.chekLocationSettings()
            hmsInit.getHmsLocation()
        } else {
            hmsInit.requestLocationPermissions()
        }

        setContent {
            HmsComposeAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    HmsMapCompose()
                }
            }
        }

        scope.launch {
            Log.d("Coroutine : ", this.coroutineContext.toString())
        }
    }


    @Composable
    fun HmsMapCompose() {
        MaterialTheme {

            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxHeight()
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Huawei Map in Jetpack Compose",
                    style = MaterialTheme.typography.h6
                )
                Spacer(modifier = Modifier.height(16.dp))
                HmsMap(
                    modifier = Modifier
                        .height(300.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
//                {
//                    huaweiMap -> huaweiMap
//                    huaweiMap.setOnMapLongClickListener { latLng ->
//                        addMarker(latLng)
//                    }
//                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        //changeCameraPosition()

                    },
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text("Get My Location")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Long click anywhere in the map to insert marker",
                    style = MaterialTheme.typography.subtitle2,
                    modifier = Modifier.alpha(0.5f)
                )
            }

        }
    }


}





