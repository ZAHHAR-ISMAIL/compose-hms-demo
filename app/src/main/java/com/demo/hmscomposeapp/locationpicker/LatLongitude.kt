package com.demo.hmscomposeapp.locationpicker

import android.os.Parcelable
import com.google.android.gms.maps.model.LatLng as GoogleLatLng
import com.huawei.hms.maps.model.LatLng as HuaweiLatLng
import kotlinx.parcelize.Parcelize

@Parcelize
data class LatLongitude(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) : Parcelable {

    fun toHuaweiLatLng(): HuaweiLatLng = HuaweiLatLng(latitude, longitude)

    fun toGoogleLatLng(): GoogleLatLng = GoogleLatLng(latitude, longitude)

    companion object {
        fun fromHuaweiLatLng(latLng: HuaweiLatLng): LatLongitude {
            return LatLongitude(
                latitude = latLng.latitude,
                longitude = latLng.longitude
            )
        }
    }
}
