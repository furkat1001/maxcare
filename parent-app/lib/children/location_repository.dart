import '../core/api_client.dart';

/// Одна геопозиция телефона ребёнка.
class DeviceLocation {
  DeviceLocation({
    required this.latitude,
    required this.longitude,
    required this.recordedAt,
    required this.receivedAt,
    this.accuracyMeters,
  });

  factory DeviceLocation.fromJson(Map<String, dynamic> json) => DeviceLocation(
        latitude: (json['latitude'] as num).toDouble(),
        longitude: (json['longitude'] as num).toDouble(),
        accuracyMeters: (json['accuracyMeters'] as num?)?.toDouble(),
        recordedAt: DateTime.parse(json['recordedAt'] as String),
        receivedAt: DateTime.parse(json['receivedAt'] as String),
      );

  final double latitude;
  final double longitude;

  /// Радиус погрешности в метрах, если телефон его знает.
  final double? accuracyMeters;

  /// По часам телефона.
  final DateTime recordedAt;

  /// По часам сервера.
  final DateTime receivedAt;
}

class LocationRepository {
  LocationRepository(this._api);

  final ApiClient _api;

  /// Последняя известная позиция; null — телефон ещё не присылал её.
  Future<DeviceLocation?> latest(String deviceId) async {
    final json = await _api.get('/v1/devices/$deviceId/location');
    // 204 без тела — позиции ещё нет.
    return json.containsKey('latitude') ? DeviceLocation.fromJson(json) : null;
  }

  /// Трек устройства за период (по умолчанию — последние сутки), по возрастанию времени.
  /// Пустой список — телефон ещё не слал позицию за это окно.
  Future<List<DeviceLocation>> history(String deviceId, {DateTime? since}) async {
    final query = since == null
        ? ''
        : '?since=${Uri.encodeQueryComponent(since.toUtc().toIso8601String())}';
    final rows = await _api.getList('/v1/devices/$deviceId/locations$query');
    return rows.map(DeviceLocation.fromJson).toList();
  }
}

/// Точка не хуже этого (в метрах) — точная: по ней можно вести линию маршрута.
/// Позиции по Wi‑Fi и вышкам связи часто «прыгают» на сотни метров при заявленных 100–200 м.
const preciseAccuracyMeters = 100.0;

/// Точность неизвестна — считаем точной: старые телефоны её не сообщают.
bool isPrecise(DeviceLocation p) => (p.accuracyMeters ?? 0) <= preciseAccuracyMeters;
