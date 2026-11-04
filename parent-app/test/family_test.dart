import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parent/children/device_repository.dart';
import 'package:parent/children/location_repository.dart';
import 'package:parent/core/api_client.dart';
import 'package:parent/core/token_store.dart';
import 'package:parent/home/child_look.dart';
import 'package:parent/home/events_tab.dart';

class _FakeTokenStore implements TokenStore {
  @override
  Future<String?> read() async => 'tok';
  @override
  Future<void> write(String token) async {}
  @override
  Future<void> clear() async {}
}

/// Отвечает заданным кодом; тело null — ответ без тела, как `204 No Content`.
class _StubAdapter implements HttpClientAdapter {
  _StubAdapter(this.status, this.body);
  final int status;
  final Object? body;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async =>
      ResponseBody.fromString(
        body == null ? '' : jsonEncode(body),
        status,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );

  @override
  void close({bool force = false}) {}
}

LocationRepository _locations(int status, Object? body) {
  final dio = Dio(BaseOptions(baseUrl: 'http://test'))..httpClientAdapter = _StubAdapter(status, body);
  return LocationRepository(ApiClient(tokenStore: _FakeTokenStore(), dio: dio));
}

Device _device(String id, String name) => Device(id: id, name: 'Phone', platform: 'android', childName: name);

DeviceEvent _event(String type, String at) =>
    DeviceEvent(type: type, occurredAt: DateTime.parse(at), receivedAt: DateTime.parse(at));

void main() {
  test('the last point of a child is read, and its absence is not an error', () async {
    final point = await _locations(200, {
      'latitude': 41.3,
      'longitude': 69.2,
      'accuracyMeters': 15,
      'recordedAt': '2026-10-29T09:38:00Z',
      'receivedAt': '2026-10-29T09:39:00Z',
    }).latest('d1');

    expect(point?.latitude, 41.3);
    expect(point?.accuracyMeters, 15);
    expect(await _locations(204, null).latest('d1'), isNull);
  });

  test('each of three children has a colour of their own', () {
    final devices = [_device('a', 'Amir'), _device('b', 'Leyla'), _device('c', 'Timur')];
    final dots = devices.map((d) => lookOf(devices, d.id).dot).toSet();

    expect(dots, hasLength(3));
    // Цвет ребёнка не зависит от того, в каком месте экрана он показан.
    expect(lookOf(devices, 'b').dot, lookOf(devices.toList(), 'b').dot);
    expect(initialOf(devices[1]), 'L');
  });

  group('family feed', () {
    final amir = _device('a', 'Amir');
    final leyla = _device('b', 'Leyla');
    final byChild = {
      amir: [_event('device_booted', '2026-10-29T02:40:00Z'), _event('search_blocked', '2026-10-29T07:30:00Z')],
      leyla: [_event('vpn_off', '2026-10-29T11:20:00Z')],
    };

    test('mixes all children, newest first', () {
      final feed = buildFeed(byChild);

      expect(feed.map((i) => i.event.type), ['vpn_off', 'search_blocked', 'device_booted']);
      expect(feed.first.device, leyla);
    });

    test('filters by child and by importance', () {
      expect(buildFeed(byChild, childId: 'a').map((i) => i.device), everyElement(amir));
      expect(
        buildFeed(byChild, importantOnly: true).map((i) => i.event.type),
        ['vpn_off', 'search_blocked'],
      );
    });
  });
}
