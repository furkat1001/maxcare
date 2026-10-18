import '../core/api_client.dart';
import 'app_usage_repository.dart';

/// Группы приложений — категории Android на телефоне ребёнка. Значение уходит на сервер.
enum LimitGroup {
  video('video'),
  social('social'),
  game('game'),
  audio('audio');

  const LimitGroup(this.wire);
  final String wire;

  static LimitGroup? parse(String value) {
    for (final g in values) {
      if (g.wire == value) return g;
    }
    return null;
  }
}

/// Правило: группа ([group]) или одно приложение ([packageName]), дневной лимит и/или цикл
/// «сессия / перерыв». Формат — `docs/backend-api.md`, «Ограничение времени в приложениях».
class LimitRule {
  const LimitRule({
    this.group,
    this.packageName,
    this.dailyLimitMin,
    this.sessionMin,
    this.breakMin,
  }) : assert((group == null) != (packageName == null));

  /// Неизвестный вид или группа (из будущей версии сервера) — null, правило пропускается.
  static LimitRule? fromJson(Map<String, dynamic> json) {
    final target = json['target'] as String;
    final daily = (json['dailyLimitMin'] as num?)?.toInt();
    final session = (json['sessionMin'] as num?)?.toInt();
    final pause = (json['breakMin'] as num?)?.toInt();
    switch (json['kind']) {
      case 'app':
        return LimitRule(
          packageName: target,
          dailyLimitMin: daily,
          sessionMin: session,
          breakMin: pause,
        );
      case 'group':
        final group = LimitGroup.parse(target);
        return group == null
            ? null
            : LimitRule(
                group: group,
                dailyLimitMin: daily,
                sessionMin: session,
                breakMin: pause,
              );
    }
    return null;
  }

  final LimitGroup? group;
  final String? packageName;
  final int? dailyLimitMin;
  final int? sessionMin;
  final int? breakMin;

  /// Одно правило на цель: на сервере повтор `(kind, target)` запрещён.
  String get key => group != null ? 'group:${group!.wire}' : 'app:$packageName';

  Map<String, dynamic> toJson() => {
    'kind': group != null ? 'group' : 'app',
    'target': group?.wire ?? packageName,
    if (dailyLimitMin != null) 'dailyLimitMin': dailyLimitMin,
    if (sessionMin != null) 'sessionMin': sessionMin,
    if (breakMin != null) 'breakMin': breakMin,
  };
}

/// Набор ограничений телефона. [alwaysAllowed] этот экран не меняет, но сохраняет как есть.
class AppLimits {
  const AppLimits({this.rules = const [], this.alwaysAllowed = const []});

  factory AppLimits.fromJson(Map<String, dynamic> json) => AppLimits(
    rules: (json['rules'] as List<dynamic>? ?? const [])
        .map((e) => LimitRule.fromJson(e as Map<String, dynamic>))
        .whereType<LimitRule>()
        .toList(),
    alwaysAllowed: (json['alwaysAllowed'] as List<dynamic>? ?? const [])
        .whereType<String>()
        .toList(),
  );

  final List<LimitRule> rules;
  final List<String> alwaysAllowed;

  /// Заменить правило с тем же [LimitRule.key] или добавить новое.
  AppLimits put(LimitRule rule) {
    final index = rules.indexWhere((r) => r.key == rule.key);
    final next = [...rules];
    if (index >= 0) {
      next[index] = rule;
    } else {
      next.add(rule);
    }
    return AppLimits(rules: next, alwaysAllowed: alwaysAllowed);
  }

  AppLimits remove(String key) => AppLimits(
    rules: rules.where((r) => r.key != key).toList(),
    alwaysAllowed: alwaysAllowed,
  );

  Map<String, dynamic> toJson() => {
    'rules': rules.map((r) => r.toJson()).toList(),
    'alwaysAllowed': alwaysAllowed,
  };
}

/// Ограничения времени: родитель задаёт набор целиком, телефон считает и блокирует сам.
class AppLimitsRepository {
  AppLimitsRepository(this._api, this._usage);

  final ApiClient _api;
  final AppUsageRepository _usage;

  Future<AppLimits> get(String deviceId) async =>
      AppLimits.fromJson(await _api.get('/v1/devices/$deviceId/app-limits'));

  Future<AppLimits> save(String deviceId, AppLimits limits) async =>
      AppLimits.fromJson(
        await _api.put(
          '/v1/devices/$deviceId/app-limits',
          body: limits.toJson(),
        ),
      );

  /// Приложения, которыми ребёнок пользовался за последние [days] дня: пакет → название,
  /// от самых долгих. Из них родитель выбирает приложение для правила.
  Future<Map<String, String>> recentApps(
    String deviceId, {
    int days = 3,
    DateTime? now,
  }) async {
    final today = now ?? DateTime.now();
    final totals = <String, Duration>{};
    final labels = <String, String>{};
    for (var d = 0; d < days; d++) {
      final day = await _usage.day(
        deviceId,
        DateTime(today.year, today.month, today.day - d),
      );
      for (final app in day.apps) {
        totals[app.packageName] =
            (totals[app.packageName] ?? Duration.zero) + app.foreground;
        labels.putIfAbsent(app.packageName, () => app.label);
      }
    }
    final ordered = totals.keys.toList()
      ..sort((a, b) => totals[b]!.compareTo(totals[a]!));
    return {for (final pkg in ordered) pkg: labels[pkg]!};
  }
}
