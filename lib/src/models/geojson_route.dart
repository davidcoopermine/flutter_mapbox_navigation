/// Represents a route defined by GeoJSON geometry.
/// This allows the navigation to follow a predefined route path instead of
/// calculating routes based on waypoints.
class GeoJsonRoute {
  /// Constructor
  GeoJsonRoute({
    required this.geometry,
    this.routeColor = '#FFFF00',
    this.routeName,
  });

  /// Create [GeoJsonRoute] from JSON
  GeoJsonRoute.fromJson(Map<String, dynamic> json) {
    geometry = json['geometry'] as String;
    routeColor = json['routeColor'] as String? ?? '#FFFF00';
    routeName = json['routeName'] as String?;
  }

  /// The GeoJSON geometry string representing the route path.
  /// This should be a LineString geometry in encoded polyline format or GeoJSON format.
  /// Example: "encoded_polyline_string" or full GeoJSON geometry object
  late String geometry;

  /// The color to display the route in hex format (e.g., '#FFFF00' for yellow).
  /// Default is yellow (#FFFF00).
  String? routeColor;

  /// Optional name for the route
  String? routeName;

  /// Convert to JSON
  Map<String, dynamic> toJson() {
    return {
      'geometry': geometry,
      'routeColor': routeColor,
      'routeName': routeName,
    };
  }

  @override
  String toString() {
    return 'GeoJsonRoute{geometry: ${geometry.substring(0, geometry.length > 50 ? 50 : geometry.length)}..., routeColor: $routeColor, routeName: $routeName}';
  }
}
