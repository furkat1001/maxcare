import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'app_language.dart';
import 'settings_tab.dart';

/// Выбор языка. Интерфейс переключается сразу, без перезапуска.
class LanguageScreen extends ConsumerWidget {
  const LanguageScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = AppLocalizations.of(context);
    final current = ref.watch(languageProvider);
    return Scaffold(
      appBar: AppBar(title: Text(t.settingsLanguage)),
      body: RadioGroup<AppLanguage>(
        groupValue: current,
        onChanged: (value) {
          if (value != null) ref.read(languageProvider.notifier).select(value);
        },
        child: ListView(
          children: [
            for (final language in AppLanguage.values)
              RadioListTile<AppLanguage>(
                value: language,
                title: Text(languageName(t, language)),
              ),
          ],
        ),
      ),
    );
  }
}
