package com.demo.hmscomposeapp.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.demo.maps.model.BitmapDescriptor
import com.demo.maps.model.BitmapDescriptorFactory

/** Rasterize a fresh drawable. Reject invalid dimensions before allocating its bitmap. */
internal fun vectorBitmap(context: Context, @DrawableRes resource: Int, tint: Int? = null): Bitmap {
    val drawable = requireNotNull(ContextCompat.getDrawable(context, resource)) { "Drawable not found: $resource" }.mutate()
    val width = drawable.intrinsicWidth
    val height = drawable.intrinsicHeight
    require(width > 0 && height > 0) { "Drawable must have positive intrinsic dimensions: $resource ($width x $height)" }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        tint?.let(drawable::setTint)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(it))
    }
}

fun bitmapDescriptorFromVector(context: Context, @DrawableRes vectorResId: Int): BitmapDescriptor =
    BitmapDescriptorFactory.fromBitmap(vectorBitmap(context, vectorResId))
