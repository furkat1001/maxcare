import '../core/api_client.dart';
import '../core/token_store.dart';

/// Регистрация и вход родителя. Смотри backend `docs/backend-api.md`.
class AuthRepository {
  AuthRepository({required ApiClient api, required TokenStore tokenStore})
      : _api = api,
        _tokenStore = tokenStore;

  final ApiClient _api;
  final TokenStore _tokenStore;

  Future<void> register(String email, String password) async {
    await _api.post(
      '/v1/parents',
      body: {'email': email.trim(), 'password': password},
    );
  }

  /// Создаёт сессию и сохраняет токен в защищённом хранилище.
  Future<void> signIn(String email, String password) async {
    final response = await _api.post(
      '/v1/sessions',
      body: {'email': email.trim(), 'password': password},
    );
    await _saveSession(response);
  }

  /// Вход через Google: ID-токен проверяет сервер и выдаёт обычную сессию MaxCare.
  Future<void> signInWithGoogle(String idToken) async {
    final response = await _api.post(
      '/v1/sessions/google',
      body: {'idToken': idToken},
    );
    await _saveSession(response);
  }

  Future<void> _saveSession(Map<String, dynamic> response) async {
    final token = response['token'] as String?;
    if (token == null || token.isEmpty) {
      throw StateError('Session response has no token');
    }
    await _tokenStore.write(token);
  }

  Future<void> signOut() => _tokenStore.clear();

  Future<bool> hasSession() async => (await _tokenStore.read()) != null;
}
