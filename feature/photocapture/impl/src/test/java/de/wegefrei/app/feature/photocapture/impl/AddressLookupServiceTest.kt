package de.wegefrei.app.feature.photocapture.impl

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Exercises [NominatimAddressLookupService] against a real local HTTP server (the JDK's
 * built-in [HttpServer], so no mocking library or extra test dependency is needed) instead of
 * the real nominatim.openstreetmap.org, which would make this test flaky and dependent on
 * network access and a third-party service's availability/rate limits.
 */
@RunWith(RobolectricTestRunner::class)
class AddressLookupServiceTest {
    private var server: HttpServer? = null

    @After
    fun stopServer() {
        server?.stop(0)
    }

    private fun startServer(
        statusCode: Int,
        responseBody: String,
    ): String {
        val httpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        httpServer.createContext("/reverse") { exchange ->
            val bytes = responseBody.toByteArray()
            exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        httpServer.start()
        server = httpServer
        return "http://localhost:${httpServer.address.port}"
    }

    @Test
    fun `reverseGeocode returns the address on a successful response`() =
        runTest {
            val baseUrl = startServer(200, """{"display_name":"Alexanderplatz, Mitte, Berlin, Deutschland"}""")
            val service = NominatimAddressLookupService(baseUrl)

            val result = service.reverseGeocode(52.5200, 13.4050)

            assertEquals("Alexanderplatz, Mitte, Berlin, Deutschland", result)
        }

    @Test
    fun `reverseGeocode returns null for a non-200 response`() =
        runTest {
            val baseUrl = startServer(500, """{"error":"boom"}""")
            val service = NominatimAddressLookupService(baseUrl)

            val result = service.reverseGeocode(52.5200, 13.4050)

            assertNull(result)
        }

    @Test
    fun `reverseGeocode returns null when the connection fails`() =
        runTest {
            // An address nothing is listening on: a closed local port refuses the connection
            // immediately, exercising the same IOException path a real network failure would.
            val closedPort = ServerSocket(0).use { it.localPort }
            val service = NominatimAddressLookupService("http://localhost:$closedPort")

            val result = service.reverseGeocode(52.5200, 13.4050)

            assertNull(result)
        }
}
