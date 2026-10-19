import '../core/api_client.dart';

/// Сколько приложение было на экране телефона ребёнка за день.
class AppUsage {
  AppUsage({required this.packageName, required this.label, required this.foreground});

  factory AppUsage.fromJson(Map<String, dynamic> json) => AppUsage(
        packageName: json['packageName'] as String,
        label: json['label'] as String,
        foreground: Duration(milliseconds: (json['foregroundMs'] as num).toInt()),
      );

  final String packageName;

  /// Название приложения, как его видит ребёнок.
  final String label;
  final Duration foreground;
}

/// Время в приложениях за один день (по местному времени телефона).
class DayAppUsage {
  DayAppUsage({required this.apps, this.updatedAt});

  factory DayAppUsage.fromJson(Map<String, dynamic> json) => DayAppUsage(
        updatedAt: switch (json['updatedAt']) {
          final String s => DateTime.parse(s),
          _ => null,
        },
        apps: (json['apps'] as List<dynamic>)
            .map((e) => AppUsage.fromJson(e as Map<String, dynamic>))
            .toList(),
      );

  /// По убыванию времени.
  final List<AppUsage> apps;

  /// Когда пришёл последний снимок дня; null — за день данных нет совсем.
  final DateTime? updatedAt;

  Duration get total => apps.fold(Duration.zero, (sum, app) => sum + app.foreground);
}

class AppUsageRepository {
  AppUsageRepository(this._api);

  final ApiClient _api;

  /// Время в приложениях за календарный день [date] (берётся только дата).
  Future<DayAppUsage> day(String deviceId, DateTime date) async {
    final json = await _api.get('/v1/devices/$deviceId/app-usage?date=${isoDate(date)}');
    return DayAppUsage.fromJson(json);
  }
}

/// Дата в виде `2026-10-15` — так её ждёт сервер.
String isoDate(DateTime date) => '${date.year.toString().padLeft(4, '0')}-'
    '${date.month.toString().padLeft(2, '0')}-'
    '${date.day.toString().padLeft(2, '0')}';
