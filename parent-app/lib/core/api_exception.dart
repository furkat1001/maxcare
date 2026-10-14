/// Типизированная ошибка API: экран выбирает текст по [kind], а не по случайной строке.
/// [code] — машинный код из тела ответа сервера (backend/.../http/Http.kt), если был.
class ApiException implements Exception {
  ApiException(this.kind, {this.code});

  final ApiErrorKind kind;
  final String? code;

  @override
  String toString() => 'ApiException($kind, code=$code)';
}

enum ApiErrorKind {
  noConnection,
  unauthorized,
  emailTaken,
  invalidRequest,
  invalidCode,
  childLimit,
  notFound,
  tooManyRequests,
  server,
}
