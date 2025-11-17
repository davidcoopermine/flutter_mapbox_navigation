import 'package:flutter_mapbox_navigation/src/models/way_point.dart';

/// Event data for when the user goes off the planned route
class OffRouteEvent {
  /// Constructor
  OffRouteEvent({
    this.distanceFromPlannedRoute,
    this.nearestPlannedWaypoint,
    this.isOffPlannedRoute = false,
  });
  
  /// Creates [OffRouteEvent] object from json
  factory OffRouteEvent.fromJson(Map<String, dynamic> json) {
    return OffRouteEvent(
      distanceFromPlannedRoute: json['distanceFromPlannedRoute'] as double?,
      nearestPlannedWaypoint: json['nearestPlannedWaypoint'] != null
          ? WayPoint.fromJson(
              json['nearestPlannedWaypoint'] as Map<String, dynamic>,)
          : null,
      isOffPlannedRoute: json['isOffPlannedRoute'] as bool? ?? false,
    );
  }

  /// Distance from the planned route in meters
  final double? distanceFromPlannedRoute;
  
  /// The nearest waypoint on the planned route
  final WayPoint? nearestPlannedWaypoint;
  
  /// Whether the user is currently off the planned route
  final bool isOffPlannedRoute;
  
  /// Convert to JSON
  Map<String, dynamic> toJson() {
    return {
      'distanceFromPlannedRoute': distanceFromPlannedRoute,
      'nearestPlannedWaypoint': nearestPlannedWaypoint?.toJson(),
      'isOffPlannedRoute': isOffPlannedRoute,
    };
  }
}
