import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parent/auth/auth_repository.dart';
import 'package:parent/auth/google_id_token_source.dart';
import 'package:parent/core/api_client.dart';
import 'package:parent/core/api_exception.dart';
import 'package:parent/core/providers.dart';
import 'package:parent/core/token_store.dart';

class _MemoryTokenStore implements TokenStore {
  String? token;
  @override
  Future<String?> read() async => token;
  @override
  Future<void> write(String value) async => token = value;
  @override
  Future<void> clear() async => token = null;
}

class _StubAdapter implements HttpClientAdapter {
  _StubAdapter(this.status, this.body);
  final int status;
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
      status,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

class _FakeGoogle implements GoogleIdTokenSource {
  int signOuts = 0;
  @override
  bool get isAvailable => true;
  @override
  Future<String?> obtainIdToken() async => 'google-id-token';
  @override
  Future<void> signOut() async => signOuts++;
}

(AuthRepository, _StubAdapter, _MemoryTokenStore) _repo(
  int status,
  Object body,
) {
  final adapter = _StubAdapter(status, body);
  final store = _MemoryTokenStore();
  final dio = Dio(
    BaseOptions(
      baseUrl: 'http://test',
      validateStatus: (s) => s != null && s < 500,
    ),
  )..httpClientAdapter = adapter;
  final api = ApiClient(tokenStore: store, dio: dio);
  return (AuthRepository(api: api, tokenStore: store), adapter, store);
}

void main() {
  test('google sign in sends the id token and keeps the session', () async {
    final (repo, adapter, store) = _repo(201, {'token': 'session-1'});

    await repo.signInWithGoogle('google-id-token');

    expect(adapter.requests.single.uri.path, '/v1/sessions/google');
    expect(adapter.requests.single.data, {'idToken': 'google-id-token'});
    expect(store.token, 'session-1');
  });

  test('rejected google token is an error and no session is kept', () async {
    final (repo, _, store) = _repo(401, {'error': 'invalid_credentials'});

    await expectLater(
      repo.signInWithGoogle('bad'),
      throwsA(isA<ApiException>()),
    );
    expect(store.token, isNull);
  });

  test('email taken by a password account is reported as such', () async {
    final (repo, _, _) = _repo(409, {'error': 'email_taken'});

    await expectLater(
      repo.signInWithGoogle('unverified'),
      throwsA(
        isA<ApiException>().having(
          (e) => e.kind,
          'kind',
          ApiErrorKind.emailTaken,
        ),
      ),
    );
  });

  test('signing out also forgets the google account', () async {
    final (repo, _, store) = _repo(201, {'token': 'session-1'});
    final google = _FakeGoogle();
    await repo.signInWithGoogle('google-id-token');
    final session = SessionController(repo, google);

    await session.signOut();

    expect(store.token, isNull);
    expect(google.signOuts, 1);
    expect(session.state, isFalse);
  });

  test('google button is hidden when the client id is not configured', () {
    expect(PluginGoogleIdTokenSource(serverClientId: '').isAvailable, isFalse);
    expect(
      PluginGoogleIdTokenSource(
        serverClientId: 'web.apps.googleusercontent.com',
      ).isAvailable,
      isTrue,
    );
  });
}
