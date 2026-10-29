import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../children/filter_words_screen.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'account_repository.dart';
import 'account_screen.dart';
import 'app_language.dart';
import 'language_screen.dart';
import 'subscription_screen.dart';

/// «Настройки»: то, что касается всей семьи. Настройки одного ребёнка — на его экране.
class SettingsTab extends ConsumerWidget {
  const SettingsTab({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = AppLocalizations.of(context);
    final account = ref.watch(accountProvider);
    final language = ref.watch(languageProvider);
    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(accountProvider);
      },
      child: ListView(
        children: [
          _Section(t.settingsAccount),
          ListTile(
            leading: const Icon(Icons.account_circle_outlined),
            title: Text(account.valueOrNull?.email ?? t.settingsAccount),
            subtitle: switch (account.valueOrNull) {
              final Account a => Text(signInText(t, a)),
              null => null,
            },
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _push(context, const AccountScreen()),
          ),
          ListTile(
            leading: const Icon(Icons.workspace_premium_outlined),
            title: Text(t.settingsSubscription),
            subtitle: switch (account.valueOrNull) {
              final Account a => Text(planText(t, a)),
              null => null,
            },
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _push(context, const SubscriptionScreen()),
          ),
          _Section(t.settingsProtection),
          ListTile(
            leading: const Icon(Icons.shield_outlined),
            title: Text(t.settingsFilter),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _push(context, const FilterWordsScreen()),
          ),
          _Section(t.settingsApp),
          ListTile(
            leading: const Icon(Icons.translate),
            title: Text(t.settingsLanguage),
            subtitle: Text(languageName(t, language)),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _push(context, const LanguageScreen()),
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  static Future<void> _push(BuildContext context, Widget screen) =>
      Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => screen));
}

/// Способ входа одной строкой: Google важнее пароля, если связаны оба.
String signInText(AppLocalizations t, Account a) =>
    a.signedInWithGoogle ? t.settingsSignInGoogle : t.settingsSignInPassword;

/// «Осталось N дней» или «закончился».
String planText(AppLocalizations t, Account a) => switch (a.planStatus) {
      PlanStatus.trial => t.planTrialDaysLeft(a.trialDaysLeft),
      PlanStatus.expired => t.planExpired,
    };

/// Название языка — на нём самом, чтобы его узнал тот, кто не читает текущий язык.
String languageName(AppLocalizations t, AppLanguage language) => switch (language) {
      AppLanguage.system => t.languageSystem,
      AppLanguage.english => t.languageEnglish,
      AppLanguage.uzbekLatin => t.languageUzbekLatin,
      AppLanguage.russian => t.languageRussian,
    };

class _Section extends StatelessWidget {
  const _Section(this.title);

  final String title;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 4),
      child: Text(
        title,
        style: theme.textTheme.titleSmall?.copyWith(color: theme.colorScheme.primary),
      ),
    );
  }
}
