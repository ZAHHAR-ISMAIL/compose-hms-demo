package com.demo.maps

import com.demo.maps.compose.MapProperties
import com.demo.maps.compose.MapType
import com.demo.maps.compose.isSupported
import org.junit.Assert.*
import org.junit.Test

class MapTypeSupportTest {
    @Test fun requestedMapTypesEitherConstructOrFailExplicitly() {
        for (type in MapType.entries) {
            val result=runCatching { MapProperties(mapType=type) }
            if (type.isSupported()) assertTrue("$type should remain supported",result.isSuccess)
            else {
                assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
                assertTrue(result.exceptionOrNull()!!.message!!.contains(type.name))
            }
        }
        assertTrue(MapType.NORMAL.isSupported())
        assertTrue(MapType.NONE.isSupported())
        assertTrue(MapType.TERRAIN.isSupported())
    }
}
