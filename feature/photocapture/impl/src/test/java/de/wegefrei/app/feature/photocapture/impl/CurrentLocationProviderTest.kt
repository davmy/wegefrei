package de.wegefrei.app.feature.photocapture.impl

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLocationManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Exercises [AndroidCurrentLocationProvider] against the real permission-check/LocationManager
 * flow, using Robolectric's [ShadowLocationManager] to simulate GPS fixes instead of mocking
 * [CurrentLocationProvider] itself.
 */
@RunWith(RobolectricTestRunner::class)
class CurrentLocationProviderTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    private fun shadowLocationManager(): ShadowLocationManager =
        shadowOf(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)

    private fun grantLocationPermission() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun location(
        latitude: Double,
        longitude: Double,
    ): Location =
        Location(LocationManager.GPS_PROVIDER).apply {
            this.latitude = latitude
            this.longitude = longitude
        }

    @Test
    fun `getCurrentLocation returns null without a location permission`() =
        runTest {
            shadowLocationManager().setProviderEnabled(LocationManager.GPS_PROVIDER, true)
            val provider = AndroidCurrentLocationProvider(context)

            val result = provider.getCurrentLocation()

            assertNull(result)
        }

    @Test
    fun `getCurrentLocation returns null when no provider is enabled`() =
        runTest {
            grantLocationPermission()
            shadowLocationManager().setProviderEnabled(LocationManager.GPS_PROVIDER, false)
            shadowLocationManager().setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
            val provider = AndroidCurrentLocationProvider(context)

            val result = provider.getCurrentLocation()

            assertNull(result)
        }

    /**
     * [AndroidCurrentLocationProvider.getCurrentLocation] delivers its result through
     * [Context.getMainExecutor], and Robolectric's main [Looper] doesn't run posted callbacks
     * until told to. The suspend call itself runs on a real background thread (via a real,
     * un-virtualized `Dispatchers.IO`), so this test polls: simulate a fix, drain the main
     * Looper, and repeat until the background call resumes or a timeout trips - avoiding a
     * race between "the request registers with the shadow" and "we simulate a location for it".
     */
    private fun awaitCurrentLocation(
        provider: AndroidCurrentLocationProvider,
        fix: () -> Unit,
    ): LatLng? {
        val result = AtomicReference<LatLng?>()
        val done = CountDownLatch(1)
        val thread =
            Thread {
                result.set(runBlocking { provider.getCurrentLocation() })
                done.countDown()
            }
        thread.start()

        val deadlineMillis = System.currentTimeMillis() + 5_000
        while (done.count > 0 && System.currentTimeMillis() < deadlineMillis) {
            fix()
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        assertTrue("timed out waiting for getCurrentLocation to resume", done.await(0, TimeUnit.MILLISECONDS))
        thread.join()
        return result.get()
    }

    @Test
    fun `getCurrentLocation returns the simulated GPS fix`() {
        grantLocationPermission()
        shadowLocationManager().setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val provider = AndroidCurrentLocationProvider(context)
        val fix = location(52.5200, 13.4050)

        val result = awaitCurrentLocation(provider) { shadowLocationManager().simulateLocation(fix) }

        assertEquals(LatLng(latitude = 52.5200, longitude = 13.4050), result)
    }

    @Test
    fun `getCurrentLocation falls back to the network provider when GPS is disabled`() {
        grantLocationPermission()
        shadowLocationManager().setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowLocationManager().setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        val provider = AndroidCurrentLocationProvider(context)
        val fix = location(48.1351, 11.5820)

        val result =
            awaitCurrentLocation(provider) {
                shadowLocationManager().simulateLocation(LocationManager.NETWORK_PROVIDER, fix)
            }

        assertEquals(LatLng(latitude = 48.1351, longitude = 11.5820), result)
    }
}
