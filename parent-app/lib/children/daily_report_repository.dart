import '../core/api_client.dart';
import 'app_usage_repository.dart';

/// Итоги дня по телефону ребёнка — сводка с сервера. Смотри `docs/backend-api.md`.
class DailyReport {
  DailyReport({
    required this.screenTotal,
    required this.topApps,
    required this.newApps,
    required this.blocked,
    required this.topSites,
    required this.limitsReached,
    required this.bypassAttempts,
    required this.protectionDisabled,
    required this.location,
    required this.boots,
    required this.shutdowns,
  });

  factory DailyReport.fromJson(Map<String, dynamic> json) {
    final screen = json['screenTime'] as Map<String, dynamic>;
    final blocked = json['blocked'] as Map<String, dynamic>;
    final phone = json['phone'] as Map<String, dynamic>;
    List<AppUsage> apps(Object? list) => (list as List<dynamic>)
        .map((e) => AppUsage.fromJson(e as Map<String, dynamic>))
        .toList();
    return DailyReport(
      screenTotal: Duration(milliseconds: (screen['totalMs'] as num).toInt()),
      topApps: apps(screen['topApps']),
      newApps: apps(screen['newApps']),
      blocked: {
        for (final category in BlockedCategory.values)
          category: (blocked[category.name] as num?)?.toInt() ?? 0,
      },
      topSites: (blocked['topSites'] as List<dynamic>).cast<String>(),
      limitsReached: (json['limitsReached'] as List<dynamic>)
          .map((e) => LimitHit.fromJson(e as Map<String, dynamic>))
          .toList(),
      bypassAttempts: (json['bypassAttempts'] as List<dynamic>)
          .map((e) => EventCount.fromJson(e as Map<String, dynamic>))
          .toList(),
      protectionDisabled: (json['protectionDisabled'] as num).toInt(),
      location: switch (json['location']) {
        final Map<String, dynamic> l => RouteSummary.fromJson(l),
        _ => null,
      },
      boots: (phone['boots'] as num).toInt(),
      shutdowns: (phone['shutdowns'] as num).toInt(),
    );
  }

  final Duration screenTotal;
  final List<AppUsage> topApps;

  /// Приложения, которых не было в прошлые дни; пусто в первый день наблюдения.
  final List<AppUsage> newApps;
  final Map<BlockedCategory, int> blocked;
  final List<String> topSites;
  final List<LimitHit> limitsReached;
  final List<EventCount> bypassAttempts;
  final int protectionDisabled;
  final RouteSummary? location;
  final int boots;
  final int shutdowns;

  int get blockedTotal => blocked.values.fold(0, (sum, n) => sum + n);
}

/// Имена совпадают с полями `blocked` в ответе сервера.
enum BlockedCategory { sites, gambling, search, images, messenger }

class LimitHit {
  LimitHit({required this.packageName, required this.label, required this.count});

  factory LimitHit.fromJson(Map<String, dynamic> json) => LimitHit(
        packageName: json['packageName'] as String,
        label: json['label'] as String?,
        count: (json['count'] as num).toInt(),
      );

  final String packageName;
  final String? label;
  final int count;
}

class EventCount {
  EventCount({required this.type, required this.count, required this.lastAt});

  factory EventCount.fromJson(Map<String, dynamic> json) => EventCount(
        type: json['type'] as String,
        count: (json['count'] as num).toInt(),
        lastAt: DateTime.parse(json['lastAt'] as String),
      );

  final String type;
  final int count;
  final DateTime lastAt;
}

class RouteSummary {
  RouteSummary({
    required this.points,
    required this.distanceMeters,
    required this.firstAt,
    required this.lastAt,
  });

  factory RouteSummary.fromJson(Map<String, dynamic> json) => RouteSummary(
        points: (json['points'] as num).toInt(),
        distanceMeters: (json['distanceMeters'] as num).toInt(),
        firstAt: DateTime.parse(json['firstAt'] as String),
        lastAt: DateTime.parse(json['lastAt'] as String),
      );

  final int points;
  final int distanceMeters;
  final DateTime firstAt;
  final DateTime lastAt;
}

class DailyReportRepository {
  DailyReportRepository(this._api, {Duration Function()? utcOffset})
      : _utcOffset = utcOffset ?? (() => DateTime.now().timeZoneOffset);

  final ApiClient _api;
  final Duration Function() _utcOffset;

  /// Итоги дня [date] по часовому поясу этого телефона (родителя).
  Future<DailyReport> day(String deviceId, DateTime date) async {
    final offset = _utcOffset().inMinutes;
    final json = await _api.get(
      '/v1/devices/$deviceId/daily-report?date=${isoDate(date)}&offsetMinutes=$offset',
    );
    return DailyReport.fromJson(json);
  }
}
