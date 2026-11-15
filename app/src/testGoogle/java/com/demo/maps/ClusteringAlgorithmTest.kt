package com.demo.maps

import com.demo.maps.clustering.ClusterItem
import com.demo.maps.model.CameraPosition
import com.demo.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import kotlin.random.Random

/** Google-only regression baseline. HMS intentionally uses a different grouping algorithm. */
class ClusteringAlgorithmTest {
    private data class Station(val id: Int, val lat: Double, val lon: Double) : ClusterItem {
        override fun getPosition() = LatLng(lat,lon)
        override fun getTitle() = "Station $id"
        override fun getSnippet(): String? = "Metadata $id"
        override fun getZIndex(): Float? = id.toFloat()
        override fun hashCode() = id
    }
    @Test fun groupingMatchesNativeBaselineAcrossPanResizeZoomAndChanges() {
        val random = Random(651382)
        val items = List(1000) { Station(it,24.7+random.nextDouble(-.15,.15),46.67+random.nextDouble(-.15,.15)) }
        val results = mutableListOf<String>()
        for (zoom in listOf(8f,12f,12.99f,13f,17f)) for (width in listOf(180,360,720)) for (pan in listOf(0.0,.02)) {
            val algorithm = ViewGrouping<Station>(width,640)
            algorithm.addItems(items)
            algorithm.onCameraChange(CameraPosition.fromLatLngZoom(LatLng(24.7,46.67+pan),zoom))
            fun fingerprint() = algorithm.getClusters(zoom).map { group -> group.items.map { it.id }.sorted().joinToString(",") }
                .sorted().joinToString(";").let { text -> MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) } }
            results.add("$zoom/$width/$pan=${fingerprint()}")
            algorithm.updateViewSize(width/2,320)
            algorithm.removeItems(items.take(100))
            algorithm.addItem(Station(1001,24.7,46.67))
            results.add("$zoom/$width/$pan/changed=${fingerprint()}")
        }
        val actual = results.joinToString("\n")+"\n"
        println("NATIVE_CLUSTER_GOLDEN_BEGIN\n${actual}NATIVE_CLUSTER_GOLDEN_END")
        val expected = javaClass.getResourceAsStream("/cluster-membership-3.8.2.txt")?.bufferedReader()?.use { it.readText() }
        assertNotNull("Record the pinned native Google grouping baseline",expected)
        assertEquals(expected,actual)
    }
    @Test fun viewFilteringAndOneTwoThreeItemThresholdInputs() {
        val algorithm = ViewGrouping<Station>(360,640)
        algorithm.onCameraChange(CameraPosition.fromLatLngZoom(LatLng(24.7,46.67),14f))
        assertTrue(algorithm.getClusters(14f).isEmpty())
        repeat(3) { index ->
            algorithm.addItem(Station(index,24.7,46.67))
            val group = algorithm.getClusters(14f).single()
            assertEquals(index+1,group.size)
            assertEquals(index>=1,group.size>=2)
        }
        algorithm.onCameraChange(CameraPosition.fromLatLngZoom(LatLng(0.0,0.0),14f))
        assertTrue(algorithm.getClusters(14f).isEmpty())
    }
    @Test fun distanceEdgesAndDateLineVisibilityMatchPinnedGroupingRules() {
        for (distanceDp in listOf(49.999,50.0,50.001)) {
            val algorithm = ViewGrouping<Station>(360,640)
            val longitude = distanceDp*360/(256*(1 shl 12))
            algorithm.addItems(listOf(Station(1,0.0,0.0),Station(2,0.0,longitude)))
            algorithm.onCameraChange(CameraPosition.fromLatLngZoom(LatLng(0.0,0.0),12.99f))
            // Search spans use integer zoom; bounds include their exact edge.
            assertEquals(if (distanceDp<=50) 1 else 2,algorithm.getClusters(12.99f).size)
        }
        val dateline = ViewGrouping<Station>(360,640)
        dateline.addItems(listOf(Station(1,0.0,179.999),Station(2,0.0,-179.999)))
        dateline.onCameraChange(CameraPosition.fromLatLngZoom(LatLng(0.0,179.999),10f))
        assertEquals(2,dateline.getClusters(10f).flatMap { it.items }.size)
        dateline.onCameraChange(CameraPosition.fromLatLngZoom(LatLng(0.0,0.0),10f))
        assertTrue(dateline.getClusters(10f).isEmpty())
    }
}
