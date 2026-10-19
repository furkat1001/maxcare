import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parent/children/app_limits_repository.dart';
import 'package:parent/children/app_usage_repository.dart';
import 'package:parent/core/api_client.dart';
import 'package:parent/core/token_store.dart';

class _FakeTokenStore implements TokenStore {
  @override
  Future<String?> read() async => 'tok';
  @override
  Future<void> write(String token) async {}
  @override
  Future<void> clear() async {}
}

/// Отвечает телом по пути запроса и запоминает запросы.
class _StubAdapter implements HttpClientAdapter {
  _StubAdapter(this.bodies);
  final Object? Function(RequestOptions) bodies;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    return ResponseBody.fromString(
      jsonEncode(bodies(options)),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

(AppLimitsRepository, _StubAdapter) _repo(Object? Function(RequestOptions) bodies) {
  final adapter = _StubAdapter(bodies);
  final dio = Dio(BaseOptions(
    baseUrl: 'http://test',
    validateStatus: (s) => s != null && s < 500,
  ))
    ..httpClientAdapter = adapter;
  final api = ApiClient(tokenStore: _FakeTokenStore(), dio: dio);
  return (AppLimitsRepository(api, AppUsageRepository(api)), adapter);
}

// Синтетические правила и приложения — не данные настоящей семьи.
const _limitsJson = {
  'rules': [
    {'kind': 'group', 'target': 'video', 'dailyLimitMin': 60, 'sessionMin': 15, 'breakMin': 15},
    {'kind': 'app', 'target': 'com.example.blockgame', 'dailyLimitMin': 90},
    {'kind': 'group', 'target': 'news', 'dailyLimitMin': 10},
  ],
  'alwaysAllowed': ['com.example.school'],
};

void main() {
  test('get parses rules and skips unknown groups', () async {
    final (repo, adapter) = _repo((_) => _limitsJson);

    final limits = await repo.get('dev-1');

    expect(adapter.requests.single.uri.path, '/v1/devices/dev-1/app-limits');
    expect(limits.rules.map((r) => r.key), ['group:video', 'app:com.example.blockgame']);
    expect(limits.rules.first.sessionMin, 15);
    expect(limits.alwaysAllowed, ['com.example.school']);
  });

  test('put replaces a rule with the same target, remove drops it', () {
    const video = LimitRule(group: LimitGroup.video, dailyLimitMin: 60);
    const limits = AppLimits(rules: [video], alwaysAllowed: ['com.example.school']);

    final changed = limits.put(const LimitRule(group: LimitGroup.video, dailyLimitMin: 30));
    expect(changed.rules.single.dailyLimitMin, 30);
    expect(changed.alwaysAllowed, ['com.example.school']);

    final added = changed.put(const LimitRule(packageName: 'com.example.blockgame', dailyLimitMin: 90));
    expect(added.rules.length, 2);
    expect(added.remove('group:video').rules.single.packageName, 'com.example.blockgame');
  });

  test('save sends the whole set without empty fields', () async {
    final (repo, adapter) = _repo((o) => o.data);
    const limits = AppLimits(
      rules: [LimitRule(group: LimitGroup.game, sessionMin: 30, breakMin: 15)],
      alwaysAllowed: ['com.example.school'],
    );

    await repo.save('dev-1', limits);

    final request = adapter.requests.single;
    expect(request.method, 'PUT');
    expect(request.data, {
      'rules': [
        {'kind': 'group', 'target': 'game', 'sessionMin': 30, 'breakMin': 15},
      ],
      'alwaysAllowed': ['com.example.school'],
    });
  });

  test('recent apps add up the days, longest first', () async {
    final (repo, _) = _repo((o) {
      final date = o.uri.queryParameters['date'];
      final apps = switch (date) {
        '2026-10-18' => [
            {'packageName': 'com.example.videoa', 'label': 'Video A', 'foregroundMs': 600000},
          ],
        '2026-10-17' => [
            {'packageName': 'com.example.blockgame', 'label': 'Block Game', 'foregroundMs': 1800000},
            {'packageName': 'com.example.videoa', 'label': 'Video A', 'foregroundMs': 600000},
          ],
        _ => <Object>[],
      };
      return {'date': date, 'updatedAt': null, 'apps': apps};
    });

    final apps = await repo.recentApps('dev-1', now: DateTime(2026, 10, 18, 12));

    expect(apps.keys, ['com.example.blockgame', 'com.example.videoa']);
    expect(apps['com.example.videoa'], 'Video A');
  });
}
