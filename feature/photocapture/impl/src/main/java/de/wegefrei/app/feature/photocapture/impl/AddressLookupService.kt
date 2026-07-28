package de.wegefrei.app.feature.photocapture.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal fun parseNominatimDisplayName(json: String): String? =
    try {
        val displayName = JSONObject(json).optString("display_name", "")
        displayName.ifBlank { null }
    } catch (e: JSONException) {
        null
    }

interface AddressLookupService {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String?
}

internal class NominatimAddressLookupService(
    private val baseUrl: String = "https://nominatim.openstreetmap.org",
) : AddressLookupService {

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "$baseUrl/reverse?format=json&lat=$latitude&lon=$longitude",
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "wegefrei-android/1.0 (+https://github.com/davmy/wegefrei)")
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    connection.disconnect()
                    return@withContext null
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                parseNominatimDisplayName(body)
            } catch (e: IOException) {
                null
            }
        }
}
