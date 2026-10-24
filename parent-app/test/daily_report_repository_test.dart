import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parent/children/daily_report_repository.dart';
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
  final Object body;
  final requests = <RequestOptions>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
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

// Синтетический отчёт: пакеты com.example.*, адреса example.com.
const _report = {
  'date': '2026-10-23',
  'screenTime': {
    'totalMs': 5400000,
    'topApps': [
      {'packageName': 'com.example.video', 'label': 'Video', 'foregroundMs': 3600000},
    ],
    'newApps': [
      {'packageName': 'com.example.game', 'label': 'Game', 'foregroundMs': 600000},
    ],
  },
  'blocked': {
    'sites': 3,
    'gambling': 1,
    'search': 2,
    'images': 0,
    'messenger': 0,
    'topSites': ['m.example.com'],
  },
  'limitsReached': [
    {'packageName': 'com.example.video', 'label': 'Video', 'count': 2},
  ],
  'bypassAttempts': [
    {'type': 'vpn_off', 'count': 1, 'lastAt': '2026-10-23T10:00:00Z'},
  ],
  'protectionDisabled': 0,
  'location': {
    'points': 12,
    'distanceMeters': 3400,
    'firstAt': '2026-10-23T03:00:00Z',
    'lastAt': '2026-10-23T14:00:00Z',
  },
  'phone': {'boots': 1, 'shutdowns': 1, 'lastSeenAt': '2026-10-23T14:05:00Z'},
};

void main() {
  test('report is requested for the parent day and parsed', () async {
    final adapter = _StubAdapter(_report);
    final dio = Dio(BaseOptions(baseUrl: 'http://test'))..httpClientAdapter = adapter;
    final repo = DailyReportRepository(
      ApiClient(tokenStore: _FakeTokenStore(), dio: dio),
      utcOffset: () => const Duration(hours: 5),
    );

    final report = await repo.day('dev-1', DateTime(2026, 10, 23));

    final uri = adapter.requests.single.uri;
    expect(uri.path, '/v1/devices/dev-1/daily-report');
    expect(uri.queryParameters, {'date': '2026-10-23', 'offsetMinutes': '300'});
    expect(report.screenTotal, const Duration(minutes: 90));
    expect(report.newApps.single.label, 'Game');
    expect(report.blocked[BlockedCategory.sites], 3);
    expect(report.blockedTotal, 6);
    expect(report.limitsReached.single.count, 2);
    expect(report.bypassAttempts.single.type, 'vpn_off');
    expect(report.location?.distanceMeters, 3400);
    expect(report.boots, 1);
  });

  test('a day without route has no location section', () async {
    final noRoute = Map<String, Object?>.from(_report)..['location'] = null;
    final dio = Dio(BaseOptions(baseUrl: 'http://test'))
      ..httpClientAdapter = _StubAdapter(noRoute);
    final repo = DailyReportRepository(ApiClient(tokenStore: _FakeTokenStore(), dio: dio));

    final report = await repo.day('dev-1', DateTime(2026, 10, 23));

    expect(report.location, isNull);
  });
}
