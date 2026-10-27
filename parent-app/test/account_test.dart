import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:parent/core/api_client.dart';
import 'package:parent/core/token_store.dart';
import 'package:parent/l10n/app_localizations.dart';
import 'package:parent/settings/account_repository.dart';
import 'package:parent/settings/app_language.dart';
import 'package:parent/settings/settings_tab.dart';

class _FakeTokenStore implements TokenStore {
  @override
  Future<String?> read() async => 'tok';
  @override
  Future<void> write(String token) async {}
  @override
  Future<void> clear() async {}
}

class _StubAdapter implements HttpClientAdapter {
  _StubAdapter(this.body);
  final Object? body;
  String? path;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    path = options.path;
    return ResponseBody.fromString(
      jsonEncode(body),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

Future<Account> _account(Map<String, Object?> plan, {List<String> providers = const []}) {
  final dio = Dio(BaseOptions(baseUrl: 'http://test'))
    ..httpClientAdapter = _StubAdapter({
      'email': 'parent@example.test',
      'createdAt': '2026-10-09T10:00:00Z',
      'hasPassword': providers.isEmpty,
      'providers': providers,
      'plan': plan,
    });
  return AccountRepository(ApiClient(tokenStore: _FakeTokenStore(), dio: dio)).get();
}

void main() {
  final ru = lookupAppLocalizations(const Locale('ru'));

  test('a trial account shows the days left', () async {
    final a = await _account(
      {'status': 'trial', 'trialEndsAt': '2026-10-23T10:00:00Z', 'trialDaysLeft': 5},
    );

    expect(a.email, 'parent@example.test');
    expect(a.planStatus, PlanStatus.trial);
    expect(a.trialEndsAt, DateTime.utc(2026, 10, 23, 10));
    expect(planText(ru, a), 'Пробный период: осталось 5 дней');
    expect(signInText(ru, a), ru.settingsSignInPassword);
  });

  test('one day left uses the singular form', () async {
    final a = await _account(
      {'status': 'trial', 'trialEndsAt': '2026-10-23T10:00:00Z', 'trialDaysLeft': 1},
    );

    expect(planText(ru, a), 'Пробный период: остался 1 день');
  });

  test('an ended trial says so', () async {
    final a = await _account(
      {'status': 'expired', 'trialEndsAt': '2026-10-23T10:00:00Z', 'trialDaysLeft': 0},
      providers: ['google'],
    );

    expect(planText(ru, a), ru.planExpired);
    expect(signInText(ru, a), ru.settingsSignInGoogle);
  });

  test('an unknown plan status from a newer server is not shown as ended', () async {
    final a = await _account(
      {'status': 'active', 'trialEndsAt': '2026-10-23T10:00:00Z', 'trialDaysLeft': 0},
    );

    expect(a.planStatus, PlanStatus.trial);
  });

  test('language codes round trip, unknown falls back to the phone language', () {
    for (final language in AppLanguage.values) {
      expect(AppLanguage.fromCode(language.code), language);
    }
    expect(AppLanguage.fromCode('de'), AppLanguage.system);
    expect(AppLanguage.uzbekLatin.locale, const Locale('uz'));
    expect(AppLanguage.system.locale, isNull);
  });

  test('every selectable language has a name in every interface language', () {
    for (final locale in AppLocalizations.supportedLocales) {
      final t = lookupAppLocalizations(locale);
      for (final language in AppLanguage.values) {
        expect(languageName(t, language), isNotEmpty);
      }
    }
  });
}
