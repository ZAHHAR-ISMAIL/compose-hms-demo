package com.demo.maps

import android.content.Context
import com.demo.maps.backend.MapBackend

/** Optional compatibility entry point. The application installs its flavor backend at the root. */
object MapsInitializer {
    private var backend: MapBackend? = null
    fun install(backend: MapBackend) { this.backend = backend }
    fun initialize(context: Context) {
        checkNotNull(backend) { "Install the flavor backend with ProvideMapBackend before initializing maps" }
            .initialize(context.applicationContext)
    }
}
