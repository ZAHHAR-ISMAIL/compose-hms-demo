package com.demo.maps

import com.demo.maps.model.LatLng
import com.demo.maps.model.LatLngBounds
import org.junit.Assert.*
import org.junit.Test

class GeometryTest {
    @Test fun coordinatesNormalizeLikeMapSdks() {
        assertEquals(LatLng(90.0, -179.0), LatLng(100.0, 181.0))
        assertEquals(LatLng(-90.0, 179.0), LatLng(-100.0, -181.0))
    }
    @Test(expected = IllegalArgumentException::class) fun nonFiniteCoordinatesAreRejected() { LatLng(Double.NaN, 0.0) }
    @Test(expected = IllegalStateException::class) fun emptyBoundsAreRejected() { LatLngBounds.builder().build() }
    @Test fun singlePointBoundsContainPointAndHaveSameCenter() {
        val point = LatLng(24.7, 46.6)
        val bounds = LatLngBounds.builder().include(point).build()
        assertEquals(point, bounds.center)
        assertTrue(bounds.contains(point))
    }
    @Test fun riyadhBoundsIncludeEveryPoint() {
        val points = listOf(LatLng(24.7, 46.6), LatLng(24.6, 46.7), LatLng(24.8, 46.8))
        val bounds = LatLngBounds.builder().apply { points.forEach(::include) }.build()
        assertTrue(points.all(bounds::contains))
        assertFalse(bounds.contains(LatLng(25.0, 46.7)))
    }
    @Test fun antimeridianUsesShortArc() {
        val bounds = LatLngBounds.builder().include(LatLng(-10.0, 170.0)).include(LatLng(10.0, -170.0)).build()
        assertTrue(bounds.contains(LatLng(0.0, 179.0)))
        assertTrue(bounds.contains(LatLng(0.0, -179.0)))
        assertFalse(bounds.contains(LatLng(0.0, 0.0)))
        assertEquals(-180.0, bounds.center.longitude, 0.0)
    }
    @Test fun includingExpandsExistingBounds() {
        val bounds = LatLngBounds(LatLng(-10.0, 10.0), LatLng(10.0, 30.0)).including(LatLng(20.0, 40.0))
        assertTrue(bounds.contains(LatLng(20.0, 40.0)))
        assertTrue(bounds.contains(LatLng(-10.0, 10.0)))
    }
    @Test fun includingPreservesWideBounds() {
        val original = LatLngBounds(LatLng(-10.0, -150.0), LatLng(10.0, 150.0))
        val expanded = original.including(LatLng(20.0, 0.0))
        assertTrue(expanded.contains(LatLng(0.0, 0.0)))
        assertTrue(expanded.contains(original.southwest))
        assertTrue(expanded.contains(original.northeast))
    }
    @Test(expected = IllegalArgumentException::class) fun negativeCameraPaddingIsRejected() {
        CameraUpdateFactory.newLatLngBounds(LatLngBounds.builder().include(LatLng(0.0, 0.0)).build(), -1)
    }
}
