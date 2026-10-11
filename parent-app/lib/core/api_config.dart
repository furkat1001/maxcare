import 'package:flutter/foundation.dart';

/// Адрес сервера. В debug — локальный backend (10.0.2.2 — компьютер с точки зрения
/// эмулятора Android; для телефона по USB — `adb reverse tcp:8080 tcp:8080` и localhost).
/// В release — боевой домен. Переопределяется через --dart-define=MAXCARE_API=...
class ApiConfig {
  const ApiConfig._();

  static const String _override = String.fromEnvironment('MAXCARE_API');

  static String get baseUrl {
    if (_override.isNotEmpty) return _override;
    return kReleaseMode ? 'https://api.maxcare.dexx.uz' : 'http://10.0.2.2:8080';
  }
}
