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
  return ApiClient(
    tokenStore: store ?? _FakeTokenStore(),
    dio: dio,
    retryDelay: (_) => Duration.zero,
  );
}

/// Сначала [failures] раз бросает сетевую ошибку [type], потом отвечает 200.
class _FlakyAdapter implements HttpClientAdapter {
  _FlakyAdapter(this.failures, this.type);
  int failures;
  final DioExceptionType type;
  int calls = 0;

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream,
      Future<void>? cancelFuture) async {
    calls++;
    if (failures > 0) {
      failures--;
      throw DioException(requestOptions: options, type: type);
    }
    return ResponseBody.fromString(
      jsonEncode({'ok': true}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

ApiClient _flakyClient(_FlakyAdapter adapter) => ApiClient(
      tokenStore: _FakeTokenStore(),
      dio: Dio(BaseOptions(baseUrl: 'http://test'))..httpClientAdapter = adapter,
      retryDelay: (_) => Duration.zero,
    );

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
      'child_limit': ApiErrorKind.childLimit,
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

  test('a short network drop is retried and the request succeeds', () async {
    final adapter = _FlakyAdapter(2, DioExceptionType.connectionError);

    final body = await _flakyClient(adapter).get('/v1/devices/x/events');

    expect(body['ok'], isTrue);
    expect(adapter.calls, 3);
  });

  test('after three failed attempts it is reported as no connection', () async {
    final adapter = _FlakyAdapter(5, DioExceptionType.connectionTimeout);

    await expectLater(
      _flakyClient(adapter).get('/v1/devices'),
      throwsA(isA<ApiException>().having((e) => e.kind, 'kind', ApiErrorKind.noConnection)),
    );
    expect(adapter.calls, 3);
  });

  test('a post whose answer was lost is not sent twice', () async {
    final adapter = _FlakyAdapter(1, DioExceptionType.receiveTimeout);

    await expectLater(
      _flakyClient(adapter).post('/v1/parents', body: {'email': 'p@example.test'}),
      throwsA(isA<ApiException>()),
    );
    expect(adapter.calls, 1);
  });

  test('a post that never left the phone is retried', () async {
    final adapter = _FlakyAdapter(1, DioExceptionType.connectionTimeout);

    final body =
        await _flakyClient(adapter).post('/v1/sessions', body: {'email': 'p@example.test'});

    expect(body['ok'], isTrue);
    expect(adapter.calls, 2);
  });
}
