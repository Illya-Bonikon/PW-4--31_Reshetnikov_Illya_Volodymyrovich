package ua.kpi.building_audit.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import ua.kpi.building_audit.domain.BuildingAssessment
import ua.kpi.building_audit.domain.model.GeoPoint
import org.osmdroid.util.GeoPoint as OsmPoint

private val KYIV_CENTER = OsmPoint(50.4470, 30.4470)

/**
 * Мапа OpenStreetMap (osmdroid): API-ключ не потрібен. Обрана будівля показується повністю
 * непрозорою з відкритим InfoWindow, решта маркерів напівпрозорі.
 */
@Composable
fun BuildingsMap(
    items: List<BuildingAssessment>,
    focusedId: Long?,
    onMarkerClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    userLocation: GeoPoint? = null,
    radiusKm: Double? = null
) {
    val context = LocalContext.current
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(14.5)
            controller.setCenter(KYIV_CENTER)
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            map.onDetach()
        }
    }

    // Камера рухається лише при зміні фокусу, а не при кожному оновленні списку.
    LaunchedEffect(focusedId) {
        items.firstOrNull { it.building.id == focusedId }?.building?.location?.let { map.controller.animateTo(it.toOsm()) }
    }

    AndroidView(factory = { map }, modifier = modifier, update = { view ->
        view.overlays.clear()
        if (userLocation != null && radiusKm != null) {
            view.overlays.add(Polygon(view).apply {
                points = Polygon.pointsAsCircle(userLocation.toOsm(), radiusKm * 1000)
                fillPaint.color = 0x221976D2
                outlinePaint.color = 0xFF1976D2.toInt()
                outlinePaint.strokeWidth = 3f
            })
        }
        items.forEach { item ->
            val location = item.building.location ?: return@forEach
            val focused = item.building.id == focusedId
            val marker = Marker(view).apply {
                position = location.toOsm()
                title = item.building.name
                snippet = "${item.building.type.title}, клас ${item.energyClass.label}<br>${item.building.status.title}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                alpha = if (focused) 1f else 0.55f
                setOnMarkerClickListener { clicked, _ ->
                    onMarkerClick(item.building.id)
                    clicked.showInfoWindow()
                    true
                }
            }
            view.overlays.add(marker)
            if (focused) marker.showInfoWindow()
        }
        if (userLocation != null) {
            view.overlays.add(Marker(view).apply {
                position = userLocation.toOsm()
                title = "Ви тут"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = context.getDrawable(android.R.drawable.presence_online)
            })
        }
        view.invalidate()
    })
}

private fun GeoPoint.toOsm() = OsmPoint(latitude, longitude)
