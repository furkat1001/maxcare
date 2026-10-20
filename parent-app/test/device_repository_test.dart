import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parent/children/device_repository.dart';
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

/// Отдаёт заранее заданное тело на любой запрос (массив или объект).
class _StubAdapter implements HttpClientAdapter {
  _StubAdapter(this.body);
  final Object? body;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async =>
      ResponseBody.fromString(
        jsonEncode(body),
        200,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );

  @override
  void close({bool force = false}) {}
}

DeviceRepository _repo(Object? body) {
  final dio = Dio(BaseOptions(
    baseUrl: 'http://test',
    validateStatus: (s) => s != null && s < 500,
  ))
    ..httpClientAdapter = _StubAdapter(body);
  return DeviceRepository(ApiClient(tokenStore: _FakeTokenStore(), dio: dio));
}

void main() {
  test('device list parses children, platform and last seen', () async {
    final repo = _repo([
      {
        'id': 'd1',
        'name': 'Pixel 7',
        'childName': 'Dilnoza',
        'platform': 'android',
        'lastSeenAt': '2026-10-11T08:00:00Z',
      },
      {
        'id': 'd2',
        'name': 'iPhone 15',
        'childName': null,
        'platform': 'ios',
        'lastSeenAt': null,
      },
    ]);

    final devices = await repo.list();

    expect(devices.length, 2);
    expect(devices[0].title, 'Dilnoza');
    expect(devices[0].platform, 'android');
    expect(devices[0].lastSeenAt, DateTime.utc(2026, 10, 11, 8));
    // Нет имени ребёнка — в заголовок идёт имя телефона.
    expect(devices[1].title, 'iPhone 15');
    expect(devices[1].lastSeenAt, isNull);
  });

  test('empty device list is handled', () async {
    expect(await _repo(<dynamic>[]).list(), isEmpty);
  });

  test('activity parses last seen and events with a blocked site', () async {
    final repo = _repo({
      'lastSeenAt': '2026-10-11T09:30:00Z',
      'events': [
        {
          'type': 'site_blocked_gambling',
          'occurredAt': '2026-10-11T09:00:00Z',
          'receivedAt': '2026-10-11T09:01:00Z',
          'detail': 'bet.example.com',
        },
        {
          'type': 'protection_disabled',
          'occurredAt': '2026-10-11T09:20:00Z',
          'receivedAt': '2026-10-11T09:21:00Z',
        },
      ],
    });

    final activity = await repo.activity('d1');

    expect(activity.lastSeenAt, DateTime.utc(2026, 10, 11, 9, 30));
    expect(activity.events.length, 2);
    expect(activity.events[0].type, 'site_blocked_gambling');
    expect(activity.events[0].detail, 'bet.example.com');
    expect(activity.events[1].detail, isNull);
  });

  test('recovery code is parsed and grouped for reading aloud', () async {
    final code = await _repo({
      'code': '62074247',
      'validUntil': '2026-10-20T12:05:00Z',
    }).recoveryCode('dev-1');

    expect(code.code, '62074247');
    expect(code.grouped, '6207 4247');
    expect(code.validUntil, DateTime.utc(2026, 10, 20, 12, 5));
  });
}
