package com.demo.hmscomposeapp.busondemand

import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.demo.maps.compose.Circle
import com.demo.maps.compose.MapUiSettings
import com.demo.maps.compose.MarkerComposable
import com.demo.maps.compose.rememberUpdatedMarkerState
import com.demo.maps.model.LatLng

/** Demo stand-ins for dependencies absent from the pasted screen, not production implementations. */
data class LiveLineTransport(
    val x: Double?,
    val y: Double?,
    val bearing: Double? = null,
    val isAtStop: Boolean? = false,
)

object DemoTheme {
    object firebaseColors {
        object basicColors {
            val black = Color.Black
            val white = Color.White
        }
        object busOnDemandColors { val busOnDemand = Color(0xFF00796B) }
    }
    object shapes { val circularButton = CircleShape }
}

val dimen5sdp = 5.dp
val dimen10sdp = 10.dp
val dimen2sdp = 2.dp
enum class IconSize { S }

@Composable
fun DemoIcon(modifier: Modifier, painter: Painter, contentDescription: String?, tint: Color, iconSize: IconSize) {
    Icon(painter = painter, contentDescription = contentDescription,
        modifier = modifier.size(when (iconSize) { IconSize.S -> 28.dp }), tint = tint)
}

object Timber {
    fun e(throwable: Throwable) { Log.e("BusOnDemandDemo", throwable.message, throwable) }
}

fun defaultMapSetting() = MapUiSettings(myLocationButtonEnabled = false)

/** The real helper was not supplied. This demo uses the same call signature and shared map API. */
@Composable
fun LiveBusTransportMarker(
    markerPosition: LatLng,
    isVisible: Boolean,
    iconResId: Int,
    bearing: Float,
    isAtStop: Boolean,
    rippleColorSolid: Color,
    isBod: Boolean,
) {
    if (isAtStop && isVisible) {
        Circle(center = markerPosition, radius = 35.0, fillColor = rippleColorSolid.copy(alpha = 0.18f),
            strokeColor = Color.Transparent, strokeWidth = 0f, zIndex = 2f)
    }
    MarkerComposable(iconResId, isAtStop, isBod,
        state = rememberUpdatedMarkerState(markerPosition), visible = isVisible,
        anchor = Offset(0.5f, 0.5f), flat = true, rotation = bearing, zIndex = 3f,
        title = if (isAtStop) "Bus at stop" else "Live bus") {
        Icon(painterResource(iconResId), contentDescription = null, tint = Color.Unspecified,
            modifier = Modifier.size(if (isBod) 40.dp else 32.dp))
    }
}
