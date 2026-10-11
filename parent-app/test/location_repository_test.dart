import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parent/children/location_repository.dart';
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

(LocationRepository, _StubAdapter) _repo(Object? body) {
  final adapter = _StubAdapter(body);
  final dio = Dio(BaseOptions(
    baseUrl: 'http://test',
    validateStatus: (s) => s != null && s < 500,
  ))
    ..httpClientAdapter = adapter;
  return (LocationRepository(ApiClient(tokenStore: _FakeTokenStore(), dio: dio)), adapter);
}

void main() {
  test('history parses a chronological track with accuracy', () async {
    final (repo, _) = _repo([
      {
        'latitude': 41.1,
        'longitude': 69.2,
        'accuracyMeters': 12.5,
        'recordedAt': '2026-10-11T08:00:00Z',
        'receivedAt': '2026-10-11T08:00:05Z',
      },
      {
        'latitude': 41.2,
        'longitude': 69.3,
        'accuracyMeters': null,
        'recordedAt': '2026-10-11T08:05:00Z',
        'receivedAt': '2026-10-11T08:05:05Z',
      },
    ]);

    final track = await repo.history('d1');

    expect(track.length, 2);
    expect(track.first.latitude, 41.1);
    expect(track.first.accuracyMeters, 12.5);
    expect(track.last.accuracyMeters, isNull);
  });

  test('empty history means no location yet', () async {
    final (repo, _) = _repo(<dynamic>[]);
    expect(await repo.history('d1'), isEmpty);
  });

  test('since is sent as a UTC query parameter', () async {
    final (repo, adapter) = _repo(<dynamic>[]);

    await repo.history('d1', since: DateTime.utc(2026, 10, 11, 6));

    expect(adapter.lastRequest?.uri.queryParameters['since'], '2026-10-11T06:00:00.000Z');
  });
}
