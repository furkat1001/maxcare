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
