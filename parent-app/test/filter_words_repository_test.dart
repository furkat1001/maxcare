import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parent/children/filter_words_repository.dart';
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

/// Отдаёт заранее заданное тело на любой запрос.
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

FilterWordsRepository _repo(Object? body) {
  final dio = Dio(BaseOptions(
    baseUrl: 'http://test',
    validateStatus: (s) => s != null && s < 500,
  ))
    ..httpClientAdapter = _StubAdapter(body);
  return FilterWordsRepository(ApiClient(tokenStore: _FakeTokenStore(), dio: dio));
}

void main() {
  test('list parses the words array', () async {
    final words = await _repo({
      'words': ['казино', 'ставки'],
    }).list();

    expect(words, ['казино', 'ставки']);
  });

  test('missing words field yields an empty list', () async {
    expect(await _repo(<String, dynamic>{}).list(), isEmpty);
  });

  test('add and remove complete without error', () async {
    final repo = _repo(<String, dynamic>{});
    await repo.add('казино');
    await repo.remove('казино');
  });
}
