package de.wegefrei.app.feature.photocapture.impl

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

interface CurrentLocationProvider {
    suspend fun getCurrentLocation(): LatLng?
}

internal class AndroidCurrentLocationProvider(
    private val context: Context,
) : CurrentLocationProvider {
    override suspend fun getCurrentLocation(): LatLng? {
        val hasPermission =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return null

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider =
            when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> return null
            }

        return suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }

            try {
                locationManager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    context.mainExecutor,
                ) { location ->
                    val result = location?.let { LatLng(latitude = it.latitude, longitude = it.longitude) }
                    if (continuation.isActive) continuation.resume(result)
                }
            } catch (e: SecurityException) {
                // Defensive fallback: permission may have been revoked between the
                // upfront check and this call (TOCTOU).
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }
}
