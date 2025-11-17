# 🚗 Guia de Configuração - GODRIVERS App

## 📋 Sumário

- [Processar GeoJSON Corretamente](#processar-geojson-corretamente)
- [Código Correto vs Incorreto](#código-correto-vs-incorreto)
- [Verificação e Debug](#verificação-e-debug)
- [Checklist Final](#checklist-final)

---


### 2. No projeto GODRIVERS
```bash
# Atualizar dependências
flutter pub upgrade flutter_mapbox_navigation

# Limpar cache
flutter clean

# Reinstalar dependências
flutter pub get

# Rebuild completo (OBRIGATÓRIO!)
flutter run
```

---

## 📍 Processar GeoJSON Corretamente

### ⚠️ PROBLEMA IDENTIFICADO

Nosso app está extraindo apenas **2 pontos** quando o GeoJSON tem **45 coordenadas**!

```dart
// ❌ ERRADO - Isso está acontecendo no seu app:
I/flutter: ✅ GeoJSON carregado com sucesso
I/flutter:    Pontos: 2  // PROBLEMA! Deveria ser 45!
```

### ✅ SOLUÇÃO

O GeoJSON da API Mapbox tem esta estrutura:

```json
{
  "routes": [{
    "geometry": {
      "type": "LineString",
      "coordinates": [
        [-42.19569, -21.41056],    // ← PONTO INICIAL (NÃO IGNORE!)
        [-42.195445, -21.410603],
        [-42.195499, -21.411133],
        // ... mais 42 pontos
        [-42.186908, -21.404917]   // ← PONTO FINAL
      ]
    },
    "legs": [
      {
        "steps": [...],
        "distance": 546.157,
        "duration": 115.529
      },
      // ... mais legs
    ]
  }],
  "waypoints": [
    { "location": [-42.19569, -21.41056], "name": "" },
    // ... waypoints intermediários
  ]
}
```

---

## 💻 Código Correto vs Incorreto

### ❌ CÓDIGO INCORRETO (NÃO USE!)

```dart
// PROBLEMA: Extrai apenas waypoints das legs, ignora geometria completa!
Future<void> loadRouteFromBackend(int routeId) async {
  final response = await api.getRouteGeoJson(routeId);
  final geoJson = jsonDecode(response);

  // ❌ ERRADO - Pega apenas waypoints (3 pontos)
  final waypoints = geoJson['waypoints'] as List;

  print('Pontos: ${waypoints.length}'); // Resultado: 2 ou 3

  // ❌ Isso ignora todos os 45 pontos da geometria!
  final route = GeoJsonRoute(
    geometry: jsonEncode({
      'type': 'LineString',
      'coordinates': waypoints.map((w) => w['location']).toList()
    }),
  );
}
```

### ✅ CÓDIGO CORRETO (USE ESTE!)

```dart
Future<void> loadRouteFromBackend(int routeId) async {
  final response = await api.getRouteGeoJson(routeId);
  final geoJson = jsonDecode(response);

  // ✅ CORRETO - Extrai a geometria COMPLETA da rota
  final routes = geoJson['routes'] as List;
  if (routes.isEmpty) {
    throw Exception('Nenhuma rota encontrada no GeoJSON');
  }

  final firstRoute = routes[0];
  final geometry = firstRoute['geometry'];

  // Verificação importante
  if (geometry == null) {
    throw Exception('Geometria não encontrada no GeoJSON');
  }

  // ✅ Contar pontos da geometria
  final coordinates = geometry['coordinates'] as List;
  print('📊 Total de coordenadas: ${coordinates.length}'); // Deve ser 45!
  print('📍 Primeiro ponto: ${coordinates.first}');
  print('📍 Último ponto: ${coordinates.last}');

  // ✅ Criar GeoJsonRoute com geometria COMPLETA
  final route = GeoJsonRoute(
    geometry: jsonEncode(geometry), // Geometria completa!
    routeColor: '#32CD32',
    routeName: 'Rota $routeId',
  );

  // ✅ Iniciar navegação
  final options = MapBoxOptions(
    mode: MapBoxNavigationMode.driving,
    simulateRoute: false,
    language: 'pt-BR',
    units: VoiceUnits.metric,
    showPlannedRoute: true,
    plannedRouteColor: '#32CD32',
  );

  await MapBoxNavigation.instance.startNavigationWithGeoJson(
    geoJsonRoute: route,
    options: options,
  );
}
```

---

## 🔍 Verificação e Debug

### Logs que DEVEM aparecer após atualizar o plugin:

```
D/GeoJSON(12904): 🗺️ Loading new GeoJSON route - cleaning old layers first...
D/GeoJSON(12904): ✅ Old layers cleaned, proceeding with new route...

D/GeoJsonRoute(12904): 📍 FIRST point of route: [-42.19569, -21.41056]
D/GeoJsonRoute(12904): 📍 LAST point of route: [-42.186908, -21.404917]
D/GeoJsonRoute(12904): 📊 Total coordinates in GeoJSON: 45

D/GeoJsonRoute(12904): Creating initial route with 25 waypoints from 45 total coordinates
D/GeoJsonRoute(12904): 📍 First waypoint: [-42.19569, -21.41056]
D/GeoJsonRoute(12904): 📍 Last waypoint: [-42.186908, -21.404917]

D/CreateSteps(12904): 🔍 Looking for leg: [-42.19569, -21.41056] -> [-42.192005, -21.411136]
D/CreateSteps(12904): 📌 Found indices: start=0, end=14 (total coords: 45)
D/CreateSteps(12904): Creating steps for leg with 15 coordinate points
D/CreateSteps(12904): Created 1 detailed steps covering all 15 points

D/PlannedRoute(12904): Starting to draw planned route with color: #32CD32
D/PlannedRoute(12904): ✅ Planned route (GREEN LINE with border) drawn successfully!
```

### ⚠️ Se esses logs NÃO aparecerem:

1. **Você não fez rebuild completo**
   ```bash
   flutter clean
   flutter pub get
   flutter run
   ```

2. **O plugin não foi atualizado**
   ```bash
   flutter pub upgrade flutter_mapbox_navigation
   ```

3. **Você está rodando um APK antigo**
   - Desinstale o app do celular
   - Reinstale com `flutter run`

---

## 📝 Exemplo Completo de Uso

```dart
import 'package:flutter_mapbox_navigation/flutter_mapbox_navigation.dart';

class RouteNavigationService {

  /// Carrega e inicia navegação com GeoJSON do backend
  Future<void> startNavigationWithBackendRoute(int routeId) async {
    try {
      // 1. Buscar GeoJSON do backend
      final geoJsonData = await _fetchRouteGeoJson(routeId);

      // 2. Validar e processar GeoJSON
      final geoJsonRoute = _processGeoJson(geoJsonData);

      // 3. Configurar opções de navegação
      final options = MapBoxOptions(
        mode: MapBoxNavigationMode.driving,
        simulateRoute: false,
        language: 'pt-BR',
        units: VoiceUnits.metric,
        showPlannedRoute: true,
        plannedRouteColor: '#32CD32',
        autoRecalculateOnDeviation: true,
        voiceInstructionsEnabled: true,
        bannerInstructionsEnabled: true,
      );

      // 4. Iniciar navegação
      await MapBoxNavigation.instance.startNavigationWithGeoJson(
        geoJsonRoute: geoJsonRoute,
        options: options,
      );

      print('✅ Navegação iniciada com sucesso!');

    } catch (e) {
      print('❌ Erro ao iniciar navegação: $e');
      rethrow;
    }
  }

  /// Busca GeoJSON do backend
  Future<Map<String, dynamic>> _fetchRouteGeoJson(int routeId) async {
    // Substitua pela sua chamada API real
    final response = await http.get(
      Uri.parse('https://seu-backend.com/api/routes/$routeId/geojson'),
    );

    if (response.statusCode != 200) {
      throw Exception('Falha ao buscar rota: ${response.statusCode}');
    }

    return jsonDecode(response.body);
  }

  /// Processa GeoJSON e valida dados
  GeoJsonRoute _processGeoJson(Map<String, dynamic> geoJson) {
    // Validar estrutura
    if (!geoJson.containsKey('routes')) {
      throw Exception('GeoJSON inválido: campo "routes" não encontrado');
    }

    final routes = geoJson['routes'] as List;
    if (routes.isEmpty) {
      throw Exception('GeoJSON não contém rotas');
    }

    // Extrair primeira rota
    final route = routes[0];
    final geometry = route['geometry'];

    if (geometry == null) {
      throw Exception('Geometria não encontrada na rota');
    }

    // Validar geometria
    if (geometry['type'] != 'LineString') {
      throw Exception('Tipo de geometria inválido: ${geometry['type']}');
    }

    final coordinates = geometry['coordinates'] as List;
    if (coordinates.length < 2) {
      throw Exception('Rota deve ter pelo menos 2 pontos');
    }

    // Log para debug
    print('📊 GeoJSON processado:');
    print('   Total de coordenadas: ${coordinates.length}');
    print('   Primeiro ponto: ${coordinates.first}');
    print('   Último ponto: ${coordinates.last}');

    // IMPORTANTE: Passar a geometria COMPLETA, não apenas waypoints!
    return GeoJsonRoute(
      geometry: jsonEncode(geometry),
      routeColor: '#32CD32',
      routeName: 'Rota ${route['distance']?.toStringAsFixed(0)}m',
    );
  }
}
```

---

## ✅ Checklist Final

Antes de testar, verifique:

### 📱 No projeto do plugin
- [ ] Código modificado em `TurnByTurn.kt`
- [ ] Commit feito com mensagem clara
- [ ] Push para o repositório Git

### 🚗 No projeto GODRIVERS
- [ ] Plugin atualizado (`flutter pub upgrade`)
- [ ] Cache limpo (`flutter clean`)
- [ ] Dependências reinstaladas (`flutter pub get`)
- [ ] Rebuild completo (`flutter run`)
- [ ] App antigo desinstalado do celular

### 💻 No código do app
- [ ] GeoJSON extraindo geometria completa (não apenas waypoints)
- [ ] Validação de dados (verificar se tem coordenadas)
- [ ] Logs de debug adicionados
- [ ] `GeoJsonRoute` criado com geometria completa

### 🔍 Ao testar
- [ ] Logs do Android aparecem com emojis e detalhes
- [ ] Total de coordenadas está correto (ex: 45)
- [ ] Primeiro ponto está correto
- [ ] Linha verde aparece COMPLETA no mapa
- [ ] Linha azul segue a linha verde ponto por ponto
- [ ] Navegação não pula waypoints

---

## 🐛 Problemas Comuns

### Problema 1: "Pontos: 2" em vez de 45

**Causa:** Código está extraindo waypoints ao invés de geometria completa

**Solução:** Use `route['geometry']` ao invés de `route['waypoints']`

### Problema 2: Linha verde começa do segundo ponto

**Causa:** Geometria está sendo cortada ou primeiro ponto foi removido

**Solução:** Verifique se está usando `jsonEncode(geometry)` sem modificações

### Problema 3: Logs do plugin não aparecem

**Causa:** Rebuild não foi feito ou app antigo está rodando

**Solução:**
```bash
flutter clean
flutter pub get
# Desinstalar app do celular
flutter run
```

### Problema 4: Navegação pula waypoints

**Causa:** Steps vazios (plugin não atualizado)

**Solução:** Certifique-se de que fez pull do plugin atualizado

---

## 📞 Suporte

Se após seguir este guia o problema persistir, forneça:

1. ✅ Confirmação de que fez rebuild completo
2. ✅ Logs completos do Android (com filtro `GeoJson`, `CreateSteps`, `PlannedRoute`)
3. ✅ Código que processa o GeoJSON no seu app
4. ✅ Exemplo do GeoJSON que está sendo usado

---

## 🎯 Resultado Esperado

Após seguir todas as instruções, você deve ter:

- ✅ Linha verde traçada **COMPLETAMENTE** desde o primeiro ponto
- ✅ Linha azul seguindo **TODA** a linha verde sem pulos
- ✅ Navegação funcionando corretamente
- ✅ Sistema de off-route redirecionando para a linha verde
- ✅ Logs detalhados para debug

**Boa navegação! 🚗💨**
