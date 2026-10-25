import 'package:dio/dio.dart';

import 'api_config.dart';
import 'api_exception.dart';
import 'token_store.dart';

/// Клиент к серверу MaxCare. Подставляет токен родителя в заголовок и разбирает
/// коды ошибок в [ApiException]. Тела запросов/ответов не логируются (в них токены).
///
/// Канал до сервера бывает нестабильным (потери пакетов, короткие обрывы), поэтому сетевые
/// ошибки повторяются до [_maxAttempts] раз с паузой [retryDelay] — и только потом «нет связи».
class ApiClient {
  ApiClient({
    required TokenStore tokenStore,
    Dio? dio,
    Duration Function(int attempt)? retryDelay,
  })  : _tokenStore = tokenStore,
        _retryDelay = retryDelay ?? ((attempt) => Duration(seconds: attempt)),
        _dio = dio ??
            Dio(
              BaseOptions(
                baseUrl: ApiConfig.baseUrl,
                connectTimeout: const Duration(seconds: 15),
                receiveTimeout: const Duration(seconds: 20),
                // Коды ошибок (4xx) разбираем сами, поэтому не бросаем на них.
                validateStatus: (status) => status != null && status < 500,
              ),
            );

  final Dio _dio;
  final TokenStore _tokenStore;
  final Duration Function(int attempt) _retryDelay;

  static const _maxAttempts = 3;

  Future<Map<String, dynamic>> post(
    String path, {
    Object? body,
    bool authorized = false,
  }) =>
      _request(
        (options) => _dio.post<dynamic>(path, data: body, options: options),
        authorized,
        idempotent: false,
      );

  Future<Map<String, dynamic>> get(String path, {bool authorized = true}) =>
      _request((options) => _dio.get<dynamic>(path, options: options), authorized);

  Future<Map<String, dynamic>> put(
    String path, {
    Object? body,
    bool authorized = true,
  }) =>
      _request((options) => _dio.put<dynamic>(path, data: body, options: options), authorized);

  Future<void> delete(String path, {bool authorized = true}) async {
    await _requestRaw((options) => _dio.delete<dynamic>(path, options: options), authorized);
  }

  /// Для эндпоинтов, отвечающих JSON-массивом (например, список устройств).
  Future<List<Map<String, dynamic>>> getList(String path, {bool authorized = true}) async {
    final data = await _requestRaw(
      (options) => _dio.get<dynamic>(path, options: options),
      authorized,
    );
    if (data is! List) return const [];
    return data.whereType<Map<String, dynamic>>().toList();
  }

  Future<Map<String, dynamic>> _request(
    Future<Response<dynamic>> Function(Options) call,
    bool authorized, {
    bool idempotent = true,
  }) async {
    final data = await _requestRaw(call, authorized, idempotent: idempotent);
    return data is Map<String, dynamic> ? data : <String, dynamic>{};
  }

  Future<Object?> _requestRaw(
    Future<Response<dynamic>> Function(Options) call,
    bool authorized, {
    bool idempotent = true,
  }) async {
    final headers = <String, String>{};
    if (authorized) {
      final token = await _tokenStore.read();
      if (token != null) headers['Authorization'] = 'Bearer $token';
    }
    late final Response<dynamic> response;
    for (var attempt = 1;; attempt++) {
      try {
        response = await call(Options(headers: headers));
        break;
      } on DioException catch (e) {
        if (attempt >= _maxAttempts || !_retryable(e, idempotent: idempotent)) {
          // Таймаут, нет сети, сервер недоступен — всё как «нет связи».
          throw ApiException(ApiErrorKind.noConnection);
        }
        await Future<void>.delayed(_retryDelay(attempt));
      }
    }
    final status = response.statusCode ?? 0;
    if (status < 200 || status >= 300) {
      final code = _errorCode(response.data);
      throw ApiException(_kindFor(status, code), code: code);
    }
    return response.data;
  }

  /// Соединение не установилось — запрос не ушёл, повторять безопасно всегда. Ответ не пришёл
  /// после отправки — повторяем только идемпотентные запросы: регистрацию дважды не шлём.
  bool _retryable(DioException e, {required bool idempotent}) => switch (e.type) {
        DioExceptionType.connectionTimeout || DioExceptionType.connectionError => true,
        DioExceptionType.receiveTimeout ||
        DioExceptionType.sendTimeout ||
        DioExceptionType.unknown =>
          idempotent,
        _ => false,
      };

  ApiErrorKind _kindFor(int status, String? code) {
    switch (code) {
      case 'email_taken':
        return ApiErrorKind.emailTaken;
      case 'invalid_credentials':
      case 'unauthorized':
        return ApiErrorKind.unauthorized;
      case 'invalid_code':
        return ApiErrorKind.invalidCode;
      case 'child_limit':
        return ApiErrorKind.childLimit;
      case 'not_found':
        return ApiErrorKind.notFound;
      case 'rate_limited':
        return ApiErrorKind.tooManyRequests;
      case 'invalid_request':
        return ApiErrorKind.invalidRequest;
    }
    if (status == 401) return ApiErrorKind.unauthorized;
    if (status == 429) return ApiErrorKind.tooManyRequests;
    if (status == 400) return ApiErrorKind.invalidRequest;
    return ApiErrorKind.server;
  }

  String? _errorCode(Object? data) =>
      (data is Map && data['error'] is String) ? data['error'] as String : null;
}
