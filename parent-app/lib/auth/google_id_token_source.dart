import 'package:google_sign_in/google_sign_in.dart';

/// ID веб-клиента OAuth из Google Cloud: для него Google выпускает ID-токен, а сервер
/// проверяет получателя (`MAXCARE_GOOGLE_CLIENT_IDS`). Передаётся при сборке:
/// `--dart-define=MAXCARE_GOOGLE_SERVER_CLIENT_ID=...`. Пусто — кнопка Google скрыта.
const googleServerClientId = String.fromEnvironment(
  'MAXCARE_GOOGLE_SERVER_CLIENT_ID',
);

/// Получение ID-токена Google. Отдельный интерфейс — чтобы экран и тесты не зависели от плагина.
abstract interface class GoogleIdTokenSource {
  bool get isAvailable;

  /// Системный выбор аккаунта Google. null — пользователь отменил.
  Future<String?> obtainIdToken();

  /// Забыть выбранный аккаунт: следующий вход снова предложит выбор.
  Future<void> signOut();
}

class PluginGoogleIdTokenSource implements GoogleIdTokenSource {
  PluginGoogleIdTokenSource({String serverClientId = googleServerClientId})
    : _serverClientId = serverClientId;

  final String _serverClientId;
  Future<void>? _initialized;

  @override
  bool get isAvailable => _serverClientId.isNotEmpty;

  Future<void> _ensureInitialized() => _initialized ??= GoogleSignIn.instance
      .initialize(serverClientId: _serverClientId);

  @override
  Future<String?> obtainIdToken() async {
    await _ensureInitialized();
    try {
      final account = await GoogleSignIn.instance.authenticate();
      return account.authentication.idToken;
    } on GoogleSignInException catch (e) {
      if (e.code == GoogleSignInExceptionCode.canceled) return null;
      rethrow;
    }
  }

  @override
  Future<void> signOut() async {
    if (!isAvailable) return;
    await _ensureInitialized();
    await GoogleSignIn.instance.signOut();
  }
}
