package com.google.maps.android.collections;

import com.google.android.gms.maps.GoogleMap;

/**
 * Internal Google-only integration hook. 3.8.2 posts setListenersOnUiThread from its constructor.
 * Maps Compose already owns those listeners and routes events through InputHandler. Suppressing
 * that package-private hook avoids replacing them after this cluster layer is disposed. This
 * class replaces no SDK class; the package is needed solely to override that native hook.
 */
public final class SceneOwnedMarkerManager extends MarkerManager {
    public SceneOwnedMarkerManager(GoogleMap map) { super(map); }
    @Override void setListenersOnUiThread() { /* The Compose scene retains listener ownership. */ }
}
