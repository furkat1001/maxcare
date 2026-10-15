import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
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

class _StubAdapter implements HttpClientAdapter {
  _StubAdapter(this.body);
  final Object? body;
  RequestOptions? lastRequest;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    lastRequest = options;
    return ResponseBody.fromString(
      jsonEncode(body),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

(AppUsageRepository, _StubAdapter) _repo(Object? body) {
  final adapter = _StubAdapter(body);
  final dio = Dio(BaseOptions(
    baseUrl: 'http://test',
    validateStatus: (s) => s != null && s < 500,
  ))
    ..httpClientAdapter = adapter;
  return (AppUsageRepository(ApiClient(tokenStore: _FakeTokenStore(), dio: dio)), adapter);
}

void main() {
  // Синтетические приложения и минуты — не данные настоящего ребёнка.
  test('day parses apps, total and update time', () async {
    final (repo, adapter) = _repo({
      'date': '2026-10-15',
      'updatedAt': '2026-10-15T09:45:00Z',
      'apps': [
        {'packageName': 'com.google.android.youtube', 'label': 'YouTube', 'foregroundMs': 2400000},
        {'packageName': 'com.android.chrome', 'label': 'Chrome', 'foregroundMs': 900000},
      ],
    });

    final day = await repo.day('dev-1', DateTime(2026, 10, 15, 18, 30));

    expect(adapter.lastRequest!.uri.path, '/v1/devices/dev-1/app-usage');
    expect(adapter.lastRequest!.uri.queryParameters['date'], '2026-10-15');
    expect(day.apps.map((a) => a.label), ['YouTube', 'Chrome']);
    expect(day.apps.first.foreground, const Duration(minutes: 40));
    expect(day.total, const Duration(minutes: 55));
    expect(day.updatedAt, DateTime.utc(2026, 10, 15, 9, 45));
  });

  test('a day without data has no update time', () async {
    final (repo, _) = _repo({'date': '2026-10-14', 'updatedAt': null, 'apps': <Object>[]});

    final day = await repo.day('dev-1', DateTime(2026, 10, 14));

    expect(day.updatedAt, isNull);
    expect(day.apps, isEmpty);
    expect(day.total, Duration.zero);
  });

  test('isoDate pads month and day', () {
    expect(isoDate(DateTime(2026, 3, 7)), '2026-03-07');
  });
}
