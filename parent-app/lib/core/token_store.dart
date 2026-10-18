import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Токен сессии родителя — в системном защищённом хранилище (Keystore на Android,
/// Keychain на iOS). В обычные SharedPreferences класть нельзя.
class TokenStore {
  // flutter_secure_storage 11.x шифрует по умолчанию (Keystore/Keychain) — доп. опции не нужны.
  TokenStore([FlutterSecureStorage? storage])
      : _storage = storage ?? const FlutterSecureStorage();

  final FlutterSecureStorage _storage;
  static const _key = 'parent_session_token';

  Future<String?> read() => _storage.read(key: _key);

  Future<void> write(String token) => _storage.write(key: _key, value: token);

  Future<void> clear() => _storage.delete(key: _key);
}
