# 🚨 Guia: Capturar Evento de Off-Route (80m da Linha Verde)

## 📋 Como Funciona

O plugin agora envia automaticamente o evento `MapBoxEvent.USER_OFF_ROUTE` quando o motorista se afasta **mais de 80 metros** da linha verde (rota planejada).

---

## 💻 Implementação no App GODRIVERS

### Opção 1: Listener Simples

```dart
import 'package:flutter_mapbox_navigation/flutter_mapbox_navigation.dart';

class NavigationService {

  void startListeningToOffRouteEvents() {
    MapBoxNavigation.instance.registerRouteEventListener((RouteEvent event) {

      if (event.eventType == MapBoxEvent.USER_OFF_ROUTE) {
        // Motorista saiu da rota (> 80m da linha verde)
        _handleOffRoute();
      }

    });
  }

  void _handleOffRoute() {
    print('🚨 MOTORISTA FORA DA ROTA!');

    // Suas ações aqui:
    // 1. Mostrar alerta visual
    // 2. Emitir som/vibração
    // 3. Notificar backend
    // 4. Registrar no log
  }
}
```

---

### Opção 2: Implementação Completa com Backend

```dart
class NavigationService {
  final SignalRService _signalR;
  final RouteLogger _logger;

  bool _isOffRoute = false;
  DateTime? _offRouteStartTime;

  void startListeningToOffRouteEvents() {
    MapBoxNavigation.instance.registerRouteEventListener((RouteEvent event) {

      switch (event.eventType) {

        case MapBoxEvent.USER_OFF_ROUTE:
          _handleOffRoute();
          break;

        case MapBoxEvent.REROUTE_ALONG:
          _handleReroute();
          break;

        default:
          break;
      }
    });
  }

  void _handleOffRoute() {
    if (_isOffRoute) return; // Já estamos off-route

    _isOffRoute = true;
    _offRouteStartTime = DateTime.now();

    print('🚨 MOTORISTA SAIU DA ROTA PLANEJADA (>80m da linha verde)');

    // 1. Mostrar alerta visual no app
    _showOffRouteAlert();

    // 2. Notificar backend via SignalR
    _notifyBackendOffRoute();

    // 3. Registrar no log local
    _logger.logOffRoute(
      timestamp: _offRouteStartTime!,
      reason: 'Distância > 80m da rota planejada',
    );

    // 4. Vibrar/emitir som (opcional)
    _alertDriver();
  }

  void _handleReroute() {
    if (!_isOffRoute) return; // Não estávamos off-route

    final offRouteDuration = DateTime.now().difference(_offRouteStartTime!);

    print('✅ MOTORISTA RETORNOU À ROTA');
    print('⏱️ Tempo fora da rota: ${offRouteDuration.inSeconds}s');

    _isOffRoute = false;

    // Notificar backend que retornou
    _notifyBackendBackOnRoute(offRouteDuration);

    // Registrar retorno no log
    _logger.logBackOnRoute(
      timestamp: DateTime.now(),
      durationOffRoute: offRouteDuration,
    );
  }

  void _showOffRouteAlert() {
    // Mostrar snackbar, dialog, ou banner
    // Exemplo:
    Get.snackbar(
      'Fora da Rota',
      'Você saiu da rota planejada. Siga as instruções para retornar.',
      backgroundColor: Colors.orange,
      colorText: Colors.white,
      icon: Icon(Icons.warning, color: Colors.white),
      duration: Duration(seconds: 5),
    );
  }

  Future<void> _notifyBackendOffRoute() async {
    try {
      await _signalR.invoke('NotifyOffRoute', args: [
        {
          'motoristaId': currentDriverId,
          'rotaId': currentRouteId,
          'timestamp': DateTime.now().toIso8601String(),
          'distanciaEstimada': '> 80m',
        }
      ]);
      print('✅ Backend notificado: motorista fora da rota');
    } catch (e) {
      print('❌ Erro ao notificar backend: $e');
    }
  }

  Future<void> _notifyBackendBackOnRoute(Duration offRouteDuration) async {
    try {
      await _signalR.invoke('NotifyBackOnRoute', args: [
        {
          'motoristaId': currentDriverId,
          'rotaId': currentRouteId,
          'timestamp': DateTime.now().toIso8601String(),
          'tempoForaDaRota': offRouteDuration.inSeconds,
        }
      ]);
      print('✅ Backend notificado: motorista retornou à rota');
    } catch (e) {
      print('❌ Erro ao notificar backend: $e');
    }
  }

  void _alertDriver() {
    // Vibrar
    Vibration.vibrate(duration: 500);

    // Ou tocar som de alerta
    // AudioPlayer().play('assets/sounds/off_route_alert.mp3');
  }
}
```

