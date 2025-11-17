package com.eopeter.fluttermapboxnavigation

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.eopeter.fluttermapboxnavigation.databinding.NavigationActivityBinding
import com.eopeter.fluttermapboxnavigation.models.MapBoxEvents
import com.eopeter.fluttermapboxnavigation.models.MapBoxRouteProgressEvent
import com.eopeter.fluttermapboxnavigation.models.Waypoint
import com.eopeter.fluttermapboxnavigation.models.WaypointSet
import com.eopeter.fluttermapboxnavigation.utilities.CustomInfoPanelEndNavButtonBinder
import com.eopeter.fluttermapboxnavigation.utilities.PluginUtilities
import com.google.gson.Gson
import com.mapbox.maps.Style
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.*
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineResources
import com.mapbox.navigation.dropin.map.MapViewObserver
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.LayerPosition
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject
import java.util.*
import kotlin.math.*

open class TurnByTurn(
    ctx: Context,
    act: Activity,
    bind: NavigationActivityBinding,
    accessToken: String
) : MethodChannel.MethodCallHandler,
    EventChannel.StreamHandler,
    Application.ActivityLifecycleCallbacks {

    open fun initFlutterChannelHandlers() {
        this.methodChannel?.setMethodCallHandler(this)
        this.eventChannel?.setStreamHandler(this)
    }

    open fun initNavigation() {
        val navigationOptions = NavigationOptions.Builder(this.context)
            .accessToken(this.token)
            .build()

        MapboxNavigationApp
            .setup(navigationOptions)
            .attach(this.activity as LifecycleOwner)

        // initialize navigation trip observers
        this.registerObservers()
    }

    override fun onMethodCall(methodCall: MethodCall, result: MethodChannel.Result) {
        when (methodCall.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            "enableOfflineRouting" -> {
                // downloadRegionForOfflineRouting(call, result)
            }
            "buildRoute" -> {
                this.buildRoute(methodCall, result)
            }
            "clearRoute" -> {
                this.clearRoute(methodCall, result)
            }
            "startFreeDrive" -> {
                FlutterMapboxNavigationPlugin.enableFreeDriveMode = true
                this.startFreeDrive()
            }
            "startNavigation" -> {
                FlutterMapboxNavigationPlugin.enableFreeDriveMode = false
                this.startNavigation(methodCall, result)
            }
            "startNavigationWithGeoJson" -> {
                FlutterMapboxNavigationPlugin.enableFreeDriveMode = false
                this.startNavigationWithGeoJson(methodCall, result)
            }
            "finishNavigation" -> {
                this.finishNavigation(methodCall, result)
            }
            "getDistanceRemaining" -> {
                result.success(this.distanceRemaining)
            }
            "getDurationRemaining" -> {
                result.success(this.durationRemaining)
            }
            else -> result.notImplemented()
        }
    }

    private fun buildRoute(methodCall: MethodCall, result: MethodChannel.Result) {
        this.isNavigationCanceled = false

        val arguments = methodCall.arguments as? Map<*, *>
        if (arguments != null) this.setOptions(arguments)
        this.addedWaypoints.clear()
        val points = arguments?.get("wayPoints") as HashMap<*, *>
        for (item in points) {
            val point = item.value as HashMap<*, *>
            val latitude = point["Latitude"] as Double
            val longitude = point["Longitude"] as Double
            val isSilent = point["IsSilent"] as Boolean
            this.addedWaypoints.add(Waypoint(Point.fromLngLat(longitude, latitude),isSilent))
        }
        this.getRoute(this.context)
        result.success(true)
    }

    private fun getRoute(context: Context) {
        MapboxNavigationApp.current()!!.requestRoutes(
            routeOptions = RouteOptions
                .builder()
                .applyDefaultNavigationOptions(navigationMode)
                .applyLanguageAndVoiceUnitOptions(context)
                .coordinatesList(this.addedWaypoints.coordinatesList())
                .waypointIndicesList(this.addedWaypoints.waypointsIndices())
                .waypointNamesList(this.addedWaypoints.waypointsNames())
                .language(navigationLanguage)
                .alternatives(alternatives)
                .steps(true)
                .voiceUnits(navigationVoiceUnits)
                .bannerInstructions(bannerInstructionsEnabled)
                .voiceInstructions(voiceInstructionsEnabled)
                .build(),
            callback = object : NavigationRouterCallback {
                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
                    this@TurnByTurn.currentRoutes = routes
                    
                    // Always store the route as planned route (guide layer)
                    if (this@TurnByTurn.showPlannedRoute && this@TurnByTurn.plannedRoute == null) {
                        this@TurnByTurn.plannedRoute = routes
                        // Store geometry for comparison
                        routes.firstOrNull()?.directionsRoute?.geometry()?.let { geometry ->
                            this@TurnByTurn.plannedRouteGeometry = geometry
                        }
                    }
                    
                    PluginUtilities.sendEvent(
                        MapBoxEvents.ROUTE_BUILT,
                        Gson().toJson(routes.map { it.directionsRoute.toJson() })
                    )
                    this@TurnByTurn.binding.navigationView.api.routeReplayEnabled(
                        this@TurnByTurn.simulateRoute
                    )
                    this@TurnByTurn.binding.navigationView.api.startRoutePreview(routes)
                    this@TurnByTurn.binding.navigationView.customizeViewBinders {
                        this.infoPanelEndNavigationButtonBinder =
                            CustomInfoPanelEndNavButtonBinder(activity)
                    }
                }

                override fun onFailure(
                    reasons: List<RouterFailure>,
                    routeOptions: RouteOptions
                ) {
                    PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILD_FAILED)
                }

                override fun onCanceled(
                    routeOptions: RouteOptions,
                    routerOrigin: RouterOrigin
                ) {
                    PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILD_CANCELLED)
                }
            }
        )
    }

    private fun getRouteFromGeoJson(context: Context, result: MethodChannel.Result) {
        try {
            Log.d("GeoJSON", "🗺️ Loading new GeoJSON route - cleaning old layers first...")

            // IMPORTANT: Remove old layers before creating new ones to avoid conflicts
            removePlannedRouteLayers()

            val geometry = this.geoJsonRouteGeometry ?: run {
                result.error("INVALID_GEOJSON", "GeoJSON geometry is null", null)
                return
            }

            Log.d("GeoJSON", "✅ Old layers cleaned, proceeding with new route...")

            // Parse coordinates to get start and end points
            val coordinatesList = try {
                parseGeoJsonLineString(geometry)
            } catch (e: Exception) {
                try {
                    com.mapbox.geojson.utils.PolylineUtils.decode(geometry, 5)
                        .map { Point.fromLngLat(it.longitude(), it.latitude()) }
                } catch (e2: Exception) {
                    result.error("PARSE_ERROR", "Failed to parse GeoJSON geometry: ${e2.message}", null)
                    return
                }
            }

            if (coordinatesList.size < 2) {
                result.error("INVALID_GEOJSON", "Route must have at least 2 points", null)
                return
            }

            // Convert coordinates list to GeoJSON LineString format
            val geometryJson = if (geometry.contains("{")) {
                // Already in GeoJSON format
                geometry
            } else {
                // Convert polyline to GeoJSON LineString
                val coords = coordinatesList.map { listOf(it.longitude(), it.latitude()) }
                """{"type":"LineString","coordinates":${Gson().toJson(coords)}}"""
            }

            // CRITICAL: Log the FIRST and LAST points to ensure they're included
            Log.d("GeoJsonRoute", "📍 FIRST point of route: [${coordinatesList.first().longitude()}, ${coordinatesList.first().latitude()}]")
            Log.d("GeoJsonRoute", "📍 LAST point of route: [${coordinatesList.last().longitude()}, ${coordinatesList.last().latitude()}]")
            Log.d("GeoJsonRoute", "📊 Total coordinates in GeoJSON: ${coordinatesList.size}")

            // Check if user is near the start point of the planned route
            val currentLocation = this.lastLocation
            val routeStartPoint = coordinatesList.first()
            val distanceToStart = if (currentLocation != null) {
                calculateDistance(
                    currentLocation.latitude, currentLocation.longitude,
                    routeStartPoint.latitude(), routeStartPoint.longitude()
                )
            } else {
                0.0
            }

            Log.d("GeoJsonRoute", "📏 Distance to route start: ${distanceToStart.toInt()}m")

            // CRITICAL: Determine navigation strategy based on distance to start
            val needsRouteToStart = distanceToStart > 200.0 // More than 200m from start

            val waypoints = if (needsRouteToStart && currentLocation != null) {
                // User is far from start - create route TO the start point first
                Log.w("GeoJsonRoute", "⚠️ User is ${distanceToStart.toInt()}m from route start")
                Log.w("GeoJsonRoute", "🔵 Creating BLUE route from current location to route START")
                Log.w("GeoJsonRoute", "🟢 GREEN line will show the complete planned route")

                // Waypoints: Current Location → First point of planned route
                val currentPoint = Point.fromLngLat(currentLocation.longitude, currentLocation.latitude)
                listOf(currentPoint, routeStartPoint)
            } else {
                // User is near start - use the planned route directly
                Log.i("GeoJsonRoute", "✅ User is near route start (${distanceToStart.toInt()}m)")
                Log.i("GeoJsonRoute", "🔵 Using planned route for navigation")

                // Extract waypoints from the route to ensure it follows the GEOJSON exactly
                extractWaypointsFromRoute(coordinatesList)
            }

            Log.d("GeoJsonRoute", "Creating navigation route with ${waypoints.size} waypoints")
            Log.d("GeoJsonRoute", "📍 First waypoint: [${waypoints.first().longitude()}, ${waypoints.first().latitude()}]")
            Log.d("GeoJsonRoute", "📍 Last waypoint: [${waypoints.last().longitude()}, ${waypoints.last().latitude()}]")

            // ALWAYS store the planned route geometry for the GREEN line
            this.plannedRouteGeometry = geometryJson
            Log.d("GeoJsonRoute", "✅ Planned route geometry stored for GREEN line")

            if (needsRouteToStart) {
                // User is far from start - use REAL Mapbox API to navigate TO the start
                Log.i("GeoJsonRoute", "📡 Requesting route from Mapbox API (current → start)")

                MapboxNavigationApp.current()!!.requestRoutes(
                    routeOptions = RouteOptions
                        .builder()
                        .applyDefaultNavigationOptions(navigationMode)
                        .applyLanguageAndVoiceUnitOptions(context)
                        .coordinatesList(waypoints)
                        .language(navigationLanguage)
                        .alternatives(false)
                        .steps(true)
                        .voiceUnits(navigationVoiceUnits)
                        .bannerInstructions(bannerInstructionsEnabled)
                        .voiceInstructions(voiceInstructionsEnabled)
                        .build(),
                    callback = object : NavigationRouterCallback {
                        override fun onRoutesReady(
                            routes: List<NavigationRoute>,
                            routerOrigin: RouterOrigin
                        ) {
                            Log.i("GeoJsonRoute", "✅ API route received - navigating to route START")

                            this@TurnByTurn.currentRoutes = routes
                            // DON'T set plannedRoute here - it's the original GeoJSON

                            PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILT, "Route to start created")

                            // Re-register observers
                            registerObservers()

                            this@TurnByTurn.binding.navigationView.api.routeReplayEnabled(this@TurnByTurn.simulateRoute)
                            this@TurnByTurn.binding.navigationView.api.startActiveGuidance(routes)

                            // Draw the planned route (GREEN line) - ALWAYS show complete route
                            drawPlannedRoute()

                            result.success(true)
                            PluginUtilities.sendEvent(MapBoxEvents.NAVIGATION_RUNNING)
                        }

                        override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                            Log.e("GeoJsonRoute", "❌ Failed to create route to start: $reasons")
                            result.error("ROUTE_TO_START_FAILED", "Failed to create route to start point", null)
                        }

                        override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                            Log.w("GeoJsonRoute", "⚠️ Route request cancelled")
                        }
                    }
                )
                return // Exit early - callback will handle the rest
            }

            // User is near start - use the planned route with mock response
            Log.i("GeoJsonRoute", "✅ Using planned route geometry for navigation")

            val mockDirectionsJson = createMockDirectionsResponse(
                geometryJson,
                waypoints,
                coordinatesList,  // Pass full coordinates list for creating detailed steps
                context
            )

            try {
                // Build route request URL with all waypoints for NavigationRoute.create()
                val waypointsCoords = waypoints.joinToString(";") { "${it.longitude()},${it.latitude()}" }
                val routeRequestUrl = "https://api.mapbox.com/directions/v5/mapbox/${navigationMode}/" +
                    waypointsCoords +
                    "?alternatives=false&geometries=geojson&steps=true&overview=full"

                // Create NavigationRoute from the mock JSON response
                val navigationRoutes = com.mapbox.navigation.base.route.NavigationRoute.create(
                    mockDirectionsJson,
                    routeRequestUrl
                )

                if (navigationRoutes.isEmpty()) {
                    result.error("ROUTE_CREATION_FAILED", "Failed to create route from GeoJSON", null)
                    return
                }

                this.currentRoutes = navigationRoutes
                this.plannedRoute = navigationRoutes

                PluginUtilities.sendEvent(
                    MapBoxEvents.ROUTE_BUILT,
                    "GeoJSON route loaded"
                )

                // Re-register observers to ensure fresh start (prevents duplicates)
                Log.d("Navigation", "📡 Re-registering observers for new navigation session")
                registerObservers()

                this.binding.navigationView.api.routeReplayEnabled(this.simulateRoute)
                this.binding.navigationView.api.startActiveGuidance(navigationRoutes)

                drawPlannedRoute()

                result.success(true)
                PluginUtilities.sendEvent(MapBoxEvents.NAVIGATION_RUNNING)

            } catch (e: Exception) {
                result.error("ROUTE_PARSE_ERROR", "Failed to parse route: ${e.message}", null)
            }

        } catch (e: Exception) {
            result.error("EXCEPTION", "Error processing GeoJSON: ${e.message}", null)
        }
    }

    // Extract waypoints from route coordinates to ensure route fidelity
    // CRITICAL: This uses ALL points from the GEOJSON to ensure exact route following
    private fun extractWaypointsFromRoute(coordinates: List<Point>): List<Point> {
        if (coordinates.size <= 2) return coordinates

        val maxWaypoints = 25 // Mapbox absolute maximum

        if (coordinates.size <= maxWaypoints) {
            // Use ALL points from the GEOJSON - this ensures perfect route fidelity
            Log.d("WaypointExtraction", "Using ALL ${coordinates.size} points as waypoints (≤${maxWaypoints})")
            return coordinates
        }

        // If GEOJSON has more than 25 points, distribute exactly 25 waypoints
        // evenly across the ENTIRE route to maintain maximum fidelity
        val waypoints = mutableListOf<Point>()
        waypoints.add(coordinates.first()) // Always include start

        // Calculate exact positions for intermediate waypoints
        val step = (coordinates.size - 1).toDouble() / (maxWaypoints - 1)

        for (i in 1 until maxWaypoints - 1) {
            val index = (i * step).toInt()
            waypoints.add(coordinates[index])
        }

        waypoints.add(coordinates.last()) // Always include end

        Log.d("WaypointExtraction", "Extracted ${waypoints.size} waypoints from ${coordinates.size} coordinates (GEOJSON has more than ${maxWaypoints} points)")
        Log.d("WaypointExtraction", "Route will follow these ${waypoints.size} key points exactly")
        return waypoints
    }

    private fun createMockDirectionsResponse(
        geometryJson: String,
        waypoints: List<Point>,
        allCoordinates: List<Point>,
        context: Context
    ): String {
        // Calculate total distance along the route
        var totalDistance = 0.0
        for (i in 0 until waypoints.size - 1) {
            totalDistance += calculateDistance(
                waypoints[i].latitude(), waypoints[i].longitude(),
                waypoints[i + 1].latitude(), waypoints[i + 1].longitude()
            )
        }
        val duration = totalDistance / 13.89 // Approximate duration (assuming 50 km/h average)

        // Create legs for each waypoint segment with DETAILED STEPS
        val legs = mutableListOf<String>()
        for (i in 0 until waypoints.size - 1) {
            val legDistance = calculateDistance(
                waypoints[i].latitude(), waypoints[i].longitude(),
                waypoints[i + 1].latitude(), waypoints[i + 1].longitude()
            )
            val legDuration = legDistance / 13.89

            // CRITICAL FIX: Create detailed steps for this leg using ALL coordinates
            // Find the segment of allCoordinates that corresponds to this leg
            val legSteps = createStepsForLeg(waypoints[i], waypoints[i + 1], allCoordinates)

            legs.add("""
                {
                    "distance": $legDistance,
                    "duration": $legDuration,
                    "summary": "",
                    "steps": [${legSteps.joinToString(",")}]
                }
            """.trimIndent())
        }

        // Create waypoints JSON
        val waypointsJson = waypoints.joinToString(",") { point ->
            """
                {
                    "location": [${point.longitude()}, ${point.latitude()}],
                    "name": ""
                }
            """.trimIndent()
        }

        // Create a minimal valid Directions API response with the EXACT geometry from GEOJSON
        // CRITICAL: The "geometry" field contains the EXACT route from the backend GEOJSON
        // This forces the Mapbox SDK to use this exact path instead of recalculating
        Log.d("MockDirections", "Creating mock response with ${waypoints.size} waypoints")
        Log.d("MockDirections", "Using EXACT GEOJSON geometry (not recalculated)")
        Log.d("MockDirections", "Total distance: ${totalDistance}m, Duration: ${duration}s")

        return """{
            "routes": [{
                "geometry": $geometryJson,
                "distance": $totalDistance,
                "duration": $duration,
                "weight": $duration,
                "weight_name": "routability",
                "legs": [${legs.joinToString(",")}],
                "voiceLocale": "$navigationLanguage"
            }],
            "waypoints": [$waypointsJson],
            "code": "Ok",
            "uuid": "${java.util.UUID.randomUUID()}"
        }"""
    }

    private fun parseGeoJsonLineString(geometry: String): List<Point> {
        // Parse GeoJSON LineString
        val jsonObject = JSONObject(geometry)
        val coordinates = jsonObject.getJSONArray("coordinates")
        val points = mutableListOf<Point>()

        for (i in 0 until coordinates.length()) {
            val coord = coordinates.getJSONArray(i)
            val lng = coord.getDouble(0)
            val lat = coord.getDouble(1)
            points.add(Point.fromLngLat(lng, lat))
        }

        return points
    }

    // Create detailed steps for a leg using ALL coordinates between waypoints
    // This ensures the navigation follows the ENTIRE green line, not just jumps between waypoints
    private fun createStepsForLeg(
        startWaypoint: Point,
        endWaypoint: Point,
        allCoordinates: List<Point>
    ): List<String> {
        Log.d("CreateSteps", "🔍 Looking for leg: [${startWaypoint.longitude()}, ${startWaypoint.latitude()}] -> [${endWaypoint.longitude()}, ${endWaypoint.latitude()}]")

        // Try to find exact or close matches first with reasonable tolerance
        var startIndex = allCoordinates.indexOfFirst {
            arePointsClose(it, startWaypoint, 0.0001) // ~10 meters tolerance
        }
        var endIndex = allCoordinates.indexOfFirst {
            arePointsClose(it, endWaypoint, 0.0001)
        }

        // If not found, find the nearest points
        if (startIndex < 0) {
            Log.w("CreateSteps", "⚠️ Start waypoint not found with tolerance, searching for nearest point...")
            startIndex = findNearestPointIndex(startWaypoint, allCoordinates)
            Log.d("CreateSteps", "📍 Nearest start point found at index $startIndex")
        }
        if (endIndex < 0) {
            Log.w("CreateSteps", "⚠️ End waypoint not found with tolerance, searching for nearest point...")
            endIndex = findNearestPointIndex(endWaypoint, allCoordinates)
            Log.d("CreateSteps", "📍 Nearest end point found at index $endIndex")
        }

        Log.d("CreateSteps", "📌 Found indices: start=$startIndex, end=$endIndex (total coords: ${allCoordinates.size})")

        if (startIndex < 0 || endIndex < 0 || startIndex >= endIndex) {
            Log.w("CreateSteps", "⚠️ Could not find valid indices for waypoints, creating simple step")
            Log.w("CreateSteps", "   startIndex=$startIndex, endIndex=$endIndex")
            // Fallback: create a single step for this leg
            val distance = calculateDistance(
                startWaypoint.latitude(), startWaypoint.longitude(),
                endWaypoint.latitude(), endWaypoint.longitude()
            )
            return listOf(createSimpleStep(startWaypoint, endWaypoint, distance))
        }

        // Extract all coordinates between start and end waypoints
        val legCoordinates = allCoordinates.subList(startIndex, endIndex + 1)

        Log.d("CreateSteps", "Creating steps for leg with ${legCoordinates.size} coordinate points")

        // Create steps - group coordinates into manageable chunks
        // Each step should represent a segment of the route
        val steps = mutableListOf<String>()
        val maxPointsPerStep = 50 // Reasonable number of points per step

        var i = 0
        while (i < legCoordinates.size - 1) {
            val stepEndIndex = minOf(i + maxPointsPerStep, legCoordinates.size - 1)
            val stepCoordinates = legCoordinates.subList(i, stepEndIndex + 1)

            // Calculate step distance
            var stepDistance = 0.0
            for (j in 0 until stepCoordinates.size - 1) {
                stepDistance += calculateDistance(
                    stepCoordinates[j].latitude(), stepCoordinates[j].longitude(),
                    stepCoordinates[j + 1].latitude(), stepCoordinates[j + 1].longitude()
                )
            }

            // Create step geometry
            val stepGeometry = createStepGeometry(stepCoordinates)
            val stepDuration = stepDistance / 13.89

            steps.add("""
                {
                    "distance": $stepDistance,
                    "duration": $stepDuration,
                    "geometry": $stepGeometry,
                    "name": "",
                    "mode": "driving",
                    "maneuver": {
                        "type": "turn",
                        "location": [${stepCoordinates.first().longitude()}, ${stepCoordinates.first().latitude()}]
                    }
                }
            """.trimIndent())

            i = stepEndIndex
        }

        Log.d("CreateSteps", "Created ${steps.size} detailed steps covering all ${legCoordinates.size} points")
        return steps
    }

    // Helper function to check if two points are close to each other
    private fun arePointsClose(p1: Point, p2: Point, tolerance: Double): Boolean {
        return abs(p1.latitude() - p2.latitude()) < tolerance &&
               abs(p1.longitude() - p2.longitude()) < tolerance
    }

    // Create a simple fallback step
    private fun createSimpleStep(start: Point, end: Point, distance: Double): String {
        val duration = distance / 13.89
        return """
            {
                "distance": $distance,
                "duration": $duration,
                "geometry": {"type":"LineString","coordinates":[[${start.longitude()},${start.latitude()}],[${end.longitude()},${end.latitude()}]]},
                "name": "",
                "mode": "driving",
                "maneuver": {
                    "type": "turn",
                    "location": [${start.longitude()}, ${start.latitude()}]
                }
            }
        """.trimIndent()
    }

    // Create GeoJSON geometry for a step
    private fun createStepGeometry(coordinates: List<Point>): String {
        val coords = coordinates.map { listOf(it.longitude(), it.latitude()) }
        return """{"type":"LineString","coordinates":${Gson().toJson(coords)}}"""
    }

    private fun clearRoute(methodCall: MethodCall, result: MethodChannel.Result) {
        Log.d("Navigation", "🧹 Clearing route...")

        // Unregister observers to prevent memory leaks and duplicates
        unregisterObservers()

        this.currentRoutes = null
        this.plannedRoute = null
        this.plannedRouteGeometry = null
        this.geoJsonRouteGeometry = null
        this.geoJsonRouteColor = null

        val navigation = MapboxNavigationApp.current()
        navigation?.stopTripSession()

        // Remove planned route layers from map
        removePlannedRouteLayers()

        Log.d("Navigation", "✅ Route cleared, observers unregistered, and all data cleaned")
        PluginUtilities.sendEvent(MapBoxEvents.NAVIGATION_CANCELLED)
    }

    private fun startFreeDrive() {
        this.binding.navigationView.api.startFreeDrive()
    }

    private fun startNavigation(methodCall: MethodCall, result: MethodChannel.Result) {
        val arguments = methodCall.arguments as? Map<*, *>
        if (arguments != null) {
            this.setOptions(arguments)
        }

        this.startNavigation()

        if (this.currentRoutes != null) {
            result.success(true)
        } else {
            result.success(false)
        }
    }

    private fun startNavigationWithGeoJson(methodCall: MethodCall, result: MethodChannel.Result) {
        val arguments = methodCall.arguments as? Map<*, *>
        if (arguments != null) {
            this.setOptions(arguments)

            // Extract GeoJSON route information
            val geoJsonRoute = arguments["geoJsonRoute"] as? Map<*, *>
            if (geoJsonRoute != null) {
                this.geoJsonRouteGeometry = geoJsonRoute["geometry"] as? String
                this.geoJsonRouteColor = geoJsonRoute["routeColor"] as? String ?: "#FFFF00"

                // Set planned route color to match GeoJSON route color
                this.plannedRouteColor = this.geoJsonRouteColor ?: "#FFFF00"

                // Parse GeoJSON geometry and create route from it
                if (this.geoJsonRouteGeometry != null) {
                    this.getRouteFromGeoJson(this.context, result)
                } else {
                    result.error("INVALID_GEOJSON", "GeoJSON geometry is null", null)
                }
            } else {
                result.error("INVALID_ARGS", "geoJsonRoute is required", null)
            }
        } else {
            result.error("INVALID_ARGS", "arguments are null", null)
        }
    }

    private fun finishNavigation(methodCall: MethodCall, result: MethodChannel.Result) {
        this.finishNavigation()

        if (this.currentRoutes != null) {
            result.success(true)
        } else {
            result.success(false)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startNavigation() {
        if (this.currentRoutes == null) {
            PluginUtilities.sendEvent(MapBoxEvents.NAVIGATION_CANCELLED)
            return
        }
        this.binding.navigationView.api.startActiveGuidance(this.currentRoutes!!)
        
        // Draw planned route after navigation starts
        drawPlannedRoute()
        
        PluginUtilities.sendEvent(MapBoxEvents.NAVIGATION_RUNNING)
    }

    private fun finishNavigation(isOffRouted: Boolean = false) {
        Log.d("Navigation", "🛑 Finishing navigation and cleaning up...")

        // Unregister observers to prevent memory leaks and duplicates
        unregisterObservers()

        MapboxNavigationApp.current()!!.stopTripSession()
        this.isNavigationCanceled = true

        // Clear all route data
        this.plannedRoute = null
        this.plannedRouteGeometry = null
        this.geoJsonRouteGeometry = null
        this.geoJsonRouteColor = null
        this.currentRoutes = null

        // Remove planned route layers from map
        removePlannedRouteLayers()

        Log.d("Navigation", "✅ Navigation finished, observers unregistered, and all data cleared")
        PluginUtilities.sendEvent(MapBoxEvents.NAVIGATION_CANCELLED)
    }

    // Helper function to remove planned route layers from the map
    private fun removePlannedRouteLayers() {
        try {
            // Try to access MapView and remove layers
            Handler(Looper.getMainLooper()).post {
                try {
                    this.binding.navigationView.registerMapObserver(object : MapViewObserver() {
                        override fun onAttached(mapView: com.mapbox.maps.MapView) {
                            mapView.getMapboxMap().getStyle { style: Style ->
                                try {
                                    if (style.styleLayerExists("planned-route-layer")) {
                                        style.removeStyleLayer("planned-route-layer")
                                        Log.d("Cleanup", "✅ Removed planned-route-layer")
                                    }
                                    if (style.styleLayerExists("planned-route-casing")) {
                                        style.removeStyleLayer("planned-route-casing")
                                        Log.d("Cleanup", "✅ Removed planned-route-casing")
                                    }
                                    if (style.styleSourceExists("planned-route-source")) {
                                        style.removeStyleSource("planned-route-source")
                                        Log.d("Cleanup", "✅ Removed planned-route-source")
                                    }
                                } catch (e: Exception) {
                                    Log.w("Cleanup", "Could not remove layers (may not exist): ${e.message}")
                                }
                            }
                        }
                        override fun onDetached(mapView: com.mapbox.maps.MapView) {}
                    })
                } catch (e: Exception) {
                    Log.w("Cleanup", "Could not access map to remove layers: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w("Cleanup", "Error removing planned route layers: ${e.message}")
        }
    }

    private fun setOptions(arguments: Map<*, *>) {
        val navMode = arguments["mode"] as? String
        if (navMode != null) {
            when (navMode) {
                "walking" -> this.navigationMode = DirectionsCriteria.PROFILE_WALKING
                "cycling" -> this.navigationMode = DirectionsCriteria.PROFILE_CYCLING
                "driving" -> this.navigationMode = DirectionsCriteria.PROFILE_DRIVING
            }
        }

        val simulated = arguments["simulateRoute"] as? Boolean
        if (simulated != null) {
            this.simulateRoute = simulated
        }

        val language = arguments["language"] as? String
        if (language != null) {
            this.navigationLanguage = language
        }

        val units = arguments["units"] as? String

        if (units != null) {
            if (units == "imperial") {
                this.navigationVoiceUnits = DirectionsCriteria.IMPERIAL
            } else if (units == "metric") {
                this.navigationVoiceUnits = DirectionsCriteria.METRIC
            }
        }

        this.mapStyleUrlDay = arguments["mapStyleUrlDay"] as? String
        this.mapStyleUrlNight = arguments["mapStyleUrlNight"] as? String

        //Set the style Uri
        if (this.mapStyleUrlDay == null) this.mapStyleUrlDay = Style.MAPBOX_STREETS
        if (this.mapStyleUrlNight == null) this.mapStyleUrlNight = Style.DARK

        this@TurnByTurn.binding.navigationView.customizeViewOptions {
            mapStyleUriDay = this@TurnByTurn.mapStyleUrlDay
            mapStyleUriNight = this@TurnByTurn.mapStyleUrlNight
        }           

        this.initialLatitude = arguments["initialLatitude"] as? Double
        this.initialLongitude = arguments["initialLongitude"] as? Double

        val zm = arguments["zoom"] as? Double
        if (zm != null) {
            this.zoom = zm
        }

        val br = arguments["bearing"] as? Double
        if (br != null) {
            this.bearing = br
        }

        val tt = arguments["tilt"] as? Double
        if (tt != null) {
            this.tilt = tt
        }

        val optim = arguments["isOptimized"] as? Boolean
        if (optim != null) {
            this.isOptimized = optim
        }

        val anim = arguments["animateBuildRoute"] as? Boolean
        if (anim != null) {
            this.animateBuildRoute = anim
        }

        val altRoute = arguments["alternatives"] as? Boolean
        if (altRoute != null) {
            this.alternatives = altRoute
        }
        
        // Force alternatives to false when showing planned route to avoid gray routes
        if (showPlannedRoute) {
            this.alternatives = false
        }

        val voiceEnabled = arguments["voiceInstructionsEnabled"] as? Boolean
        if (voiceEnabled != null) {
            this.voiceInstructionsEnabled = voiceEnabled
        }

        val bannerEnabled = arguments["bannerInstructionsEnabled"] as? Boolean
        if (bannerEnabled != null) {
            this.bannerInstructionsEnabled = bannerEnabled
        }

        val longPress = arguments["longPressDestinationEnabled"] as? Boolean
        if (longPress != null) {
            this.longPressDestinationEnabled = longPress
        }

        val onMapTap = arguments["enableOnMapTapCallback"] as? Boolean
        if (onMapTap != null) {
            this.enableOnMapTapCallback = onMapTap
        }
        
        val showPlanned = arguments["showPlannedRoute"] as? Boolean
        if (showPlanned != null) {
            this.showPlannedRoute = showPlanned
        }
        
        val plannedColor = arguments["plannedRouteColor"] as? String
        if (plannedColor != null) {
            this.plannedRouteColor = plannedColor
        }
        
        val autoRecalc = arguments["autoRecalculateOnDeviation"] as? Boolean
        if (autoRecalc != null) {
            this.autoRecalculateOnDeviation = autoRecalc
        }
    }

    open fun registerObservers() {
        // register event listeners
        MapboxNavigationApp.current()?.registerBannerInstructionsObserver(this.bannerInstructionObserver)
        MapboxNavigationApp.current()?.registerVoiceInstructionsObserver(this.voiceInstructionObserver)
        MapboxNavigationApp.current()?.registerOffRouteObserver(this.offRouteObserver)
        MapboxNavigationApp.current()?.registerRoutesObserver(this.routesObserver)
        MapboxNavigationApp.current()?.registerLocationObserver(this.locationObserver)
        MapboxNavigationApp.current()?.registerRouteProgressObserver(this.routeProgressObserver)
        MapboxNavigationApp.current()?.registerArrivalObserver(this.arrivalObserver)
    }

    open fun unregisterObservers() {
        // unregister event listeners to prevent leaks or unnecessary resource consumption
        MapboxNavigationApp.current()?.unregisterBannerInstructionsObserver(this.bannerInstructionObserver)
        MapboxNavigationApp.current()?.unregisterVoiceInstructionsObserver(this.voiceInstructionObserver)
        MapboxNavigationApp.current()?.unregisterOffRouteObserver(this.offRouteObserver)
        MapboxNavigationApp.current()?.unregisterRoutesObserver(this.routesObserver)
        MapboxNavigationApp.current()?.unregisterLocationObserver(this.locationObserver)
        MapboxNavigationApp.current()?.unregisterRouteProgressObserver(this.routeProgressObserver)
        MapboxNavigationApp.current()?.unregisterArrivalObserver(this.arrivalObserver)
    }

    // Flutter stream listener delegate methods
    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        FlutterMapboxNavigationPlugin.eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        FlutterMapboxNavigationPlugin.eventSink = null
    }

    private val context: Context = ctx
    val activity: Activity = act
    private val token: String = accessToken
    open var methodChannel: MethodChannel? = null
    open var eventChannel: EventChannel? = null
    private var lastLocation: Location? = null

    /**
     * Helper class that keeps added waypoints and transforms them to the [RouteOptions] params.
     */
    private val addedWaypoints = WaypointSet()

    // Config
    private var initialLatitude: Double? = null
    private var initialLongitude: Double? = null

    // val wayPoints: MutableList<Point> = mutableListOf()
    private var navigationMode = DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
    var simulateRoute = false
    private var mapStyleUrlDay: String? = null
    private var mapStyleUrlNight: String? = null
    private var navigationLanguage = "pt-BR"
    private var navigationVoiceUnits = DirectionsCriteria.METRIC
    private var zoom = 15.0
    private var bearing = 0.0
    private var tilt = 0.0
    private var distanceRemaining: Float? = null
    private var durationRemaining: Double? = null

    private var alternatives = true

    var allowsUTurnAtWayPoints = false
    var enableRefresh = false
    private var voiceInstructionsEnabled = true
    private var bannerInstructionsEnabled = true
    private var longPressDestinationEnabled = true
    private var enableOnMapTapCallback = false
    private var animateBuildRoute = true
    private var isOptimized = false
    
    // Planned route support
    private var showPlannedRoute = true
    private var plannedRouteColor = "#32CD32" // Verde suave (LimeGreen)
    private var autoRecalculateOnDeviation = true
    private var plannedRoute: List<NavigationRoute>? = null
    private var plannedRouteGeometry: String? = null

    // GeoJSON route support
    private var geoJsonRouteGeometry: String? = null
    private var geoJsonRouteColor: String? = null

    private var currentRoutes: List<NavigationRoute>? = null
    private var isNavigationCanceled = false

    // Off-route detection for planned route (green line)
    private var offRoutePlannedThreshold = 80.0 // meters
    private var lastOffRouteCheckTime = 0L
    private var offRouteCheckIntervalMs = 3000L // Check every 3 seconds
    private var isOffPlannedRoute = false
    private var lastDistanceFromPlannedRoute = 0.0

    // Route line APIs for planned route
    private var plannedRouteLineApi: MapboxRouteLineApi? = null
    private var plannedRouteLineView: MapboxRouteLineView? = null
    
    /**
     * Bindings to the example layout.
     */
    open val binding: NavigationActivityBinding = bind

    /**
     * Gets notified with location updates.
     *
     * Exposes raw updates coming directly from the location services
     * and the updates enhanced by the Navigation SDK (cleaned up and matched to the road).
     */
    private val locationObserver = object : LocationObserver {
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            this@TurnByTurn.lastLocation = locationMatcherResult.enhancedLocation

            // Check distance from planned route (green line) if GeoJSON route is active
            checkDistanceFromPlannedRoute(locationMatcherResult.enhancedLocation)
        }

        override fun onNewRawLocation(rawLocation: Location) {
            // no impl
        }
    }

    /**
     * Checks if user arrived at the route start point.
     * If yes, switches from "route to start" to the planned GeoJSON route.
     */
    private fun checkArrivalAtRouteStart(currentLocation: Location) {
        if (geoJsonRouteGeometry == null) return

        try {
            // Parse the GeoJSON to get the start point
            val routeCoordinates = try {
                parseGeoJsonLineString(geoJsonRouteGeometry!!)
            } catch (e: Exception) {
                try {
                    com.mapbox.geojson.utils.PolylineUtils.decode(geoJsonRouteGeometry!!, 5)
                        .map { Point.fromLngLat(it.longitude(), it.latitude()) }
                } catch (e2: Exception) {
                    return
                }
            }

            if (routeCoordinates.isEmpty()) return

            val routeStartPoint = routeCoordinates.first()
            val distanceToStart = calculateDistance(
                currentLocation.latitude, currentLocation.longitude,
                routeStartPoint.latitude(), routeStartPoint.longitude()
            )

            // Check if user arrived at start (within 50 meters)
            if (distanceToStart < 50.0) {
                // Check if we're currently on "route to start" (currentRoutes has only 2 waypoints)
                val currentRoute = this.currentRoutes?.firstOrNull()
                if (currentRoute != null) {
                    val currentWaypoints = currentRoute.directionsRoute.waypoints()
                    if (currentWaypoints?.size == 2) {
                        // We're on "route to start" - need to switch to planned route
                        Log.i("RouteStart", "🎯 ARRIVED AT ROUTE START!")
                        Log.i("RouteStart", "📍 Distance to start: ${distanceToStart.toInt()}m")
                        Log.i("RouteStart", "🔄 Switching to PLANNED ROUTE...")

                        // Switch to planned route
                        switchToPlannedRoute()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("RouteStart", "Error checking arrival at route start: ${e.message}")
        }
    }

    /**
     * Switches from "route to start" to the planned GeoJSON route.
     */
    private fun switchToPlannedRoute() {
        try {
            if (geoJsonRouteGeometry == null) {
                Log.e("RouteStart", "Cannot switch - no planned route geometry")
                return
            }

            Log.d("RouteStart", "🔄 Creating planned route for navigation...")

            // Parse coordinates
            val routeCoordinates = try {
                parseGeoJsonLineString(geoJsonRouteGeometry!!)
            } catch (e: Exception) {
                try {
                    com.mapbox.geojson.utils.PolylineUtils.decode(geoJsonRouteGeometry!!, 5)
                        .map { Point.fromLngLat(it.longitude(), it.latitude()) }
                } catch (e2: Exception) {
                    Log.e("RouteStart", "Failed to parse geometry: ${e2.message}")
                    return
                }
            }

            // Convert to GeoJSON if needed
            val geometryJson = if (geoJsonRouteGeometry!!.contains("{")) {
                geoJsonRouteGeometry!!
            } else {
                val coords = routeCoordinates.map { listOf(it.longitude(), it.latitude()) }
                """{"type":"LineString","coordinates":${Gson().toJson(coords)}}"""
            }

            // Extract waypoints from planned route
            val waypoints = extractWaypointsFromRoute(routeCoordinates)

            // Create mock response
            val mockDirectionsJson = createMockDirectionsResponse(
                geometryJson,
                waypoints,
                routeCoordinates,
                this.context
            )

            // Build route request URL
            val waypointsCoords = waypoints.joinToString(";") { "${it.longitude()},${it.latitude()}" }
            val routeRequestUrl = "https://api.mapbox.com/directions/v5/mapbox/${navigationMode}/" +
                waypointsCoords +
                "?alternatives=false&geometries=geojson&steps=true&overview=full"

            // Create NavigationRoute from mock response
            val navigationRoutes = com.mapbox.navigation.base.route.NavigationRoute.create(
                mockDirectionsJson,
                routeRequestUrl
            )

            if (navigationRoutes.isEmpty()) {
                Log.e("RouteStart", "Failed to create planned route")
                return
            }

            // Switch routes
            this.currentRoutes = navigationRoutes
            this.plannedRoute = navigationRoutes

            // Update navigation
            this.binding.navigationView.api.startActiveGuidance(navigationRoutes)

            Log.i("RouteStart", "✅ SWITCHED TO PLANNED ROUTE!")
            Log.i("RouteStart", "🔵 Blue line now follows GREEN line")

            PluginUtilities.sendEvent(MapBoxEvents.REROUTE_ALONG)

        } catch (e: Exception) {
            Log.e("RouteStart", "Error switching to planned route: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Checks if the current location is within the threshold distance of the planned route (green line).
     * If the distance exceeds the threshold (default 80m), sends an off-route event.
     * Also checks if user arrived at the route start point to switch to planned route.
     */
    private fun checkDistanceFromPlannedRoute(currentLocation: Location) {
        // Only check if we have a GeoJSON route active
        if (geoJsonRouteGeometry == null) {
            return
        }

        // Throttle checks to avoid excessive calculations (every 3 seconds)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastOffRouteCheckTime < offRouteCheckIntervalMs) {
            return
        }
        lastOffRouteCheckTime = currentTime

        // Check if user arrived at route start and should switch to planned route
        checkArrivalAtRouteStart(currentLocation)

        try {
            // Parse the GeoJSON geometry to get all coordinates
            val routeCoordinates = try {
                parseGeoJsonLineString(geoJsonRouteGeometry!!)
            } catch (e: Exception) {
                try {
                    com.mapbox.geojson.utils.PolylineUtils.decode(geoJsonRouteGeometry!!, 5)
                        .map { Point.fromLngLat(it.longitude(), it.latitude()) }
                } catch (e2: Exception) {
                    Log.e("OffRoutePlanned", "Failed to parse geometry: ${e2.message}")
                    return
                }
            }

            if (routeCoordinates.size < 2) {
                Log.w("OffRoutePlanned", "Route has less than 2 points, skipping check")
                return
            }

            // Find the nearest point on the planned route
            val currentPoint = Point.fromLngLat(currentLocation.longitude, currentLocation.latitude)
            val nearestPoint = findNearestPointOnRoute(currentPoint, routeCoordinates)

            // Calculate distance from current location to nearest point on route
            val distance = calculateDistance(
                currentPoint.latitude(), currentPoint.longitude(),
                nearestPoint.latitude(), nearestPoint.longitude()
            )

            lastDistanceFromPlannedRoute = distance

            // Check if we crossed the threshold
            val wasOffRoute = isOffPlannedRoute
            isOffPlannedRoute = distance > offRoutePlannedThreshold

            // Log the check
            if (isOffPlannedRoute) {
                Log.w("OffRoutePlanned", "🚨 OFF PLANNED ROUTE: ${distance.toInt()}m from green line (threshold: ${offRoutePlannedThreshold.toInt()}m)")
            } else {
                Log.d("OffRoutePlanned", "✅ ON PLANNED ROUTE: ${distance.toInt()}m from green line")
            }

            // Send event when state changes (entering or leaving off-route state)
            if (isOffPlannedRoute && !wasOffRoute) {
                Log.e("OffRoutePlanned", "❌ ENTERED OFF-ROUTE STATE - Distance: ${distance.toInt()}m")
                PluginUtilities.sendEvent(MapBoxEvents.USER_OFF_ROUTE)

                // You can also send a custom event with distance data
                val offRouteData = mapOf(
                    "distance" to distance,
                    "threshold" to offRoutePlannedThreshold,
                    "latitude" to currentLocation.latitude,
                    "longitude" to currentLocation.longitude
                )
                Log.d("OffRoutePlanned", "Off-route data: $offRouteData")
            } else if (!isOffPlannedRoute && wasOffRoute) {
                Log.i("OffRoutePlanned", "✅ RETURNED TO PLANNED ROUTE - Distance: ${distance.toInt()}m")
            }

        } catch (e: Exception) {
            Log.e("OffRoutePlanned", "Error checking distance from planned route: ${e.message}")
            e.printStackTrace()
        }
    }

    private val bannerInstructionObserver = BannerInstructionsObserver { bannerInstructions ->
        PluginUtilities.sendEvent(MapBoxEvents.BANNER_INSTRUCTION, bannerInstructions.primary().text())
    }

    private val voiceInstructionObserver = VoiceInstructionsObserver { voiceInstructions ->
        PluginUtilities.sendEvent(MapBoxEvents.SPEECH_ANNOUNCEMENT, voiceInstructions.announcement().toString())
    }

    private val offRouteObserver = OffRouteObserver { offRoute ->
        if (offRoute) {
            Log.d("OffRoute", "🚨 User went OFF ROUTE")
            Log.d("OffRoute", "Position: ${lastLocation?.latitude}, ${lastLocation?.longitude}")
            Log.d("OffRoute", "autoRecalculate: $autoRecalculateOnDeviation, hasGeoJson: ${geoJsonRouteGeometry != null}, hasPlannedRoute: ${plannedRoute != null}")

            PluginUtilities.sendEvent(MapBoxEvents.USER_OFF_ROUTE)

            // CRITICAL: When off-route with GeoJSON, ALWAYS recalculate to force back to green line
            if (geoJsonRouteGeometry != null) {
                Log.d("OffRoute", "✅ Forcing recalculation to snap back to GREEN LINE (GEOJSON route)")
                recalculateToNearestPointOnGeoJsonRoute()
            } else if (autoRecalculateOnDeviation && plannedRoute != null) {
                Log.d("OffRoute", "Using standard recalculation (no GeoJSON)")
                recalculateToNearestPointOnGeoJsonRoute()
            } else {
                Log.w("OffRoute", "⚠️ No recalculation - blue route will use Mapbox API (may not follow green line)")
            }
        } else {
            Log.d("OffRoute", "✅ User is ON ROUTE (following planned path)")
        }
    }

    private fun recalculateToNearestPointOnGeoJsonRoute() {
        try {
            Log.d("OffRoute", "🔄 Starting recalculation to snap back to GREEN LINE")

            val currentLocation = this.lastLocation
            if (currentLocation == null) {
                Log.e("OffRoute", "❌ Cannot recalculate - no current location")
                return
            }

            val geometry = this.geoJsonRouteGeometry
            if (geometry == null) {
                Log.e("OffRoute", "❌ Cannot recalculate - no GeoJSON geometry")
                return
            }

            // Parse the route geometry to get all coordinates
            val coordinatesList = try {
                parseGeoJsonLineString(geometry)
            } catch (e: Exception) {
                try {
                    com.mapbox.geojson.utils.PolylineUtils.decode(geometry, 5)
                        .map { Point.fromLngLat(it.longitude(), it.latitude()) }
                } catch (e2: Exception) {
                    Log.e("OffRoute", "❌ Failed to parse geometry for recalculation: ${e2.message}")
                    return
                }
            }

            Log.d("OffRoute", "📍 Original GeoJSON has ${coordinatesList.size} coordinate points")

            // Find the nearest point on the route
            val currentPoint = Point.fromLngLat(currentLocation.longitude, currentLocation.latitude)
            val nearestPoint = findNearestPointOnRoute(currentPoint, coordinatesList)

            // Get remaining coordinates from nearest point to end
            val nearestIndex = coordinatesList.indexOf(nearestPoint)
            val remainingCoordinates = if (nearestIndex >= 0) {
                coordinatesList.subList(nearestIndex, coordinatesList.size)
            } else {
                coordinatesList
            }

            Log.d("OffRoute", "📌 Nearest point index: $nearestIndex/${coordinatesList.size}")
            Log.d("OffRoute", "📏 Remaining coordinates: ${remainingCoordinates.size} points")

            if (remainingCoordinates.size < 2) {
                Log.w("OffRoute", "⚠️ Not enough coordinates to recalculate route")
                return
            }

            // CRITICAL: Do NOT use requestRoutes() which recalculates via API
            // Instead, create a mock Directions API response with the exact remaining GEOJSON geometry
            // This ensures the route stays FIXED on the original GEOJSON path

            // Create GeoJSON LineString for remaining route
            val remainingGeometryCoords = remainingCoordinates.map {
                listOf(it.longitude(), it.latitude())
            }
            val remainingGeometryJson = """{"type":"LineString","coordinates":${Gson().toJson(remainingGeometryCoords)}}"""

            // Extract waypoints from remaining coordinates for route fidelity
            val remainingWaypoints = extractWaypointsFromRoute(remainingCoordinates)
            Log.d("OffRoute", "🎯 Extracted ${remainingWaypoints.size} waypoints from ${remainingCoordinates.size} remaining coordinates")
            Log.d("OffRoute", "🎯 Waypoints ensure EXACT following of GREEN LINE geometry")

            // Create mock Directions API response with remaining GEOJSON geometry
            val mockDirectionsJson = createMockDirectionsResponse(
                remainingGeometryJson,
                remainingWaypoints,
                remainingCoordinates,  // Pass full remaining coordinates for detailed steps
                this.context
            )

            // Build route request URL for NavigationRoute.create() with all waypoints
            val waypointsCoords = remainingWaypoints.joinToString(";") {
                "${it.longitude()},${it.latitude()}"
            }
            val routeRequestUrl = "https://api.mapbox.com/directions/v5/mapbox/${navigationMode}/" +
                waypointsCoords +
                "?alternatives=false&geometries=geojson&steps=true&overview=full"

            Log.d("OffRoute", "🔗 Creating navigation route with ${remainingWaypoints.size} waypoints")

            // Create NavigationRoute from mock response (NO API CALL - uses exact geometry)
            val navigationRoutes = com.mapbox.navigation.base.route.NavigationRoute.create(
                mockDirectionsJson,
                routeRequestUrl
            )

            if (navigationRoutes.isNotEmpty()) {
                this.currentRoutes = navigationRoutes
                this.binding.navigationView.api.startActiveGuidance(navigationRoutes)
                PluginUtilities.sendEvent(MapBoxEvents.REROUTE_ALONG)

                // Redraw the planned route to ensure it stays visible
                drawPlannedRoute()

                Log.d("OffRoute", "✅ Successfully snapped back to GEOJSON route!")
                Log.d("OffRoute", "✅ Blue navigation route now leads back to GREEN LINE with ${remainingWaypoints.size} waypoints")
            } else {
                Log.e("OffRoute", "❌ Failed to create route from remaining GEOJSON geometry")
            }

        } catch (e: Exception) {
            Log.e("OffRoute", "Error in recalculateToNearestPointOnGeoJsonRoute: ${e.message}")
        }
    }

    private fun findNearestPointOnRoute(currentPoint: Point, routePoints: List<Point>): Point {
        var nearestPoint = routePoints.first()
        var minDistance = Double.MAX_VALUE

        for (point in routePoints) {
            val distance = calculateDistance(
                currentPoint.latitude(), currentPoint.longitude(),
                point.latitude(), point.longitude()
            )
            if (distance < minDistance) {
                minDistance = distance
                nearestPoint = point
            }
        }

        return nearestPoint
    }

    private fun findNearestPointIndex(targetPoint: Point, routePoints: List<Point>): Int {
        var nearestIndex = 0
        var minDistance = Double.MAX_VALUE

        for ((index, point) in routePoints.withIndex()) {
            val distance = calculateDistance(
                targetPoint.latitude(), targetPoint.longitude(),
                point.latitude(), point.longitude()
            )
            if (distance < minDistance) {
                minDistance = distance
                nearestIndex = index
            }
        }

        Log.d("FindNearest", "Found nearest point at index $nearestIndex with distance ${minDistance}m")
        return nearestIndex
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Haversine formula to calculate distance between two points
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            PluginUtilities.sendEvent(MapBoxEvents.REROUTE_ALONG)

            // Update current routes - this is the blue navigation route
            currentRoutes = routeUpdateResult.navigationRoutes

            // The planned route (yellow guide) stays fixed and unchanged
            // Redraw the planned route to ensure it stays visible on top
            drawPlannedRoute()
        }
    }

    /**
     * Gets notified with progress along the currently active route.
     */
    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        // update flutter events
        if (!this.isNavigationCanceled) {
            try {

                this.distanceRemaining = routeProgress.distanceRemaining
                this.durationRemaining = routeProgress.durationRemaining

                val progressEvent = MapBoxRouteProgressEvent(routeProgress)
                PluginUtilities.sendEvent(progressEvent)
            } catch (_: java.lang.Exception) {
                // handle this error
            }
        }
    }

    private val arrivalObserver: ArrivalObserver = object : ArrivalObserver {
        override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
            PluginUtilities.sendEvent(MapBoxEvents.ON_ARRIVAL)
        }

        override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) {
            // not impl
        }

        override fun onWaypointArrival(routeProgress: RouteProgress) {
            // not impl
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.d("Embedded", "onActivityCreated not implemented")
    }

    override fun onActivityStarted(activity: Activity) {
        Log.d("Embedded", "onActivityStarted not implemented")
    }

    override fun onActivityResumed(activity: Activity) {
        Log.d("Embedded", "onActivityResumed not implemented")
    }

    override fun onActivityPaused(activity: Activity) {
        Log.d("Embedded", "onActivityPaused not implemented")
    }

    override fun onActivityStopped(activity: Activity) {
        Log.d("Embedded", "onActivityStopped not implemented")
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Log.d("Embedded", "onActivitySaveInstanceState not implemented")
    }

    override fun onActivityDestroyed(activity: Activity) {
        Log.d("Embedded", "onActivityDestroyed not implemented")
    }

    private fun initializePlannedRouteLine() {
        // Configure planned route line with yellow color
        val routeLineColorResources = RouteLineColorResources.Builder()
            .routeDefaultColor(android.graphics.Color.parseColor(plannedRouteColor))
            .routeCasingColor(android.graphics.Color.parseColor(plannedRouteColor))
            .routeLowCongestionColor(android.graphics.Color.parseColor(plannedRouteColor))
            .routeModerateCongestionColor(android.graphics.Color.parseColor(plannedRouteColor))
            .routeHeavyCongestionColor(android.graphics.Color.parseColor(plannedRouteColor))
            .routeSevereCongestionColor(android.graphics.Color.parseColor(plannedRouteColor))
            .build()

        val routeLineResources = RouteLineResources.Builder()
            .routeLineColorResources(routeLineColorResources)
            .build()

        val options = MapboxRouteLineOptions.Builder(this.context)
            .withRouteLineResources(routeLineResources)
            .withRouteLineBelowLayerId("road-label") // Put below road labels
            .build()

        plannedRouteLineApi = MapboxRouteLineApi(options)
        plannedRouteLineView = MapboxRouteLineView(options)
    }

    // Helper function to convert JSON to Value recursively
    private fun jsonToValue(json: String): com.mapbox.bindgen.Value {
        val jsonObj = JSONObject(json)
        return jsonObjectToValue(jsonObj)
    }

    private fun jsonObjectToValue(jsonObject: JSONObject): com.mapbox.bindgen.Value {
        val map = HashMap<String, com.mapbox.bindgen.Value>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObject.get(key)
            map[key] = when (value) {
                is JSONObject -> jsonObjectToValue(value)
                is org.json.JSONArray -> jsonArrayToValue(value)
                is String -> com.mapbox.bindgen.Value.valueOf(value)
                is Int -> com.mapbox.bindgen.Value.valueOf(value.toLong())
                is Long -> com.mapbox.bindgen.Value.valueOf(value)
                is Double -> com.mapbox.bindgen.Value.valueOf(value)
                is Boolean -> com.mapbox.bindgen.Value.valueOf(value)
                JSONObject.NULL -> com.mapbox.bindgen.Value.nullValue()
                else -> com.mapbox.bindgen.Value.valueOf(value.toString())
            }
        }
        return com.mapbox.bindgen.Value.valueOf(map)
    }

    private fun jsonArrayToValue(jsonArray: org.json.JSONArray): com.mapbox.bindgen.Value {
        val list = ArrayList<com.mapbox.bindgen.Value>()
        for (i in 0 until jsonArray.length()) {
            val value = jsonArray.get(i)
            list.add(when (value) {
                is JSONObject -> jsonObjectToValue(value)
                is org.json.JSONArray -> jsonArrayToValue(value)
                is String -> com.mapbox.bindgen.Value.valueOf(value)
                is Int -> com.mapbox.bindgen.Value.valueOf(value.toLong())
                is Long -> com.mapbox.bindgen.Value.valueOf(value)
                is Double -> com.mapbox.bindgen.Value.valueOf(value)
                is Boolean -> com.mapbox.bindgen.Value.valueOf(value)
                JSONObject.NULL -> com.mapbox.bindgen.Value.nullValue()
                else -> com.mapbox.bindgen.Value.valueOf(value.toString())
            })
        }
        return com.mapbox.bindgen.Value.valueOf(list)
    }

    private fun drawPlannedRoute() {
        if (!showPlannedRoute || plannedRouteGeometry == null) {
            Log.d("PlannedRoute", "Skipping draw: showPlannedRoute=$showPlannedRoute, geometry=${plannedRouteGeometry != null}")
            return
        }

        Log.d("PlannedRoute", "Starting to draw planned route with color: $plannedRouteColor")

        // Use a Handler with multiple retries to ensure MapView is ready
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                // Register MapObserver to access MapView when it's attached
                this.binding.navigationView.registerMapObserver(object : MapViewObserver() {
                    override fun onAttached(mapView: com.mapbox.maps.MapView) {
                        // Use another delay to ensure style is loaded
                        Handler(Looper.getMainLooper()).postDelayed({
                            mapView.getMapboxMap().getStyle { style: Style ->
                                try {
                                    val geometry = plannedRouteGeometry
                                    if (geometry == null) {
                                        Log.e("PlannedRoute", "Geometry is null in callback")
                                        return@getStyle
                                    }

                                    Log.d("PlannedRoute", "Raw geometry type: ${if (geometry.startsWith("{")) "GeoJSON" else "Polyline"}")
                                    Log.d("PlannedRoute", "Drawing route with geometry: ${geometry.take(100)}...")

                                    // Convert polyline to GeoJSON if needed
                                    val geoJsonGeometry = if (geometry.startsWith("{")) {
                                        // Already in GeoJSON format
                                        geometry
                                    } else {
                                        // It's a polyline6 encoded string - decode it
                                        try {
                                            val coordinates = com.mapbox.geojson.utils.PolylineUtils.decode(geometry, 6)
                                            val coordsList = coordinates.map { listOf(it.longitude(), it.latitude()) }
                                            """{"type":"LineString","coordinates":${Gson().toJson(coordsList)}}"""
                                        } catch (e: Exception) {
                                            Log.e("PlannedRoute", "Failed to decode polyline: ${e.message}")
                                            return@getStyle
                                        }
                                    }

                                    Log.d("PlannedRoute", "GeoJSON geometry: ${geoJsonGeometry.take(200)}...")

                                    // Remove existing layers/sources if they exist
                                    if (style.styleLayerExists("planned-route-layer")) {
                                        style.removeStyleLayer("planned-route-layer")
                                        Log.d("PlannedRoute", "Removed existing main layer")
                                    }
                                    if (style.styleLayerExists("planned-route-casing")) {
                                        style.removeStyleLayer("planned-route-casing")
                                        Log.d("PlannedRoute", "Removed existing casing layer")
                                    }
                                    if (style.styleSourceExists("planned-route-source")) {
                                        style.removeStyleSource("planned-route-source")
                                        Log.d("PlannedRoute", "Removed existing source")
                                    }

                                    // Create GeoJSON source JSON
                                    val sourceJson = """
                                        {
                                            "type": "geojson",
                                            "data": $geoJsonGeometry
                                        }
                                    """.trimIndent()

                                    // Convert JSON to Value
                                    val sourceValue = jsonToValue(sourceJson)

                                    // Add GeoJSON source
                                    style.addStyleSource("planned-route-source", sourceValue)
                                    Log.d("PlannedRoute", "Added source successfully")

                                    // Create CASING layer (darker green border) - same style as blue route
                                    val casingLayerJson = """
                                        {
                                            "id": "planned-route-casing",
                                            "type": "line",
                                            "source": "planned-route-source",
                                            "paint": {
                                                "line-color": "#228B22",
                                                "line-width": 10,
                                                "line-opacity": 0.8
                                            },
                                            "layout": {
                                                "line-cap": "round",
                                                "line-join": "round"
                                            }
                                        }
                                    """.trimIndent()

                                    val casingValue = jsonToValue(casingLayerJson)

                                    // CRITICAL: Add layers BELOW navigation elements to keep location arrow visible
                                    // Try to find a layer to place our route below (location indicator, puck, etc)
                                    var layerPosition: LayerPosition? = null

                                    // Common Mapbox Navigation SDK layer IDs for location/puck
                                    val topLayerIds = listOf(
                                        "mapbox-location-indicator-layer",
                                        "mapbox-location-puck-layer",
                                        "mapbox-user-location",
                                        "com.mapbox.maps.plugin.locationcomponent.location_indicator_layer"
                                    )

                                    // Find the first existing top layer
                                    for (layerId in topLayerIds) {
                                        if (style.styleLayerExists(layerId)) {
                                            layerPosition = LayerPosition(null, layerId, null)
                                            Log.d("PlannedRoute", "📍 Placing route BELOW layer: $layerId")
                                            break
                                        }
                                    }

                                    if (layerPosition == null) {
                                        Log.w("PlannedRoute", "⚠️ No location layer found, adding below road-label as fallback")
                                        // Fallback: add below road labels
                                        if (style.styleLayerExists("road-label")) {
                                            layerPosition = LayerPosition(null, "road-label", null)
                                        }
                                    }

                                    style.addStyleLayer(casingValue, layerPosition)
                                    Log.d("PlannedRoute", "Added casing layer (dark border)")

                                    // Create MAIN line layer (light green) on top of casing
                                    val layerJson = """
                                        {
                                            "id": "planned-route-layer",
                                            "type": "line",
                                            "source": "planned-route-source",
                                            "paint": {
                                                "line-color": "$plannedRouteColor",
                                                "line-width": 7,
                                                "line-opacity": 0.95
                                            },
                                            "layout": {
                                                "line-cap": "round",
                                                "line-join": "round"
                                            }
                                        }
                                    """.trimIndent()

                                    // Convert JSON to Value
                                    val layerValue = jsonToValue(layerJson)

                                    // Add main line layer on top of casing, but still below location indicator
                                    style.addStyleLayer(layerValue, layerPosition)
                                    Log.d("PlannedRoute", "✅ Planned route (GREEN LINE with border) drawn successfully!")
                                    Log.d("PlannedRoute", "✅ Location arrow will remain visible on top")

                                } catch (e: Exception) {
                                    Log.e("PlannedRoute", "❌ Error drawing planned route: ${e.message}", e)
                                    e.printStackTrace()
                                }
                            }
                        }, 500) // Additional 500ms delay after MapView is attached
                    }

                    override fun onDetached(mapView: com.mapbox.maps.MapView) {
                        // Cleanup if needed
                    }
                })
            } catch (e: Exception) {
                Log.e("PlannedRoute", "❌ Error registering MapObserver: ${e.message}", e)
                e.printStackTrace()
            }
        }, 1500) // 1.5 second initial delay to ensure NavigationView is initialized
    }

    // Simplified approach - let MapBox SDK handle route management
    // The yellow planned route guide layer will be rendered separately
    // The blue navigation route will recalculate automatically when needed
}
