# Guia Completo: Navegação com GEOJSON

Este guia fornece exemplos práticos e detalhados sobre como usar rotas GEOJSON com o plugin Flutter Mapbox Navigation.

## Índice

1. [O que é GEOJSON?](#o-que-é-geojson)
2. [Por que usar GEOJSON?](#por-que-usar-geojson)
3. [Formatos Suportados](#formatos-suportados)
4. [Exemplos Práticos](#exemplos-práticos)
5. [Como Obter GEOJSON](#como-obter-geojson)
6. [Troubleshooting](#troubleshooting)

## O que é GEOJSON?

GeoJSON é um formato para codificar estruturas de dados geográficos. Para navegação, usamos o tipo **LineString**, que representa uma linha conectando múltiplos pontos.

Estrutura básica:
```json
{
  "type": "LineString",
  "coordinates": [
    [longitude1, latitude1],
    [longitude2, latitude2],
    [longitude3, latitude3]
  ]
}
```

**Importante**: GeoJSON usa ordem **[longitude, latitude]**, não latitude/longitude!

## Por que usar GEOJSON?

### Casos de Uso Ideais

1. **Rotas de Entrega Fixas**
   - Rota já definida pela empresa
   - Motorista deve seguir caminho específico
   - Recálculo sempre volta à rota original

2. **Tours Turísticos**
   - Roteiro predefinido
   - Pontos de interesse específicos
   - Experiência consistente

3. **Rotas Otimizadas Externamente**
   - Rota calculada por seu próprio algoritmo
   - Considerações especiais (altura de veículo, peso, etc)
   - Integração com sistemas de logística

4. **Replay de Rotas**
   - Replicar uma rota anterior
   - Treinar novos motoristas
   - Análise e auditoria

## Formatos Suportados

### 1. GeoJSON LineString Completo

```dart
final geoJsonRoute = GeoJsonRoute(
  geometry: '''
  {
    "type": "LineString",
    "coordinates": [
      [-46.633308, -23.550520],
      [-46.635000, -23.549500],
      [-46.637000, -23.548000],
      [-46.638818, -23.548943]
    ]
  }
  ''',
  routeColor: "#FF6B00",
  routeName: "Minha Rota"
);
```

### 2. Polyline Codificado (Mapbox/Google)

```dart
final geoJsonRoute = GeoJsonRoute(
  geometry: "y~m~Fvro}O_a@vBmB~AqApCwB}CwA",
  routeColor: "#FF6B00",
  routeName: "Minha Rota"
);
```

O plugin detecta automaticamente qual formato você está usando.

## Exemplos Práticos

### Exemplo 1: Rota de Entrega Simples

```dart
import 'package:flutter/material.dart';
import 'package:flutter_mapbox_navigation/flutter_mapbox_navigation.dart';

class SimpleDeliveryExample extends StatelessWidget {
  Future<void> startDelivery() async {
    // Rota fixa de entrega
    final deliveryRoute = GeoJsonRoute(
      geometry: '''
      {
        "type": "LineString",
        "coordinates": [
          [-46.633308, -23.550520],
          [-46.634500, -23.551200],
          [-46.635800, -23.551800],
          [-46.637000, -23.548000],
          [-46.638818, -23.548943]
        ]
      }
      ''',
      routeColor: "#FF6B00",
      routeName: "Entrega Zona Sul - Rota 1"
    );

    final options = MapBoxOptions(
      mode: MapBoxNavigationMode.driving,
      language: "pt-BR",
      units: VoiceUnits.metric,
      voiceInstructionsEnabled: true,
      bannerInstructionsEnabled: true,
      simulateRoute: false,
    );

    await MapBoxNavigation.instance.startNavigationWithGeoJson(
      geoJsonRoute: deliveryRoute,
      options: options
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Entrega')),
      body: Center(
        child: ElevatedButton(
          onPressed: startDelivery,
          child: Text('Iniciar Entrega'),
        ),
      ),
    );
  }
}
```

### Exemplo 2: Obtendo GEOJSON de uma API

```dart
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:flutter_mapbox_navigation/flutter_mapbox_navigation.dart';

class ApiDeliveryExample extends StatefulWidget {
  @override
  _ApiDeliveryExampleState createState() => _ApiDeliveryExampleState();
}

class _ApiDeliveryExampleState extends State<ApiDeliveryExample> {
  bool _loading = false;

  Future<void> _startDeliveryFromApi(String routeId) async {
    setState(() => _loading = true);

    try {
      // 1. Buscar rota do seu backend
      final response = await http.get(
        Uri.parse('https://api.suaempresa.com/routes/$routeId')
      );

      if (response.statusCode != 200) {
        throw Exception('Falha ao carregar rota');
      }

      final data = json.decode(response.body);

      // 2. Extrair geometria GeoJSON
      // Supondo que sua API retorna:
      // {
      //   "route": {
      //     "id": "123",
      //     "geometry": { "type": "LineString", "coordinates": [...] },
      //     "color": "#FF6B00",
      //     "name": "Rota de Entrega"
      //   }
      // }

      final geometryJson = data['route']['geometry'];
      final geometryString = json.encode(geometryJson);

      // 3. Criar rota GEOJSON
      final geoJsonRoute = GeoJsonRoute(
        geometry: geometryString,
        routeColor: data['route']['color'] ?? "#FF6B00",
        routeName: data['route']['name'] ?? "Rota de Entrega"
      );

      // 4. Configurar opções
      final options = MapBoxOptions(
        mode: MapBoxNavigationMode.driving,
        language: "pt-BR",
        units: VoiceUnits.metric,
        voiceInstructionsEnabled: true,
        bannerInstructionsEnabled: true,
        simulateRoute: false,
      );

      // 5. Registrar eventos
      MapBoxNavigation.instance.registerRouteEventListener(_onRouteEvent);

      // 6. Iniciar navegação
      await MapBoxNavigation.instance.startNavigationWithGeoJson(
        geoJsonRoute: geoJsonRoute,
        options: options
      );

    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Erro ao carregar rota: $e'))
      );
    } finally {
      setState(() => _loading = false);
    }
  }

  void _onRouteEvent(RouteEvent e) {
    switch (e.eventType) {
      case MapBoxEvent.user_off_route:
        print("⚠️  Motorista saiu da rota!");
        // Opcional: notificar backend
        _notifyBackendOffRoute();
        break;

      case MapBoxEvent.reroute_along:
        print("✅ Recalculado para retornar à rota");
        break;

      case MapBoxEvent.on_arrival:
        print("🎯 Entrega concluída!");
        // Opcional: marcar como concluída no backend
        _markDeliveryComplete();
        break;
    }
  }

  Future<void> _notifyBackendOffRoute() async {
    // Implementar notificação ao backend
  }

  Future<void> _markDeliveryComplete() async {
    // Implementar marcação de conclusão
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Entrega via API')),
      body: Center(
        child: _loading
            ? CircularProgressIndicator()
            : ElevatedButton(
                onPressed: () => _startDeliveryFromApi('route_123'),
                child: Text('Iniciar Entrega'),
              ),
      ),
    );
  }
}
```

### Exemplo 3: Convertendo Waypoints para GEOJSON

```dart
import 'dart:convert';
import 'package:flutter_mapbox_navigation/flutter_mapbox_navigation.dart';

class WaypointConverter {
  /// Converte uma lista de waypoints para GEOJSON LineString
  static String waypointsToGeoJson(List<WayPoint> waypoints) {
    if (waypoints.length < 2) {
      throw ArgumentError('Precisa de pelo menos 2 waypoints');
    }

    final coordinates = waypoints.map((wp) {
      return [wp.longitude, wp.latitude];
    }).toList();

    final geoJson = {
      "type": "LineString",
      "coordinates": coordinates
    };

    return json.encode(geoJson);
  }

  /// Exemplo de uso
  static Future<void> convertAndNavigate() async {
    // Waypoints existentes
    final waypoints = [
      WayPoint(
        name: "Origem",
        latitude: -23.550520,
        longitude: -46.633308
      ),
      WayPoint(
        name: "Parada 1",
        latitude: -23.551200,
        longitude: -46.634500
      ),
      WayPoint(
        name: "Destino",
        latitude: -23.548943,
        longitude: -46.638818
      ),
    ];

    // Converter para GEOJSON
    final geometry = waypointsToGeoJson(waypoints);

    // Criar rota
    final geoJsonRoute = GeoJsonRoute(
      geometry: geometry,
      routeColor: "#00FF00",
      routeName: "Rota Convertida"
    );

    // Navegar
    await MapBoxNavigation.instance.startNavigationWithGeoJson(
      geoJsonRoute: geoJsonRoute,
      options: MapBoxOptions(
        language: "pt-BR",
        units: VoiceUnits.metric,
      )
    );
  }
}
```

### Exemplo 4: Rotas Múltiplas com Seleção

```dart
class MultiRouteSelector extends StatefulWidget {
  @override
  _MultiRouteSelectorState createState() => _MultiRouteSelectorState();
}

class _MultiRouteSelectorState extends State<MultiRouteSelector> {
  final List<GeoJsonRoute> _routes = [];

  @override
  void initState() {
    super.initState();
    _loadRoutes();
  }

  void _loadRoutes() {
    setState(() {
      _routes.addAll([
        GeoJsonRoute(
          geometry: '''{"type":"LineString","coordinates":[[-46.633308,-23.550520],[-46.638818,-23.548943]]}''',
          routeColor: "#FF0000",
          routeName: "Rota Rápida"
        ),
        GeoJsonRoute(
          geometry: '''{"type":"LineString","coordinates":[[-46.633308,-23.550520],[-46.635000,-23.549500],[-46.638818,-23.548943]]}''',
          routeColor: "#00FF00",
          routeName: "Rota Econômica"
        ),
        GeoJsonRoute(
          geometry: '''{"type":"LineString","coordinates":[[-46.633308,-23.550520],[-46.634000,-23.552000],[-46.638818,-23.548943]]}''',
          routeColor: "#0000FF",
          routeName: "Rota Panorâmica"
        ),
      ]);
    });
  }

  Future<void> _startRoute(GeoJsonRoute route) async {
    final options = MapBoxOptions(
      language: "pt-BR",
      units: VoiceUnits.metric,
      voiceInstructionsEnabled: true,
    );

    await MapBoxNavigation.instance.startNavigationWithGeoJson(
      geoJsonRoute: route,
      options: options
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Escolher Rota')),
      body: ListView.builder(
        itemCount: _routes.length,
        itemBuilder: (context, index) {
          final route = _routes[index];
          return Card(
            child: ListTile(
              leading: Container(
                width: 40,
                height: 40,
                decoration: BoxDecoration(
                  color: Color(int.parse(route.routeColor!.replaceFirst('#', '0xFF'))),
                  shape: BoxShape.circle,
                ),
              ),
              title: Text(route.routeName ?? 'Rota ${index + 1}'),
              trailing: Icon(Icons.arrow_forward),
              onTap: () => _startRoute(route),
            ),
          );
        },
      ),
    );
  }
}
```

## Como Obter GEOJSON

### 1. Da API do Mapbox Directions

```dart
import 'package:http/http.dart' as http;
import 'dart:convert';

Future<String> getRouteFromMapbox(
  double startLng,
  double startLat,
  double endLng,
  double endLat,
  String accessToken
) async {
  final url = 'https://api.mapbox.com/directions/v5/mapbox/driving/'
      '$startLng,$startLat;$endLng,$endLat'
      '?access_token=$accessToken'
      '&geometries=geojson';

  final response = await http.get(Uri.parse(url));
  final data = json.decode(response.body);

  // Extrair geometria da primeira rota
  final geometry = data['routes'][0]['geometry'];

  return json.encode(geometry);
}

// Uso
final geometry = await getRouteFromMapbox(
  -46.633308, -23.550520,  // Origem
  -46.638818, -23.548943,  // Destino
  'SEU_TOKEN_MAPBOX'
);

final route = GeoJsonRoute(geometry: geometry);
```

### 2. Do Google Maps Directions API

```dart
Future<String> getRouteFromGoogle(
  double startLat,
  double startLng,
  double endLat,
  double endLng,
  String apiKey
) async {
  final url = 'https://maps.googleapis.com/maps/api/directions/json'
      '?origin=$startLat,$startLng'
      '&destination=$endLat,$endLng'
      '&key=$apiKey';

  final response = await http.get(Uri.parse(url));
  final data = json.decode(response.body);

  // Google retorna polyline codificado
  final polyline = data['routes'][0]['overview_polyline']['points'];

  // Use diretamente (plugin suporta polyline)
  return polyline;
}

// Uso
final polyline = await getRouteFromGoogle(
  -23.550520, -46.633308,  // Origem
  -23.548943, -46.638818,  // Destino
  'SUA_CHAVE_GOOGLE'
);

final route = GeoJsonRoute(geometry: polyline);
```

### 3. De um Arquivo Local

```dart
import 'package:flutter/services.dart' show rootBundle;

Future<GeoJsonRoute> loadRouteFromAsset(String assetPath) async {
  final jsonString = await rootBundle.loadString(assetPath);
  final data = json.decode(jsonString);

  return GeoJsonRoute(
    geometry: json.encode(data['geometry']),
    routeColor: data['color'],
    routeName: data['name']
  );
}

// Uso (com assets/routes/delivery1.json no seu projeto)
final route = await loadRouteFromAsset('assets/routes/delivery1.json');
```

## Troubleshooting

### Erro: "Failed to parse GeoJSON geometry"

**Problema**: A geometria não está em formato válido.

**Solução**:
```dart
// ❌ Errado - falta aspas nas chaves
geometry: '{type: LineString, coordinates: [...]}'

// ✅ Correto - JSON válido
geometry: '{"type": "LineString", "coordinates": [...]}'

// ✅ Correto - Polyline
geometry: 'y~m~Fvro}O_a@vBmB~AqApCwB}CwA'
```

### Erro: "Route must have at least 2 points"

**Problema**: GEOJSON tem menos de 2 coordenadas.

**Solução**:
```dart
// ❌ Errado - apenas 1 ponto
"coordinates": [[-46.633308, -23.550520]]

// ✅ Correto - mínimo 2 pontos
"coordinates": [
  [-46.633308, -23.550520],
  [-46.638818, -23.548943]
]
```

### Coordenadas Invertidas

**Problema**: Rota aparece em local errado.

**Solução**: GeoJSON usa **[longitude, latitude]**, não [latitude, longitude]!

```dart
// ❌ Errado
"coordinates": [[-23.550520, -46.633308]]

// ✅ Correto
"coordinates": [[-46.633308, -23.550520]]
//                 ^longitude  ^latitude
```

### Rota Não Recalcula

**Problema**: Ao sair da rota, não recalcula.

**Solução**: Verificar configurações:
```dart
final options = MapBoxOptions(
  // Certifique-se de que está habilitado
  // (padrão já é true, mas pode ter sido sobrescrito)
);
```

O sistema é automático. Se não está funcionando, pode ser um problema de permissões de localização.

### Performance com Rotas Longas

**Problema**: Rota com muitos pontos fica lenta.

**Solução**: Use polyline codificado em vez de GeoJSON completo:

```dart
// ❌ Lento - 1000+ pontos expandidos
geometry: '''{"type":"LineString","coordinates":[[...1000 pontos...]]}'''

// ✅ Rápido - polyline comprimido
geometry: "polyline_string_aqui"
```

## Testando com Simulação

Para testar sem precisar dirigir:

```dart
final options = MapBoxOptions(
  simulateRoute: true,  // Simula movimento ao longo da rota
  language: "pt-BR",
  units: VoiceUnits.metric,
);

await MapBoxNavigation.instance.startNavigationWithGeoJson(
  geoJsonRoute: route,
  options: options
);
```

## Boas Práticas

1. **Validar GEOJSON antes de enviar**
   ```dart
   bool isValidGeoJson(String geometry) {
     try {
       final parsed = json.decode(geometry);
       return parsed['type'] == 'LineString' &&
              parsed['coordinates'] is List &&
              parsed['coordinates'].length >= 2;
     } catch (e) {
       return false;
     }
   }
   ```

2. **Cache de rotas frequentes**
   ```dart
   final prefs = await SharedPreferences.getInstance();
   await prefs.setString('route_cache_$routeId', geometryJson);
   ```

3. **Tratamento de erros**
   ```dart
   try {
     await MapBoxNavigation.instance.startNavigationWithGeoJson(
       geoJsonRoute: route,
       options: options
     );
   } on PlatformException catch (e) {
     print('Erro ao iniciar navegação: ${e.message}');
   }
   ```

4. **Logging de eventos**
   ```dart
   MapBoxNavigation.instance.registerRouteEventListener((e) {
     logger.log('Route event: ${e.eventType}');
     analytics.track('navigation_event', {
       'type': e.eventType.toString(),
       'route_name': currentRoute.routeName,
     });
   });
   ```

## Recursos Adicionais

- [Especificação GeoJSON](https://geojson.org/)
- [Mapbox Directions API](https://docs.mapbox.com/api/navigation/directions/)
- [Google Maps Polyline Algorithm](https://developers.google.com/maps/documentation/utilities/polylinealgorithm)
- [Validador GeoJSON Online](http://geojson.io/)

## Suporte

Para questões, problemas ou sugestões, abra uma issue no repositório GitHub do projeto.