---

### Opção 3: Integração com Estado Global (Provider/Bloc)

```dart
class NavigationController extends GetxController {

  final isOffRoute = false.obs;
  final offRouteDistance = 0.0.obs;

  @override
  void onInit() {
    super.onInit();
    _setupRouteListener();
  }

  void _setupRouteListener() {
    MapBoxNavigation.instance.registerRouteEventListener((event) {

      if (event.eventType == MapBoxEvent.USER_OFF_ROUTE) {
        isOffRoute.value = true;

        // Atualizar UI automaticamente
        Get.snackbar(
          '⚠️ Fora da Rota',
          'Retorne à rota planejada',
          backgroundColor: Colors.orange,
        );

        // Registrar evento
        _logOffRouteEvent();
      }

      if (event.eventType == MapBoxEvent.REROUTE_ALONG) {
        isOffRoute.value = false;
        Get.snackbar(
          '✅ De Volta',
          'Você retornou à rota',
          backgroundColor: Colors.green,
        );
      }
    });
  }

  void _logOffRouteEvent() {
    final offRouteEvent = {
      'timestamp': DateTime.now().toIso8601String(),
      'event': 'OFF_ROUTE',
      'threshold': '80m',
    };

    // Salvar no banco local
    database.insert('route_events', offRouteEvent);

    // Enviar para backend
    api.sendOffRouteEvent(offRouteEvent);
  }
}
```

E no Widget:

```dart
class NavigationScreen extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final controller = Get.find<NavigationController>();

    return Scaffold(
      body: Stack(
        children: [
          MapBoxNavigationView(...),

          // Banner de alerta
          Obx(() => controller.isOffRoute.value
            ? Container(
                color: Colors.orange,
                padding: EdgeInsets.all(16),
                child: Row(
                  children: [
                    Icon(Icons.warning, color: Colors.white),
                    SizedBox(width: 8),
                    Text(
                      '⚠️ Fora da rota planejada - Retorne à linha verde',
                      style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                    ),
                  ],
                ),
              )
            : SizedBox.shrink()
          ),
        ],
      ),
    );
  }
}
```

---

## 🎯 Resumo dos Eventos

| Evento | Quando Ocorre | O Que Fazer |
|--------|---------------|-------------|
| `USER_OFF_ROUTE` | Motorista > 80m da linha verde | Alertar, notificar backend, registrar |
| `REROUTE_ALONG` | Sistema recalcula rota | Pode significar retorno à rota |

---

## 📊 Dados que Você Pode Coletar

```dart
class OffRouteLog {
  final DateTime timestamp;
  final int motoristaId;
  final int rotaId;
  final double? distanciaEstimada;
  final LatLng? posicaoAtual;
  final Duration? tempoForaDaRota;

  // Salvar no banco local
  // Enviar para backend depois
}
```

---

## ✅ Checklist de Implementação

- [ ] Registrar listener de eventos
- [ ] Implementar handler para `USER_OFF_ROUTE`
- [ ] Mostrar alerta visual ao motorista
- [ ] Notificar backend via SignalR/HTTP
- [ ] Registrar evento no log local
- [ ] (Opcional) Vibrar/emitir som
- [ ] Implementar handler para retorno à rota
- [ ] Testar com diferentes distâncias

---

## 🐛 Debug

Para testar, adicione logs:

```dart
MapBoxNavigation.instance.registerRouteEventListener((event) {
  print('📍 Evento recebido: ${event.eventType}');
  print('📍 Dados: ${event.data}');

  if (event.eventType == MapBoxEvent.USER_OFF_ROUTE) {
    print('🚨 OFF ROUTE DETECTADO!');
    // Seu código aqui
  }
});
```

Nos logs do Android você verá:

```
D/OffRoutePlanned: ✅ ON PLANNED ROUTE: 45m from green line
D/OffRoutePlanned: ✅ ON PLANNED ROUTE: 62m from green line
W/OffRoutePlanned: 🚨 OFF PLANNED ROUTE: 95m from green line (threshold: 80m)
E/OffRoutePlanned: ❌ ENTERED OFF-ROUTE STATE - Distance: 95m
```

---

## 🚀 Pronto!

Agora seu app consegue detectar quando o motorista sai **80 metros** da linha verde e tomar as ações necessárias! 🎉
