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

### v1.0.0+customizada com Rota Planejada
- **✅ Removido botão "Cancel" desnecessário** da interface de navegação embarcada
- **✅ Adicionado suporte completo para waypoints personalizados** com documentação em português
- **✅ Melhorada experiência do usuário** com interface mais limpa
- **✅ Documentação traduzida** para português brasileiro
- **✅ Exemplos práticos** para uso de waypoints em cenários reais
- **🆕 Sistema de Rota Planejada** - Nova funcionalidade para mostrar a rota original
- **🆕 Detecção de Desvio** - Alerta quando o motorista sai da rota planejada
- **🆕 Navegação de Retorno** - Opção para voltar à rota planejada original

## Nova Funcionalidade: Sistema de Rota Planejada

Esta versão introduz um sistema avançado de rota planejada que permite:

### Características da Rota Planejada
- **Rota Fixa em Amarelo**: A primeira rota calculada fica sempre visível em amarelo no mapa
- **Camada Inferior**: A rota planejada aparece por baixo da rota de navegação ativa
- **Persistência**: Permanece visível mesmo durante recálculo de rotas
- **Detecção Automática**: Sistema detecta quando o motorista sai da rota planejada
- **Avisos Inteligentes**: Notificações quando há desvio da rota original

### Configuração da Rota Planejada

```dart
final opcoes = MapBoxOptions(
  // Configurações existentes...
  mode: MapBoxNavigationMode.drivingWithTraffic,
  language: "pt-BR",
  units: VoiceUnits.metric,
  
  // Novas opções de rota planejada
  showPlannedRoute: true,              // Ativar rota planejada (padrão: true)
  plannedRouteColor: "#FFFF00",        // Cor da rota planejada (padrão: amarelo)
  offRouteWarningEnabled: true,        // Avisos de desvio (padrão: true)
);

await MapBoxNavigation.instance.startNavigation(
  wayPoints: waypoints,
  options: opcoes
);
```

### Eventos da Rota Planejada

```dart
MapBoxNavigation.instance.registerRouteEventListener((e) {
  switch (e.eventType) {
    case MapBoxEvent.off_planned_route:
      // Motorista saiu da rota planejada
      if (e.data is OffRouteEvent) {
        final offRouteData = e.data as OffRouteEvent;
        print("⚠️ Você saiu da rota planejada!");
        
        if (offRouteData.distanceFromPlannedRoute != null) {
          print("Distância da rota: ${offRouteData.distanceFromPlannedRoute!.toStringAsFixed(0)}m");
        }
        
        // Mostrar opção para retornar à rota planejada
        showReturnToPlannedRouteDialog();
      }
      break;
      
    case MapBoxEvent.returned_to_planned_route:
      // Motorista voltou à rota planejada
      print("✅ Você retornou à rota planejada!");
      break;
  }
});
```

### Métodos para Gerenciar Rota Planejada

```dart
// Definir rota planejada manualmente
MapBoxNavigation.instance.setPlannedRoute(waypoints);

// Obter rota planejada atual
final plannedRoute = MapBoxNavigation.instance.plannedRoute;

// Calcular rota de volta para a rota planejada
await MapBoxNavigation.instance.calculateRouteToPlannedRoute();

// Limpar rota planejada
MapBoxNavigation.instance.clearPlannedRoute();
```

### Dados do Evento de Desvio

```dart
class OffRouteEvent {
  final double? distanceFromPlannedRoute;  // Distância em metros
  final WayPoint? nearestPlannedWaypoint;  // Ponto mais próximo na rota planejada
  final bool isOffPlannedRoute;            // Se está fora da rota
}
```

### Exemplo de Implementação Completa

```dart
class NavigationScreen extends StatefulWidget {
  @override
  _NavigationScreenState createState() => _NavigationScreenState();
}

class _NavigationScreenState extends State<NavigationScreen> {
  
  void _startNavigationWithPlannedRoute() async {
    final waypoints = [
      WayPoint(name: "Casa", latitude: -23.550520, longitude: -46.633308),
      WayPoint(name: "Trabalho", latitude: -23.548943, longitude: -46.638818),
    ];
    
    final options = MapBoxOptions(
      showPlannedRoute: true,
      plannedRouteColor: "#FFFF00",
      offRouteWarningEnabled: true,
      language: "pt-BR",
      units: VoiceUnits.metric,
    );
    
    // Registrar listener para eventos
    MapBoxNavigation.instance.registerRouteEventListener(_onRouteEvent);
    
    // Iniciar navegação
    await MapBoxNavigation.instance.startNavigation(
      wayPoints: waypoints,
      options: options
    );
  }
  
  void _onRouteEvent(RouteEvent e) {
    switch (e.eventType) {
      case MapBoxEvent.off_planned_route:
        _showReturnToPlannedRouteDialog();
        break;
      case MapBoxEvent.returned_to_planned_route:
        _showMessage("Você retornou à rota planejada!");
        break;
    }
  }
  
  void _showReturnToPlannedRouteDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Rota Desviada'),
        content: Text('Você saiu da rota planejada. Deseja recalcular?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('Continuar Atual'),
          ),
          ElevatedButton(
            onPressed: () async {
              Navigator.pop(context);
              await MapBoxNavigation.instance.calculateRouteToPlannedRoute();
            },
            child: Text('Voltar à Rota Planejada'),
          ),
        ],
      ),
    );
  }
}

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
* Roteamento Offline
* Melhorias na renderização visual da rota planejada

<!-- Links -->
[pub_badge]: https://img.shields.io/pub/v/flutter_mapbox_navigation.svg
[pub]: https://pub.dev/packages/flutter_mapbox_navigation
[buy_me_a_coffee]: https://www.buymeacoffee.com/eopeter
[buy_me_a_coffee_badge]: https://img.buymeacoffee.com/button-api/?text=Donate&emoji=&slug=eopeter&button_colour=29b6f6&font_colour=000000&font_family=Cookie&outline_colour=000000&coffee_colour=FFDD00
