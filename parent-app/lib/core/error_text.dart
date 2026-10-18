import '../l10n/app_localizations.dart';
import 'api_exception.dart';

/// Человеческий текст ошибки по её типу, на языке интерфейса.
String errorText(AppLocalizations t, Object error) {
  if (error is ApiException) {
    switch (error.kind) {
      case ApiErrorKind.noConnection:
        return t.errorNoConnection;
      case ApiErrorKind.unauthorized:
        return t.errorInvalidCredentials;
      case ApiErrorKind.emailTaken:
        return t.errorEmailTaken;
      case ApiErrorKind.invalidCode:
        return t.errorServer;
      // Лимит детей показывается прямо на экране «Добавить ребёнка»;
      // здесь — лишь безопасный запасной текст.
      case ApiErrorKind.childLimit:
        return t.addChildLimit;
      case ApiErrorKind.tooManyRequests:
        return t.errorTooManyAttempts;
      case ApiErrorKind.invalidRequest:
      case ApiErrorKind.notFound:
      case ApiErrorKind.server:
        return t.errorServer;
    }
  }
  return t.errorServer;
}
