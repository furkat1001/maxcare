import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'account_repository.dart';
import 'billing.dart';
import 'settings_tab.dart';

/// Подписка семьи: что сейчас с оплатой, месячный и годовой тарифы, восстановление покупок.
/// Цены и валюта — из Google Play через RevenueCat; что оплачено, решает сервер.
class SubscriptionScreen extends ConsumerStatefulWidget {
  const SubscriptionScreen({super.key});

  @override
  ConsumerState<SubscriptionScreen> createState() => _SubscriptionScreenState();
}

class _SubscriptionScreenState extends ConsumerState<SubscriptionScreen> {
  PlanPeriod _period = PlanPeriod.yearly;
  bool _busy = false;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final account = ref.watch(accountProvider);
    return Scaffold(
      appBar: AppBar(title: Text(t.settingsSubscription)),
      body: account.when(
        data: (a) => _body(t, a),
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

  Widget _body(AppLocalizations t, Account a) {
    final billing = ref.watch(billingProvider);
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _StatusCard(account: a),
        const SizedBox(height: 16),
        if (a.planStatus == PlanStatus.active)
          Text(t.subscriptionManageHint)
        else if (!billing.available)
          Text(t.subscriptionUnavailable)
        else
          ..._plans(t),
        if (billing.available) ...[
          const SizedBox(height: 8),
          TextButton(onPressed: _busy ? null : () => _restore(t, a), child: Text(t.subscriptionRestore)),
        ],
      ],
    );
  }

  List<Widget> _plans(AppLocalizations t) {
    final plans = ref.watch(storePlansProvider);
    return plans.when(
      data: (list) {
        if (list.isEmpty) return [Text(t.subscriptionUnavailable)];
        final selected = list.firstWhere((p) => p.period == _period, orElse: () => list.first);
        return [
          for (final plan in list)
            _PlanTile(
              plan: plan,
              selected: plan == selected,
              onTap: _busy ? null : () => setState(() => _period = plan.period),
            ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: _busy ? null : () => _buy(t, selected),
            child: _busy
                ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                : Text(t.subscriptionSubscribe),
          ),
        ];
      },
      loading: () => const [Center(child: CircularProgressIndicator())],
      error: (_, _) => [
        Text(t.subscriptionPlansError),
        const SizedBox(height: 8),
        OutlinedButton(
          onPressed: () => ref.invalidate(storePlansProvider),
          child: Text(t.commonRetry),
        ),
      ],
    );
  }

  Future<void> _buy(AppLocalizations t, StorePlan plan) => _run(t, () async {
        final outcome = await ref.read(billingProvider).buy(plan);
        if (outcome == PurchaseOutcome.cancelled) return null;
        await _syncWithServer();
        return t.subscriptionThanks;
      });

  Future<void> _restore(AppLocalizations t, Account a) => _run(t, () async {
        final familyId = a.familyId;
        if (familyId == null) return t.subscriptionUnavailable;
        final found = await ref.read(billingProvider).restore(familyId);
        await _syncWithServer();
        return found ? t.subscriptionThanks : t.subscriptionRestoreNone;
      });

  /// Сервер сам спрашивает RevenueCat, что оплачено, — приложение лишь просит сверить.
  Future<void> _syncWithServer() async {
    await ref.read(accountRepositoryProvider).syncBilling();
    ref.invalidate(accountProvider);
  }

  /// Выполняет действие с индикатором; текст результата или ошибки — внизу экрана.
  Future<void> _run(AppLocalizations t, Future<String?> Function() action) async {
    final messenger = ScaffoldMessenger.of(context);
    setState(() => _busy = true);
    try {
      final message = await action();
      if (message != null) messenger.showSnackBar(SnackBar(content: Text(message)));
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(errorText(t, e))));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }
}

class _StatusCard extends StatelessWidget {
  const _StatusCard({required this.account});

  final Account account;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final a = account;
    final (title, body, color) = switch (a.planStatus) {
      PlanStatus.trial => (t.subscriptionTrial, t.subscriptionTrialBody, colors.primaryContainer),
      PlanStatus.active => (t.subscriptionActiveTitle, null, colors.primaryContainer),
      PlanStatus.grace => (
          t.subscriptionGraceTitle,
          t.subscriptionGraceBody(_date(t, a.graceEndsAt)),
          colors.errorContainer,
        ),
      PlanStatus.expired => (t.subscriptionExpiredTitle, t.subscriptionExpiredBody, colors.errorContainer),
    };
    return Card(
      color: color,
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: theme.textTheme.titleMedium),
            const SizedBox(height: 8),
            Text(planText(t, a), style: theme.textTheme.headlineSmall),
            if (body != null) ...[
              const SizedBox(height: 12),
              Text(body),
            ],
          ],
        ),
      ),
    );
  }
}

class _PlanTile extends StatelessWidget {
  const _PlanTile({required this.plan, required this.selected, required this.onTap});

  final StorePlan plan;
  final bool selected;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final colors = Theme.of(context).colorScheme;
    final (name, price) = switch (plan.period) {
      PlanPeriod.monthly => (t.subscriptionMonthly, t.subscriptionPerMonth(plan.priceString)),
      PlanPeriod.yearly => (t.subscriptionYearly, t.subscriptionPerYear(plan.priceString)),
    };
    return Card(
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: selected ? colors.primary : colors.outlineVariant, width: selected ? 2 : 1),
      ),
      child: ListTile(
        onTap: onTap,
        leading: Icon(selected ? Icons.radio_button_checked : Icons.radio_button_unchecked, color: colors.primary),
        title: Text(name),
        subtitle: Text(price),
        selected: selected,
      ),
    );
  }
}

String _date(AppLocalizations t, DateTime? date) =>
    date == null ? '' : DateFormat.yMMMMd(t.localeName).format(date.toLocal());
