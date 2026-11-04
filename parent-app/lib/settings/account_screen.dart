import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'account_repository.dart';
import 'settings_tab.dart';

/// Аккаунт родителя: почта, способ входа, дата регистрации и выход.
class AccountScreen extends ConsumerWidget {
  const AccountScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = AppLocalizations.of(context);
    final account = ref.watch(accountProvider);
    return Scaffold(
      appBar: AppBar(title: Text(t.settingsAccount)),
      body: account.when(
        data: (a) => _body(context, t, ref, a),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _error(t, ref, error),
      ),
    );
  }

  Widget _body(BuildContext context, AppLocalizations t, WidgetRef ref, Account a) {
    final locale = Localizations.localeOf(context).languageCode;
    return ListView(
      children: [
        ListTile(
          leading: const Icon(Icons.mail_outline),
          title: Text(t.commonEmail),
          subtitle: Text(a.email),
        ),
        ListTile(
          leading: const Icon(Icons.key_outlined),
          title: Text(t.accountSignIn),
          subtitle: Text(signInText(t, a)),
        ),
        ListTile(
          leading: const Icon(Icons.event_outlined),
          title: Text(t.accountCreated),
          subtitle: Text(DateFormat.yMMMMd(locale).format(a.createdAt.toLocal())),
        ),
        const Divider(),
        ListTile(
          leading: const Icon(Icons.logout),
          title: Text(t.homeSignOut),
          onTap: () {
            Navigator.of(context).popUntil((route) => route.isFirst);
            ref.read(sessionProvider.notifier).signOut();
          },
        ),
      ],
    );
  }

  Widget _error(AppLocalizations t, WidgetRef ref, Object error) => ListView(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 96, 24, 16),
            child: Text(errorText(t, error), textAlign: TextAlign.center),
          ),
          Center(
            child: OutlinedButton(
              onPressed: () => ref.invalidate(accountProvider),
              child: Text(t.commonRetry),
            ),
          ),
        ],
      );
}
