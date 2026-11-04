import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parent/children/device_repository.dart';
import 'package:parent/children/pairing_repository.dart';
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

/// Отдаёт заранее заданное тело на любой запрос (массив или объект) и запоминает запросы.
class _StubAdapter implements HttpClientAdapter {
  _StubAdapter(this.body);
  final Object? body;
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

late _StubAdapter _adapter;

ApiClient _api(Object? body) {
  _adapter = _StubAdapter(body);
  final dio = Dio(BaseOptions(
    baseUrl: 'http://test',
    validateStatus: (s) => s != null && s < 500,
  ))
    ..httpClientAdapter = _adapter;
  return ApiClient(tokenStore: _FakeTokenStore(), dio: dio);
}

DeviceRepository _repo(Object? body) => DeviceRepository(_api(body));

/// Последний запрос: метод, путь и токен родителя.
String _lastRequest() {
  final r = _adapter.requests.last;
  return '${r.method} ${r.path} ${r.headers['Authorization']}';
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
    expect(_lastRequest(), 'POST /v1/devices/dev-1/recovery-code Bearer tok');
  });

  test('reconnect code is asked for the existing child with the parent token', () async {
    final code = await PairingRepository(_api({
      'code': '12345678',
      'expiresAt': '2026-11-03T18:10:00Z',
    })).createReconnectCode('dev-1');

    expect(code.code, '12345678');
    expect(_lastRequest(), 'POST /v1/devices/dev-1/pairing-code Bearer tok');
  });

  test('deleting a child sends the parent token', () async {
    await _repo(null).delete('dev-1');

    expect(_lastRequest(), 'DELETE /v1/devices/dev-1 Bearer tok');
  });
}
