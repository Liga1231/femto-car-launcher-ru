package io.github.seijikohara.femto.data.navigation

import android.location.Location
import androidx.test.core.app.ApplicationProvider
import io.github.seijikohara.femto.data.places.PlaceTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NavigationManagerTest {
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
    fun `starts navigation and transitions to active state`() =
        runTest {
            server.enqueue(MockResponse().setBody(SAMPLE_OSRM_RESPONSE))
            val baseUrl = server.url("/").toString().removeSuffix("/")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val testScope = TestScope(testDispatcher)

            val routingApi = OsrmRoutingApi(
                client = client,
                userAgent = "TestAgent",
                primaryBaseUrl = baseUrl,
                fallbackBaseUrl = baseUrl,
                ioDispatcher = testDispatcher,
            )
            val geocoder = DestinationGeocoder(
                context = ApplicationProvider.getApplicationContext(),
                client = client,
                userAgent = "TestAgent",
                ioDispatcher = testDispatcher,
            )
            val manager = NavigationManager(
                routingApi = routingApi,
                destinationGeocoder = geocoder,
                scope = testScope,
            )

            val origin = Location("gps").apply {
                latitude = 55.75
                longitude = 37.61
            }

            manager.startNavigation(
                origin = origin,
                target = PlaceTarget.Point(55.76, 37.62),
                label = "Target",
            )

            testScope.advanceUntilIdle()

            val state = manager.state.value
            assertIs<NavigationState.Active>(state)
            assertEquals("Target", state.route.destinationLabel)
            assertEquals(1200.0, state.remainingDistanceMeters)
            assertEquals(0, state.currentStepIndex)
        }

    @Test
    fun `advances step and detects arrival`() =
        runTest {
            server.enqueue(MockResponse().setBody(SAMPLE_OSRM_RESPONSE))
            val baseUrl = server.url("/").toString().removeSuffix("/")

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val testScope = TestScope(testDispatcher)

            val routingApi = OsrmRoutingApi(
                client = client,
                userAgent = "TestAgent",
                primaryBaseUrl = baseUrl,
                fallbackBaseUrl = baseUrl,
                ioDispatcher = testDispatcher,
            )
            val geocoder = DestinationGeocoder(
                context = ApplicationProvider.getApplicationContext(),
                client = client,
                userAgent = "TestAgent",
                ioDispatcher = testDispatcher,
            )
            val manager = NavigationManager(
                routingApi = routingApi,
                destinationGeocoder = geocoder,
                scope = testScope,
            )

            val origin = Location("gps").apply {
                latitude = 55.75
                longitude = 37.61
            }

            manager.startNavigation(
                origin = origin,
                target = PlaceTarget.Point(55.76, 37.62),
                label = "Target",
            )
            testScope.advanceUntilIdle()

            // Move car to maneuver coordinate
            val nearManeuver = Location("gps").apply {
                latitude = 55.755
                longitude = 37.615
            }
            manager.onLocationChanged(nearManeuver)
            val stateAfterStep = manager.state.value
            assertIs<NavigationState.Active>(stateAfterStep)
            assertEquals(1, stateAfterStep.currentStepIndex)

            // Move car to destination
            val atDest = Location("gps").apply {
                latitude = 55.76
                longitude = 37.62
            }
            manager.onLocationChanged(atDest)
            val stateAfterArrival = manager.state.value
            assertIs<NavigationState.Arrived>(stateAfterArrival)
            assertEquals("Target", stateAfterArrival.destinationLabel)

            // Stop navigation returns to Idle
            manager.stopNavigation()
            assertIs<NavigationState.Idle>(manager.state.value)
        }

    companion object {
        private const val SAMPLE_OSRM_RESPONSE = """
        {
          "code": "Ok",
          "routes": [
            {
              "distance": 1200.0,
              "duration": 180.0,
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
                  "summary": "Тверская ул.",
                  "steps": [
                    {
                      "name": "Тверская ул.",
                      "distance": 800.0,
                      "duration": 120.0,
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
