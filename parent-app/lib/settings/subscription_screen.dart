import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'account_repository.dart';
import 'settings_tab.dart';

/// Подписка. Оплаты пока нет: экран показывает пробный период, кнопка неактивна.
class SubscriptionScreen extends ConsumerWidget {
  const SubscriptionScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = AppLocalizations.of(context);
    final account = ref.watch(accountProvider);
    return Scaffold(
      appBar: AppBar(title: Text(t.settingsSubscription)),
      body: account.when(
        data: (a) => _body(context, t, a),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text(errorText(t, error), textAlign: TextAlign.center),
          ),
        ),
      ),
    );
  }

  Widget _body(BuildContext context, AppLocalizations t, Account a) {
    final theme = Theme.of(context);
    final locale = Localizations.localeOf(context).languageCode;
    final trial = a.planStatus == PlanStatus.trial;
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          color: trial ? theme.colorScheme.primaryContainer : theme.colorScheme.surfaceContainerHigh,
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(t.subscriptionTrial, style: theme.textTheme.titleMedium),
                const SizedBox(height: 8),
                Text(planText(t, a), style: theme.textTheme.headlineSmall),
                if (trial) ...[
                  const SizedBox(height: 4),
                  Text(
                    t.subscriptionTrialEnds(
                      DateFormat.yMMMMd(locale).format(a.trialEndsAt.toLocal()),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text(t.subscriptionTrialBody),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        Text(t.subscriptionSoon),
        const SizedBox(height: 16),
        // Неактивна, пока нет оплаты: видно, что подписка будет, но нажать нечего.
        FilledButton(onPressed: null, child: Text(t.subscriptionSubscribe)),
      ],
    );
  }
}
