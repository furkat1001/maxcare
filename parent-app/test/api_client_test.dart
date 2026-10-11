import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parent/core/api_client.dart';
import 'package:parent/core/api_exception.dart';
import 'package:parent/core/token_store.dart';

/// Поддельное хранилище: токен в памяти, без системного Keystore.
class _FakeTokenStore implements TokenStore {
  String? _token;
  @override
  Future<String?> read() async => _token;
  @override
  Future<void> write(String token) async => _token = token;
  @override
  Future<void> clear() async => _token = null;
}

/// Адаптер Dio, возвращающий заранее заданный ответ или бросающий сетевую ошибку.
class _StubAdapter implements HttpClientAdapter {
  _StubAdapter(this.statusCode, this.body, {this.throwNetwork = false});
  final int statusCode;
  final Object? body;
  final bool throwNetwork;
  RequestOptions? lastRequest;

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream,
      Future<void>? cancelFuture) async {
    lastRequest = options;
    if (throwNetwork) {
      throw DioException.connectionError(
          requestOptions: options, reason: 'offline');
    }
    return ResponseBody.fromString(
      jsonEncode(body),
      statusCode,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

ApiClient _client(_StubAdapter adapter, {TokenStore? store}) {
  final dio = Dio(BaseOptions(
    baseUrl: 'http://test',
    validateStatus: (s) => s != null && s < 500,
  ))
    ..httpClientAdapter = adapter;
  return ApiClient(tokenStore: store ?? _FakeTokenStore(), dio: dio);
}

void main() {
  test('success returns the parsed body', () async {
    final client = _client(_StubAdapter(201, {'token': 'abc'}));
    final result = await client.post('/v1/sessions', body: {'email': 'a@b.c'});
    expect(result['token'], 'abc');
  });

  test('error codes map to typed kinds', () async {
    final cases = {
      'email_taken': ApiErrorKind.emailTaken,
      'invalid_credentials': ApiErrorKind.unauthorized,
      'rate_limited': ApiErrorKind.tooManyRequests,
      'invalid_code': ApiErrorKind.invalidCode,
    };
    for (final entry in cases.entries) {
      final client = _client(_StubAdapter(400, {'error': entry.key}));
      await expectLater(
        client.post('/x'),
        throwsA(isA<ApiException>().having((e) => e.kind, 'kind', entry.value)),
      );
    }
  });

  test('status without an error code falls back by http status', () async {
    final client = _client(_StubAdapter(401, <String, dynamic>{}));
    await expectLater(
      client.get('/x'),
      throwsA(isA<ApiException>().having((e) => e.kind, 'kind', ApiErrorKind.unauthorized)),
    );
  });

  test('network failure becomes noConnection', () async {
    final client = _client(_StubAdapter(0, null, throwNetwork: true));
    await expectLater(
      client.post('/x'),
      throwsA(isA<ApiException>().having((e) => e.kind, 'kind', ApiErrorKind.noConnection)),
    );
  });

  test('authorized request sends the bearer token', () async {
    final store = _FakeTokenStore()..write('tok-1');
    final adapter = _StubAdapter(200, <String, dynamic>{});
    await _client(adapter, store: store).get('/v1/devices/x/events');
    expect(adapter.lastRequest?.headers['Authorization'], 'Bearer tok-1');
  });

  test('unauthorized request sends no token', () async {
    final store = _FakeTokenStore()..write('tok-1');
    final adapter = _StubAdapter(201, {'token': 'x'});
    await _client(adapter, store: store).post('/v1/sessions');
    expect(adapter.lastRequest?.headers.containsKey('Authorization'), false);
  });
}
