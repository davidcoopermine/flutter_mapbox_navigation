[![Pub][pub_badge]][pub] [![BuyMeACoffee][buy_me_a_coffee_badge]][buy_me_a_coffee]

# flutter_mapbox_navigation

Adicione navegação turn-by-turn à sua aplicação Flutter usando MapBox. Nunca saia do seu app quando precisar navegar seus usuários até um local.

## Recursos

* Interface de navegação turn-by-turn completa para Flutter, pronta para ser integrada à sua aplicação
* [Estilos de mapa profissionais](https://www.mapbox.com/maps/) para condução diurna e noturna
* Direções mundiais para condução, ciclismo e caminhada alimentadas por [dados abertos](https://www.mapbox.com/about/open/) e feedback dos usuários
* Evitamento de tráfego e roteamento proativo baseado em condições atuais em [mais de 55 países](https://docs.mapbox.com/help/how-mapbox-works/directions/#traffic-data)
* Instruções de navegação com som natural alimentadas pelo [Amazon Polly](https://aws.amazon.com/polly/) (nenhuma configuração necessária)
* [Suporte para mais de duas dúzias de idiomas](https://docs.mapbox.com/ios/navigation/overview/localization-and-internationalization/)
* **Suporte completo para waypoints personalizados e rotas multi-parada**
* **Interface otimizada sem botão de cancelamento desnecessário**

## Configuração iOS

1. Vá para o seu [painel da conta Mapbox](https://account.mapbox.com/) e crie um token de acesso que tenha o escopo `DOWNLOADS:READ`. **IMPORTANTE: Este não é o mesmo que seu token de API Mapbox de produção. Mantenha-o privado e não o insira em nenhum arquivo Info.plist.** Crie um arquivo chamado `.netrc` em seu diretório home se ele ainda não existir, então adicione as seguintes linhas ao final do arquivo:
   ```
   machine api.mapbox.com
     login mapbox
     password PRIVATE_MAPBOX_API_TOKEN
   ```
   onde _PRIVATE_MAPBOX_API_TOKEN_ é seu token de API Mapbox com o escopo `DOWNLOADS:READ`.
   
2. As APIs e tiles vetoriais do Mapbox requerem uma conta Mapbox e token de acesso API. No editor do projeto, selecione o target da aplicação, então vá para a aba Info. Sob a seção "Custom iOS Target Properties", defina `MBXAccessToken` para seu token de acesso. Você pode obter um token de acesso da [página da conta Mapbox](https://account.mapbox.com/access-tokens/).

3. Para que o SDK rastreie a localização do usuário conforme ele se move pela rota, defina `NSLocationWhenInUseUsageDescription` para:
   > Mostra sua localização no mapa e ajuda a melhorar o OpenStreetMap.

4. Os usuários esperam que o SDK continue rastreando a localização do usuário e entregue instruções audíveis mesmo quando uma aplicação diferente estiver visível ou o dispositivo estiver bloqueado. Vá para a aba Capabilities. Sob a seção Background Modes, habilite "Audio, AirPlay, and Picture in Picture" e "Location updates". (Alternativamente, adicione os valores `audio` e `location` ao array `UIBackgroundModes` na aba Info.)

## Configuração Android

1. As APIs e tiles vetoriais do Mapbox requerem uma conta Mapbox e token de acesso API. Adicione um novo arquivo de recurso chamado `mapbox_access_token.xml` com seu caminho completo sendo `<SEU_ROOT_APP_FLUTTER>/android/app/src/main/res/values/mapbox_access_token.xml`. Então adicione um recurso string com nome "mapbox_access_token" e seu token como valor conforme mostrado abaixo. Você pode obter um token de acesso da [página da conta Mapbox](https://account.mapbox.com/access-tokens/).
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools">
    <string name="mapbox_access_token" translatable="false" tools:ignore="UnusedResources">ADICIONE_SEU_TOKEN_MAPBOX_AQUI</string>
</resources>
```

2. Adicione as seguintes permissões ao Manifest Android do app
```xml
<manifest>
    ...
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    ...
</manifest>
```

3. Adicione o token de Downloads do MapBox com o escopo ```downloads:read``` ao seu arquivo gradle.properties na pasta Android para habilitar o download dos binários MapBox do repositório. Para proteger este token de ser enviado para controle de versão, você pode adicioná-lo ao gradle.properties do seu GRADLE_HOME que geralmente fica em $USER_HOME/.gradle para Mac. Este token pode ser recuperado do seu [Dashboard MapBox](https://account.mapbox.com/access-tokens/). Você pode revisar o [Guia de Token](https://docs.mapbox.com/accounts/guides/tokens/) para saber mais sobre tokens de download
```text
MAPBOX_DOWNLOADS_TOKEN=sk.XXXXXXXXXXXXXXX
```

Após adicionar o acima, seu arquivo gradle.properties pode ficar assim:
```text
org.gradle.jvmargs=-Xmx1536M
android.useAndroidX=true
android.enableJetifier=true
MAPBOX_DOWNLOADS_TOKEN=seutokenDeDownload
```

4. Atualize `MainActivity.kt` para estender `FlutterFragmentActivity` em vez de `FlutterActivity`. Caso contrário você receberá `Caused by: java.lang.IllegalStateException: Please ensure that the hosting Context is a valid ViewModelStoreOwner`.
```kotlin
//import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.android.FlutterFragmentActivity

class MainActivity: FlutterFragmentActivity() {
}
```

5. Adicione `implementation platform("org.jetbrains.kotlin:kotlin-bom:1.8.0")` ao `android/app/build.gradle`

## Uso

#### Definir Opções de Rota Padrão (Opcional)
```dart
    MapBoxNavigation.instance.setDefaultOptions(MapBoxOptions(
                     initialLatitude: -23.550520,
                     initialLongitude: -46.633308,
                     zoom: 13.0,
                     tilt: 0.0,
                     bearing: 0.0,
                     enableRefresh: false,
                     alternatives: true,
                     voiceInstructionsEnabled: true,
                     bannerInstructionsEnabled: true,
                     allowsUTurnAtWayPoints: true,
                     mode: MapBoxNavigationMode.drivingWithTraffic,
                     mapStyleUrlDay: "https://url_para_estilo_dia",
                     mapStyleUrlNight: "https://url_para_estilo_noite",
                     units: VoiceUnits.metric,
                     simulateRoute: true,
                     language: "pt-BR"))
```

#### Ouvir Eventos

```dart
  MapBoxNavigation.instance.registerRouteEventListener(_onRouteEvent);
  Future<void> _onRouteEvent(e) async {

        _distanceRemaining = await _directions.distanceRemaining;
        _durationRemaining = await _directions.durationRemaining;
    
        switch (e.eventType) {
          case MapBoxEvent.progress_change:
            var progressEvent = e.data as RouteProgressEvent;
            _arrived = progressEvent.arrived;
            if (progressEvent.currentStepInstruction != null)
              _instruction = progressEvent.currentStepInstruction;
            break;
          case MapBoxEvent.route_building:
          case MapBoxEvent.route_built:
            _routeBuilt = true;
            break;
          case MapBoxEvent.route_build_failed:
            _routeBuilt = false;
            break;
          case MapBoxEvent.navigation_running:
            _isNavigating = true;
            break;
          case MapBoxEvent.on_arrival:
            _arrived = true;
            if (!_isMultipleStop) {
              await Future.delayed(Duration(seconds: 3));
              await _controller.finishNavigation();
            } else {}
            break;
          case MapBoxEvent.navigation_finished:
          case MapBoxEvent.navigation_cancelled:
            _routeBuilt = false;
            _isNavigating = false;
            break;
          default:
            break;
        }
        //atualizar UI
        setState(() {});
      }
```

#### Começar a Navegar

```dart
    final prefeitura = WayPoint(name: "Prefeitura", latitude: -23.550520, longitude: -46.633308);
    final centrosp = WayPoint(name: "Centro SP", latitude: -23.548943, longitude: -46.638818);

    var wayPoints = List<WayPoint>();
    wayPoints.add(prefeitura);
    wayPoints.add(centrosp);
    
    await MapBoxNavigation.instance.startNavigation(wayPoints: wayPoints);
```

## Trabalhando com Waypoints Personalizados

Este plugin oferece suporte completo para waypoints personalizados, permitindo criar rotas complexas com múltiplas paradas.

### Estrutura do WayPoint

```dart
WayPoint(
  name: "Nome do ponto",        // Nome descritivo do waypoint
  latitude: -23.550520,         // Latitude (obrigatório)
  longitude: -46.633308,        // Longitude (obrigatório)
  isSilent: false              // true = passa sem anunciar, false = anuncia chegada
)
```

### Exemplos Práticos de Waypoints

#### 1. Rota Simples A para B
```dart
final origem = WayPoint(
  name: "Minha Casa", 
  latitude: -23.550520, 
  longitude: -46.633308, 
  isSilent: false
);

final destino = WayPoint(
  name: "Trabalho", 
  latitude: -23.548943, 
  longitude: -46.638818, 
  isSilent: false
);

await MapBoxNavigation.instance.startNavigation(
  wayPoints: [origem, destino]
);
```

#### 2. Rota Multi-Parada com Waypoints Silenciosos
```dart
final waypoints = [
  WayPoint(name: "Casa", latitude: -23.550520, longitude: -46.633308, isSilent: false),
  WayPoint(name: "Posto Gasolina", latitude: -23.549500, longitude: -46.635000, isSilent: true),  // Passa silenciosamente
  WayPoint(name: "Padaria", latitude: -23.548000, longitude: -46.637000, isSilent: false),        // Anuncia chegada
  WayPoint(name: "Farmácia", latitude: -23.547000, longitude: -46.639000, isSilent: false),       // Anuncia chegada
  WayPoint(name: "Escritório", latitude: -23.548943, longitude: -46.638818, isSilent: false),
];

final opcoes = MapBoxOptions(
  mode: MapBoxNavigationMode.driving,
  alternatives: true,
  allowsUTurnAtWayPoints: true,
  isOptimized: true,           // Otimiza a ordem dos waypoints automaticamente
  language: "pt-BR",
  units: VoiceUnits.metric,
  simulateRoute: false,
);

await MapBoxNavigation.instance.startNavigation(
  wayPoints: waypoints,
  options: opcoes
);
```

#### 3. Adição Dinâmica de Waypoints Durante Navegação
```dart
// Durante a navegação, adicionar nova parada
final novaParada = WayPoint(
  name: "Supermercado", 
  latitude: -23.549000, 
  longitude: -46.636000, 
  isSilent: false
);

await MapBoxNavigation.instance.addWayPoints(wayPoints: [novaParada]);
```

#### 4. Navegação Embarcada com Construção de Rota
```dart
// Primeiro, construir a rota
await _controller.buildRoute(
  wayPoints: waypoints,
  options: opcoes
);

// Depois, iniciar navegação quando pronto
await _controller.startNavigation();
```

### Opções Avançadas para Waypoints

```dart
MapBoxOptions(
  // Comportamento da Rota
  mode: MapBoxNavigationMode.drivingWithTraffic,  // driving, walking, cycling, drivingWithTraffic
  alternatives: true,                             // Mostrar rotas alternativas
  allowsUTurnAtWayPoints: true,                  // Permitir retornos nos waypoints
  isOptimized: true,                             // Otimizar ordem dos waypoints (algoritmo TSP)
  
  // Instruções de Voz
  language: "pt-BR",                             // Idioma das instruções
  units: VoiceUnits.metric,                      // Sistema métrico ou imperial
  voiceInstructionsEnabled: true,                // Habilitar instruções de voz
  bannerInstructionsEnabled: true,               // Habilitar banners de instrução
  
  // Personalização Visual
  mapStyleUrlDay: "https://custom-day-style",    // Estilo customizado para o dia
  mapStyleUrlNight: "https://custom-night-style", // Estilo customizado para a noite
  
  // Desenvolvimento e Teste
  simulateRoute: true,                           // Simular navegação para teste
)
```

### Limites e Considerações

- **Máximo**: 25 waypoints por rota
- **Modo `drivingWithTraffic`**: Limitado a 3 waypoints
- **Primeiro e último waypoint**: Nunca podem ser silenciosos
- **Waypoints silenciosos**: Úteis para pontos de passagem obrigatórios sem parada
- **Otimização automática**: Use `isOptimized: true` para reordenar waypoints automaticamente

#### Screenshots
![Navigation View](screenshots/screenshot1.png?raw=true "iOS View") | ![Android View](screenshots/screenshot2.png?raw=true "Android View")
|:---:|:---:|
| Visualização iOS | Visualização Android |

## Incorporando Visualização de Navegação

#### Declarar Controlador
```dart
      MapBoxNavigationViewController _controller;
```

#### Adicionar Visualização de Navegação à Árvore de Widgets
```dart
            Container(
                color: Colors.grey,
                child: MapBoxNavigationView(
                    options: _options,
                    onRouteEvent: _onRouteEvent,
                    onCreated:
                        (MapBoxNavigationViewController controller) async {
                      _controller = controller;
                    }),
              ),
```
#### Construir Rota

```dart
        var wayPoints = List<WayPoint>();
                            wayPoints.add(_origem);
                            wayPoints.add(_parada1);
                            wayPoints.add(_parada2);
                            wayPoints.add(_parada3);
                            wayPoints.add(_parada4);
                            wayPoints.add(_origem);
                            _controller.buildRoute(wayPoints: wayPoints);
```

#### Iniciar Navegação

```dart
    _controller.startNavigation();
```

### Configuração Adicional iOS
Adicione o seguinte ao seu arquivo `info.plist`

```xml
    <dict>
        ...
        <key>io.flutter.embedded_views_preview</key>
        <true/>
        ...
    </dict>
```

### Screenshots de Navegação Incorporada
![Navigation View](screenshots/screenshot3.png?raw=true "Embedded iOS View") | ![Navigation View](screenshots/screenshot4.png?raw=true "Embedded Android View")
|:---:|:---:|
| Visualização iOS Incorporada | Visualização Android Incorporada |

## Alterações Recentes

### v1.0.0+customizada com Layer de Rota Guia e Suporte GEOJSON
- **✅ Removido botão "Cancel" desnecessário** da interface de navegação embarcada
- **✅ Adicionado suporte completo para waypoints personalizados** com documentação em português
- **✅ Melhorada experiência do usuário** com interface mais limpa
- **✅ Documentação traduzida** para português brasileiro
- **✅ Exemplos práticos** para uso de waypoints em cenários reais
- **🆕 Layer de Rota Guia** - Rota original permanece fixa em amarelo como guia visual
- **🆕 Recálculo Automático** - Rota azul recalcula automaticamente quando necessário
- **🆕 Interface Limpa** - Sem rotas alternativas em cinza, apenas guia amarelo e navegação azul
- **🆕 Navegação com GEOJSON** - Suporte completo para rotas predefinidas em formato GEOJSON
- **🆕 Recálculo Inteligente** - Quando fora da rota GEOJSON, recalcula para o ponto mais próximo
- **🆕 Rotas Fixas** - Perfeito para entregas, tours e qualquer cenário com caminho específico

## 🆕 Navegação com Rota GEOJSON

Esta versão adiciona suporte completo para navegação usando rotas predefinidas em formato GEOJSON. Agora você pode fornecer uma rota exata que deve ser seguida, em vez de deixar o SDK calcular rotas automaticamente.

### Por que usar rotas GEOJSON?

- **Rotas Fixas**: Perfeito para rotas de entrega, turismo, ou qualquer cenário onde você precisa seguir um caminho específico
- **Precisão Total**: A rota é exatamente a que você definiu, não depende do algoritmo de roteamento do Mapbox
- **Recálculo Inteligente**: Se o motorista sair da rota, o sistema recalcula para voltar ao ponto mais próximo da rota original
- **Sem lat/lng**: Nunca usa coordenadas isoladas para calcular - sempre obedece a geometria do GEOJSON

### Como Usar Navegação com GEOJSON

```dart
import 'package:flutter_mapbox_navigation/flutter_mapbox_navigation.dart';

// Definir a rota em formato GeoJSON
final geoJsonRoute = GeoJsonRoute(
  // Geometry pode ser:
  // 1. GeoJSON LineString (JSON completo)
  // 2. Polyline codificado do Mapbox
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
  routeColor: "#FF6B00",  // Cor laranja para a rota (opcional)
  routeName: "Rota de Entrega 01"  // Nome da rota (opcional)
);

// Configurar opções
final options = MapBoxOptions(
  language: "pt-BR",
  units: VoiceUnits.metric,
  mode: MapBoxNavigationMode.driving,
  voiceInstructionsEnabled: true,
  bannerInstructionsEnabled: true,
  simulateRoute: false,  // Use true para testar
);

// Iniciar navegação com GEOJSON
await MapBoxNavigation.instance.startNavigationWithGeoJson(
  geoJsonRoute: geoJsonRoute,
  options: options
);
```

### Formatos Suportados para Geometry

#### 1. GeoJSON LineString (Formato JSON)
```dart
final geometry = '''
{
  "type": "LineString",
  "coordinates": [
    [-46.633308, -23.550520],
    [-46.635000, -23.549500],
    [-46.638818, -23.548943]
  ]
}
''';

final route = GeoJsonRoute(geometry: geometry);
```

#### 2. Polyline Codificado (Mapbox/Google)
```dart
// Polyline obtido de uma API de roteamento
final geometry = "y~m~Fvro}O_a@vBmB~AqApCwB}CwA";

final route = GeoJsonRoute(geometry: geometry);
```

### Comportamento de Recálculo

Quando você usa navegação com GEOJSON:

1. **Rota Exibida**: A rota GEOJSON é exibida na cor especificada (padrão: amarelo)
2. **Navegação Ativa**: O usuário segue a rota GEOJSON
3. **Saída da Rota**: Se o motorista sair da rota:
   - Sistema detecta automaticamente
   - Calcula o ponto mais próximo na rota GEOJSON original
   - Recalcula caminho para retornar a esse ponto
   - **Nunca abandona a rota GEOJSON original**
4. **Retorno à Rota**: Assim que retorna à rota, continua navegando no GEOJSON

### Exemplo Completo: Rota de Entrega

```dart
class DeliveryRoute extends StatefulWidget {
  @override
  _DeliveryRouteState createState() => _DeliveryRouteState();
}

class _DeliveryRouteState extends State<DeliveryRoute> {

  Future<void> _startDeliveryRoute() async {
    // Rota fixa de entrega obtida do seu backend
    final deliveryGeoJson = await _getDeliveryRouteFromBackend();

    final geoJsonRoute = GeoJsonRoute(
      geometry: deliveryGeoJson,
      routeColor: "#FF6B00",  // Laranja para rotas de entrega
      routeName: "Entrega Zona Sul"
    );

    final options = MapBoxOptions(
      mode: MapBoxNavigationMode.driving,
      language: "pt-BR",
      units: VoiceUnits.metric,
      voiceInstructionsEnabled: true,
      bannerInstructionsEnabled: true,
      simulateRoute: false,
    );

    // Registrar eventos
    MapBoxNavigation.instance.registerRouteEventListener(_onRouteEvent);

    // Iniciar navegação
    await MapBoxNavigation.instance.startNavigationWithGeoJson(
      geoJsonRoute: geoJsonRoute,
      options: options
    );
  }

  Future<String> _getDeliveryRouteFromBackend() async {
    // Obter rota do seu servidor/API
    final response = await http.get('https://api.suaempresa.com/delivery-routes/123');
    final data = json.decode(response.body);

    // Retornar geometria GeoJSON
    return data['route']['geometry'];
  }

  void _onRouteEvent(RouteEvent e) {
    switch (e.eventType) {
      case MapBoxEvent.user_off_route:
        // Motorista saiu da rota - sistema recalcula automaticamente
        // para voltar ao ponto mais próximo da rota GEOJSON
        print("⚠️  Fora da rota - recalculando para retornar à rota de entrega...");
        break;

      case MapBoxEvent.reroute_along:
        // Rota recalculada para voltar à rota GEOJSON
        print("✅ Recalculado caminho para retornar à rota de entrega");
        break;

      case MapBoxEvent.on_arrival:
        print("🎯 Entrega concluída!");
        break;
    }
  }
}
```

### Conversão de Waypoints para GEOJSON

Se você já tem waypoints e quer convertê-los para GEOJSON:

```dart
String waypointsToGeoJson(List<WayPoint> waypoints) {
  final coordinates = waypoints.map((wp) =>
    [wp.longitude, wp.latitude]
  ).toList();

  final geoJson = {
    "type": "LineString",
    "coordinates": coordinates
  };

  return json.encode(geoJson);
}

// Uso
final waypoints = [
  WayPoint(name: "A", latitude: -23.550520, longitude: -46.633308),
  WayPoint(name: "B", latitude: -23.548943, longitude: -46.638818),
];

final geometry = waypointsToGeoJson(waypoints);
final route = GeoJsonRoute(geometry: geometry);
```

### Diferenças: Waypoints vs GEOJSON

| Aspecto | **Waypoints** | **GEOJSON** |
|---------|--------------|-------------|
| **Definição** | Pontos A, B, C | Linha completa com todos os pontos |
| **Roteamento** | Calculado pelo Mapbox | Predefinido/fixo |
| **Flexibilidade** | Múltiplas rotas possíveis | Rota única específica |
| **Recálculo** | Nova rota completa | Volta ao ponto mais próximo da rota original |
| **Uso Ideal** | Navegação geral | Rotas fixas (entregas, tours, etc) |

### Customização Visual

```dart
final geoJsonRoute = GeoJsonRoute(
  geometry: myGeometry,
  routeColor: "#00FF00",  // Verde
  routeName: "Rota Turística Centro Histórico"
);

final options = MapBoxOptions(
  // ... outras configurações

  // A rota GEOJSON será exibida na cor especificada
  // durante toda a navegação, mesmo após recálculos
);
```

### Importante: Garantias do Sistema

Ao usar navegação com GEOJSON:

✅ **A rota NUNCA usa lat/lng isolados** - sempre obedece a geometria GEOJSON
✅ **Recálculos sempre voltam à rota original** - não cria rotas alternativas
✅ **A rota fica visível durante toda navegação** - na cor especificada
✅ **Funciona offline** (se os tiles do mapa estiverem baixados)
✅ **Suporta polyline codificado ou GeoJSON puro**

## Nova Funcionalidade: Layer de Rota Guia

Esta versão introduz um sistema de rota guia que simplifica a navegação:

### Características do Sistema
- **Rota Guia Amarela**: A rota enviada fica sempre visível em amarelo como referência fixa
- **Rota Navegação Azul**: Rota ativa de navegação que recalcula automaticamente quando necessário
- **Interface Limpa**: Sem rotas alternativas em cinza, mantendo o mapa limpo
- **Comportamento Automático**: Recálculo transparente da rota azul quando o usuário se desvia
- **Referência Visual**: A rota amarela serve como guia do caminho originalmente planejado

### Configuração do Layer de Rota Guia

```dart
final opcoes = MapBoxOptions(
  // Configurações existentes...
  mode: MapBoxNavigationMode.drivingWithTraffic,
  language: "pt-BR",
  units: VoiceUnits.metric,
  
  // Configurações do sistema de rota guia
  showPlannedRoute: true,                    // Ativar layer amarelo (padrão: true)
  plannedRouteColor: "#FFFF00",              // Cor do guia (padrão: amarelo)
  autoRecalculateOnDeviation: true,          // Recálculo automático (padrão: true)
);

await MapBoxNavigation.instance.startNavigation(
  wayPoints: waypoints,
  options: opcoes
);
```

### Como Funciona

1. **Ao iniciar navegação**: 
   - Rota enviada aparece em **amarelo (guia fixo)** + **azul (navegação ativa)**
   - Sem rotas alternativas em cinza

2. **Durante navegação**:
   - Se usuário segue a rota: apenas rota azul é visível sobre a amarela
   - Se usuário se desvia: rota azul recalcula automaticamente
   - Rota amarela **sempre permanece fixa** como referência visual

3. **Benefícios**:
   - **Motorista vê o caminho original planejado** (amarelo)
   - **Sistema navega pela melhor rota atual** (azul)
   - **Interface limpa** sem confusão de múltiplas rotas cinza

### Eventos do Sistema

```dart
MapBoxNavigation.instance.registerRouteEventListener((e) {
  switch (e.eventType) {
    case MapBoxEvent.user_off_route:
      // Sistema automático: rota azul recalcula, amarela permanece fixa
      debugPrint("🔄 Recalculando rota de navegação...");
      break;
      
    case MapBoxEvent.reroute_along:
      // Rota azul foi recalculada, rota amarela permanece como guia
      debugPrint("🆕 Nova rota de navegação calculada - guia amarelo mantido");
      break;
  }
});
```

### Métodos para Gerenciar o Layer Guia

```dart
// Obter a rota guia atual
final plannedRoute = MapBoxNavigation.instance.plannedRoute;

// Definir rota guia manualmente (opcional)
MapBoxNavigation.instance.setPlannedRoute(waypoints);

// Limpar rota guia
MapBoxNavigation.instance.clearPlannedRoute();
```

### Principais Diferenças

| Aspecto | Sistema Anterior | **Novo Sistema de Layer Guia** |
|---------|------------------|--------------------------------|
| **Rota Amarela** | Rota planejada com eventos complexos | **Layer fixo de referência visual** |
| **Rota Azul** | Navegação com alertas | **Navegação com recálculo automático** |
| **Rotas Cinza** | Alternativas mostradas | **Desabilitadas para interface limpa** |
| **Comportamento** | Eventos manuais de desvio | **Totalmente automático** |
| **Complexidade** | Diálogos e escolhas do usuário | **Transparente ao usuário** |

### Exemplo de Implementação Simplificada

```dart
class NavigationScreen extends StatefulWidget {
  @override
  _NavigationScreenState createState() => _NavigationScreenState();
}

class _NavigationScreenState extends State<NavigationScreen> {
  
  void _startNavigationWithGuideLayer() async {
    final waypoints = [
      WayPoint(name: "Casa", latitude: -23.550520, longitude: -46.633308),
      WayPoint(name: "Trabalho", latitude: -23.548943, longitude: -46.638818),
    ];
    
    final options = MapBoxOptions(
      // Configuração do layer de rota guia
      showPlannedRoute: true,                    // Layer amarelo ativo
      plannedRouteColor: "#FFFF00",              // Cor amarela
      autoRecalculateOnDeviation: true,          // Recálculo automático
      
      // Configurações gerais
      language: "pt-BR",
      units: VoiceUnits.metric,
      mode: MapBoxNavigationMode.drivingWithTraffic,
    );
    
    // Registrar listener para eventos (opcional)
    MapBoxNavigation.instance.registerRouteEventListener(_onRouteEvent);
    
    // Iniciar navegação - automático a partir daqui
    await MapBoxNavigation.instance.startNavigation(
      wayPoints: waypoints,
      options: options
    );
  }
  
  void _onRouteEvent(RouteEvent e) {
    switch (e.eventType) {
      case MapBoxEvent.user_off_route:
        print("🔄 Sistema recalculando rota automaticamente...");
        break;
      case MapBoxEvent.reroute_along:
        print("✅ Nova rota calculada - guia amarelo mantido!");
        break;
    }
  }
}

// Resultado Visual:
// 🟡 Linha amarela = Rota original enviada (sempre fixa)
// 🔵 Linha azul = Rota de navegação ativa (recalcula quando necessário)
// ❌ Sem linhas cinza = Interface limpa
```

## A Fazer
* [FEITO] Implementação Android
* [FEITO] Adicionar mais configurações como Modo de Navegação (condução, caminhada, etc)
* [FEITO] Stream de Eventos como notificações de navegação relevantes, métricas, localização atual, etc.
* [FEITO] Visualização de Navegação Incorporável
* [FEITO] Suporte completo para waypoints personalizados
* [FEITO] Interface otimizada sem botões desnecessários
* [FEITO] Sistema de Rota Planejada com detecção de desvio
* [FEITO] Avisos automáticos quando o motorista sai da rota planejada
* [FEITO] Funcionalidade de retorno à rota planejada original
* [FEITO] Suporte para rotas GEOJSON predefinidas com recálculo inteligente
* [FEITO] Melhorias na renderização visual da rota planejada (cores customizáveis, layer fixo)
* [FEITO] Suporte offline para rotas GEOJSON (funciona com tiles baixados)

<!-- Links -->
[pub_badge]: https://img.shields.io/pub/v/flutter_mapbox_navigation.svg
[pub]: https://pub.dev/packages/flutter_mapbox_navigation
[buy_me_a_coffee]: https://www.buymeacoffee.com/eopeter
[buy_me_a_coffee_badge]: https://img.buymeacoffee.com/button-api/?text=Donate&emoji=&slug=eopeter&button_colour=29b6f6&font_colour=000000&font_family=Cookie&outline_colour=000000&coffee_colour=FFDD00
