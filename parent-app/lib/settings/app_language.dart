import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Язык интерфейса, выбранный родителем. [system] — как в телефоне: тогда работает
/// и узбекская кириллица (uz-Cyrl), которой нет в списке выбора.
enum AppLanguage {
  system(null),
  english('en'),
  uzbekLatin('uz'),
  russian('ru');

  const AppLanguage(this.code);

  /// Значение в хранилище; у [system] его нет — запись удаляется.
  final String? code;

  Locale? get locale => code == null ? null : Locale(code!);

  static AppLanguage fromCode(String? code) =>
      values.firstWhere((l) => l.code == code, orElse: () => system);
}

/// Хранит выбор языка рядом с токеном сессии — в том же защищённом хранилище,
/// чтобы не добавлять ради одной строки ещё одну библиотеку.
class LanguageStore {
  LanguageStore([FlutterSecureStorage? storage])
      : _storage = storage ?? const FlutterSecureStorage();

  final FlutterSecureStorage _storage;
  static const _key = 'app_language';

  Future<AppLanguage> read() async => AppLanguage.fromCode(await _storage.read(key: _key));

  Future<void> write(AppLanguage language) => language.code == null
      ? _storage.delete(key: _key)
      : _storage.write(key: _key, value: language.code);
}

/// Текущий язык. Пока выбор читается из хранилища, интерфейс идёт на языке телефона.
class LanguageController extends StateNotifier<AppLanguage> {
  LanguageController(this._store) : super(AppLanguage.system) {
    _restore();
  }

  final LanguageStore _store;

  // Родитель успел выбрать язык раньше, чем прочиталось хранилище, — его выбор важнее.
  bool _selected = false;

  Future<void> _restore() async {
    final saved = await _store.read();
    if (mounted && !_selected) state = saved;
  }

  Future<void> select(AppLanguage language) async {
    _selected = true;
    state = language;
    await _store.write(language);
  }
}
