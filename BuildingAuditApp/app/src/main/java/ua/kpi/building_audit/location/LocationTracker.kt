package ua.kpi.building_audit.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ua.kpi.building_audit.domain.model.GeoPoint

class LocationUnavailableException(message: String) : Exception(message)

/**
 * Позиція пристрою як Flow поверх системного LocationManager. Google Play Services не потрібні,
 * тому трекер працює і на емуляторі без Google API. Підписка на оновлення знімається,
 * коли збирач потоку скасовується (awaitClose).
 */
class LocationTracker(private val context: Context) {

    fun hasPermission(): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun updates(intervalMs: Long = 2_000): Flow<GeoPoint> = callbackFlow {
        if (!hasPermission()) throw LocationUnavailableException("Немає дозволу на геолокацію")
        val manager = context.getSystemService(LocationManager::class.java)
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { manager.isProviderEnabled(it) }
            ?: throw LocationUnavailableException("Геолокацію вимкнено в налаштуваннях пристрою")

        manager.getLastKnownLocation(provider)?.let { trySend(GeoPoint(it.latitude, it.longitude)) }
        val listener = LocationListener { location -> trySend(GeoPoint(location.latitude, location.longitude)) }
        manager.requestLocationUpdates(provider, intervalMs, 5f, listener, Looper.getMainLooper())
        awaitClose { manager.removeUpdates(listener) }
    }

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
