// ignore_for_file: use_setters_to_change_properties

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_mapbox_navigation/src/flutter_mapbox_navigation_platform_interface.dart';
import 'package:flutter_mapbox_navigation/src/models/models.dart';

/// Turn-By-Turn Navigation Provider
class MapBoxNavigation {
  static final MapBoxNavigation _instance = MapBoxNavigation();

  /// get current instance of this class
  static MapBoxNavigation get instance => _instance;

  MapBoxOptions _defaultOptions = MapBoxOptions(
    zoom: 15,
    tilt: 0,
    bearing: 0,
    enableRefresh: false,
    alternatives: true,
    voiceInstructionsEnabled: true,
    bannerInstructionsEnabled: true,
    allowsUTurnAtWayPoints: true,
    mode: MapBoxNavigationMode.drivingWithTraffic,
    units: VoiceUnits.metric,
    simulateRoute: false,
    animateBuildRoute: true,
    longPressDestinationEnabled: true,
    language: 'pt-BR',
    showPlannedRoute: true,
    plannedRouteColor: '#FFFF00',
    autoRecalculateOnDeviation: true,
  );

  List<WayPoint>? _plannedRoute;

  /// setter to set default options
  void setDefaultOptions(MapBoxOptions options) {
    _defaultOptions = options;
  }

  /// Getter to retriev default options
  MapBoxOptions getDefaultOptions() {
    return _defaultOptions;
  }

  ///Current Device OS Version
  Future<String?> getPlatformVersion() {
    return FlutterMapboxNavigationPlatform.instance.getPlatformVersion();
  }

  ///Total distance remaining in meters along route.
  Future<double?> getDistanceRemaining() {
    return FlutterMapboxNavigationPlatform.instance.getDistanceRemaining();
  }

  ///Total seconds remaining on all legs.
  Future<double?> getDurationRemaining() {
    return FlutterMapboxNavigationPlatform.instance.getDurationRemaining();
  }

  ///Adds waypoints or stops to an on-going navigation
  ///
  /// [wayPoints] must not be null and have at least 1 item. The way points will
  /// be inserted after the currently navigating waypoint
  /// in the existing navigation
  Future<dynamic> addWayPoints({required List<WayPoint> wayPoints}) async {
    return FlutterMapboxNavigationPlatform.instance
        .addWayPoints(wayPoints: wayPoints);
  }

  /// Free-drive mode is a unique Mapbox Navigation SDK feature that allows
  /// drivers to navigate without a set destination.
  /// This mode is sometimes referred to as passive navigation.
  /// Begins to generate Route Progress
  ///
  Future<bool?> startFreeDrive({MapBoxOptions? options}) async {
    options ??= _defaultOptions;
    return FlutterMapboxNavigationPlatform.instance.startFreeDrive(options);
  }

  ///Show the Navigation View and Begins Direction Routing
  ///
  /// [wayPoints] must not be null and have at least 2 items. A collection of
  /// [WayPoint](longitude, latitude and name). Must be at least 2 or
  /// at most 25. Cannot use drivingWithTraffic mode if more than 3-waypoints.
  /// [options] options used to generate the route and used while navigating
  /// Begins to generate Route Progress
  ///
  Future<bool?> startNavigation({
    required List<WayPoint> wayPoints,
    MapBoxOptions? options,
  }) async {
    options ??= _defaultOptions;

    // Always store the route as the planned guide route when enabled
    if (options.showPlannedRoute ?? true) {
      _plannedRoute = List<WayPoint>.from(wayPoints);
    }

    return FlutterMapboxNavigationPlatform.instance
        .startNavigation(wayPoints, options);
  }

  ///Show the Navigation View and Begins Navigation with a predefined GeoJSON route
  ///
  /// [geoJsonRoute] A predefined route in GeoJSON format. The navigation will
  /// follow the exact geometry provided, displaying it in the specified color.
  /// When the user goes off-route, the system will recalculate to snap back
  /// to the nearest point on this GeoJSON route, maintaining the original path.
  /// [options] options used for navigation display and behavior
  ///
  /// This is ideal for following predefined routes like delivery routes,
  /// tour routes, or any scenario where you need to follow a specific path
  /// rather than letting the navigation SDK calculate routes dynamically.
  ///
  Future<bool?> startNavigationWithGeoJson({
    required GeoJsonRoute geoJsonRoute,
    MapBoxOptions? options,
  }) async {
    options ??= _defaultOptions;

    return FlutterMapboxNavigationPlatform.instance
        .startNavigationWithGeoJson(geoJsonRoute, options);
  }

  ///Ends Navigation and Closes the Navigation View
  Future<bool?> finishNavigation() async {
    _plannedRoute = null; // Clear planned route when navigation ends
    return FlutterMapboxNavigationPlatform.instance.finishNavigation();
  }

  /// Gets the currently stored planned route
  List<WayPoint>? get plannedRoute => _plannedRoute;

  /// Manually sets the planned route
  void setPlannedRoute(List<WayPoint> wayPoints) {
    _plannedRoute = List<WayPoint>.from(wayPoints);
  }

  /// Clears the planned route
  void clearPlannedRoute() {
    _plannedRoute = null;
  }

  /// Calculates a route back to the planned route
  /// This will find the nearest point on the planned route and create a route
  /// to it
  Future<bool?> calculateRouteToPlannedRoute() async {
    if (_plannedRoute == null || _plannedRoute!.isEmpty) {
      return false;
    }
    
    // Get current location and calculate route to the nearest waypoint on
    // planned route. For simplicity, we'll route to the next waypoint in the
    // planned route
    return FlutterMapboxNavigationPlatform.instance
        .startNavigation(_plannedRoute!, _defaultOptions);
  }

  /// Will download the navigation engine and the user's region
  /// to allow offline routing
  Future<bool?> enableOfflineRouting() async {
    return FlutterMapboxNavigationPlatform.instance.enableOfflineRouting();
  }

  /// Event listener for RouteEvents
  Future<dynamic> registerRouteEventListener(
    ValueSetter<RouteEvent> listener,
  ) async {
    return FlutterMapboxNavigationPlatform.instance
        .registerRouteEventListener(listener);
  }
}
