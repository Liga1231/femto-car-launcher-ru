package io.github.seijikohara.femto.data.navigation

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OsrmRoutingApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses route steps and geometry from osrm response`() =
        runTest {
            server.enqueue(MockResponse().setBody(SAMPLE_OSRM_RESPONSE))

            val baseUrl = server.url("/").toString().removeSuffix("/")
            val api = OsrmRoutingApi(
                client = client,
                userAgent = "TestAgent",
                primaryBaseUrl = baseUrl,
                fallbackBaseUrl = baseUrl,
            )

            val route = api.calculateRoute(
                startLat = 55.75,
                startLon = 37.61,
                destLat = 55.76,
                destLon = 37.62,
                destinationLabel = "Red Square",
            )

            assertNotNull(route)
            assertEquals("Red Square", route.destinationLabel)
            assertEquals(1200.0, route.totalDistanceMeters)
            assertEquals(180.0, route.totalDurationSeconds)
            assertEquals(3, route.coordinates.size)
            assertEquals(2, route.steps.size)

            val firstStep = route.steps[0]
            assertEquals("Tverskaya St.", firstStep.streetName)
            assertEquals("right", firstStep.maneuverModifier)
            assertTrue(firstStep.instruction.contains("Tverskaya St."))

            assertTrue(route.routeGeoJson.contains("FeatureCollection"))
            assertTrue(route.routeGeoJson.contains("LineString"))
            assertTrue(route.routeGeoJson.contains("destination"))
        }

    @Test
    fun `returns null on error response code`() =
        runTest {
            server.enqueue(MockResponse().setBody("""{"code":"NoRoute"}"""))

            val baseUrl = server.url("/").toString().removeSuffix("/")
            val api = OsrmRoutingApi(
                client = client,
                userAgent = "TestAgent",
                primaryBaseUrl = baseUrl,
                fallbackBaseUrl = baseUrl,
            )

            val route = api.calculateRoute(55.75, 37.61, 55.76, 37.62)
            assertNull(route)
        }

    @Test
    fun `returns null on http 500 failure`() =
        runTest {
            server.enqueue(MockResponse().setResponseCode(500))
            server.enqueue(MockResponse().setResponseCode(500))

            val baseUrl = server.url("/").toString().removeSuffix("/")
            val api = OsrmRoutingApi(
                client = client,
                userAgent = "TestAgent",
                primaryBaseUrl = baseUrl,
                fallbackBaseUrl = baseUrl,
            )

            val route = api.calculateRoute(55.75, 37.61, 55.76, 37.62)
            assertNull(route)
        }

    companion object {
        private const val SAMPLE_OSRM_RESPONSE = """
        {
          "code": "Ok",
          "routes": [
            {
              "distance": 1200.0,
              "duration": 180.0,
              "weight_name": "routability",
              "weight": 180.0,
              "geometry": {
                "coordinates": [
                  [37.61, 55.75],
                  [37.615, 55.755],
                  [37.62, 55.76]
                ],
                "type": "LineString"
              },
              "legs": [
                {
                  "distance": 1200.0,
                  "duration": 180.0,
                  "summary": "Tverskaya St.",
                  "steps": [
                    {
                      "name": "Tverskaya St.",
                      "distance": 800.0,
                      "duration": 120.0,
                      "mode": "driving",
                      "driving_side": "right",
                      "maneuver": {
                        "type": "turn",
                        "modifier": "right",
                        "location": [37.615, 55.755]
                      }
                    },
                    {
                      "name": "",
                      "distance": 400.0,
                      "duration": 60.0,
                      "mode": "driving",
                      "driving_side": "right",
                      "maneuver": {
                        "type": "arrive",
                        "location": [37.62, 55.76]
                      }
                    }
                  ]
                }
              ]
            }
          ],
          "waypoints": []
        }
        """
    }
}
