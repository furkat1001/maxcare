import '../core/api_client.dart';

/// Телефон ребёнка в списке у родителя.
class Device {
  Device({
    required this.id,
    required this.name,
    required this.platform,
    this.childName,
    this.lastSeenAt,
  });

  factory Device.fromJson(Map<String, dynamic> json) => Device(
        id: json['id'] as String,
        name: json['name'] as String,
        platform: json['platform'] as String,
        childName: json['childName'] as String?,
        lastSeenAt: _parseTime(json['lastSeenAt']),
      );

  final String id;
  final String name;
  final String platform;
  final String? childName;
  final DateTime? lastSeenAt;

  /// Что показать в заголовке карточки: имя ребёнка, иначе имя телефона.
  String get title => (childName != null && childName!.isNotEmpty) ? childName! : name;
}

/// Одно событие в журнале устройства. [type] — проводное имя (EventType.wireName),
/// экран сам подбирает локализованный текст.
class DeviceEvent {
  DeviceEvent({
    required this.type,
    required this.occurredAt,
    required this.receivedAt,
    this.detail,
  });

  factory DeviceEvent.fromJson(Map<String, dynamic> json) => DeviceEvent(
        type: json['type'] as String,
        occurredAt: DateTime.parse(json['occurredAt'] as String),
        receivedAt: DateTime.parse(json['receivedAt'] as String),
        detail: json['detail'] as String?,
      );

  final String type;
  final DateTime occurredAt;
  final DateTime receivedAt;

  /// Для событий с сайтом — его адрес; иначе null.
  final String? detail;
}

/// Последняя активность устройства: когда телефон отмечался и его события.
class DeviceActivity {
  DeviceActivity({required this.lastSeenAt, required this.events});

  factory DeviceActivity.fromJson(Map<String, dynamic> json) => DeviceActivity(
        lastSeenAt: _parseTime(json['lastSeenAt']),
        events: (json['events'] as List<dynamic>? ?? const [])
            .whereType<Map<String, dynamic>>()
            .map(DeviceEvent.fromJson)
            .toList(),
      );

  final DateTime? lastSeenAt;
  final List<DeviceEvent> events;
}

DateTime? _parseTime(Object? value) =>
    value is String ? DateTime.tryParse(value) : null;

/// Код для сброса пароля MaxCare Kids на телефоне ребёнка. Телефон проверяет его без сети.
class RecoveryCode {
  RecoveryCode({required this.code, required this.validUntil});

  factory RecoveryCode.fromJson(Map<String, dynamic> json) => RecoveryCode(
        code: json['code'] as String,
        validUntil: DateTime.parse(json['validUntil'] as String),
      );

  final String code;
  final DateTime validUntil;

  /// «6207 4247» — так легче продиктовать и набрать.
  String get grouped =>
      code.length == 8 ? '${code.substring(0, 4)} ${code.substring(4)}' : code;
}

class DeviceRepository {
  DeviceRepository(this._api);

  final ApiClient _api;

  Future<List<Device>> list() async {
    final rows = await _api.getList('/v1/devices');
    return rows.map(Device.fromJson).toList();
  }

  Future<DeviceActivity> activity(String deviceId) async {
    final response = await _api.get('/v1/devices/$deviceId/events');
    return DeviceActivity.fromJson(response);
  }

  Future<RecoveryCode> recoveryCode(String deviceId) async {
    final response =
        await _api.post('/v1/devices/$deviceId/recovery-code', body: const {});
    return RecoveryCode.fromJson(response);
  }
}
