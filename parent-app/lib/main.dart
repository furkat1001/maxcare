import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'auth/sign_in_screen.dart';
import 'core/providers.dart';
import 'core/theme.dart';
import 'home/home_screen.dart';
import 'l10n/app_localizations.dart';

void main() {
  runApp(const ProviderScope(child: MaxCareParentApp()));
}

class MaxCareParentApp extends ConsumerWidget {
  const MaxCareParentApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final signedIn = ref.watch(sessionProvider);
    final language = ref.watch(languageProvider);
    return MaterialApp(
      onGenerateTitle: (context) => AppLocalizations.of(context).appName,
      theme: maxCareTheme(Brightness.light),
      darkTheme: maxCareTheme(Brightness.dark),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      // null — язык телефона через _resolveLocale.
      locale: language.locale,
      localeListResolutionCallback: _resolveLocale,
      home: switch (signedIn) {
        null => const _Splash(),
        false => const SignInScreen(),
        true => const MainShell(),
      },
    );
  }

  // Узбекская кириллица приходит как uz-Cyrl; обычное сопоставление её теряет.
  Locale? _resolveLocale(List<Locale>? device, Iterable<Locale> supported) {
    for (final locale in device ?? const <Locale>[]) {
      final match = supported.where((s) => s.languageCode == locale.languageCode);
      if (match.isEmpty) continue;
      final byScript = match.where((s) => s.scriptCode == locale.scriptCode);
      return byScript.isNotEmpty ? byScript.first : match.first;
    }
    return null;
  }
}

class _Splash extends StatelessWidget {
  const _Splash();

  @override
  Widget build(BuildContext context) =>
      const Scaffold(body: Center(child: CircularProgressIndicator()));
}
