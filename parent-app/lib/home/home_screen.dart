import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../children/add_child_screen.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import '../settings/account_repository.dart';
import '../settings/settings_tab.dart';
import '../settings/subscription_screen.dart';
import 'events_tab.dart';
import 'family_map_tab.dart';
import 'family_tab.dart';

/// Корневой экран родителя: «Семья», «Карта», «События», «Настройки».
/// Всё об одном ребёнке — на его экране (открывается из «Семьи», карты и событий).
class MainShell extends ConsumerWidget {
  const MainShell({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = AppLocalizations.of(context);
    final tab = ref.watch(mainTabProvider);
    final account = ref.watch(accountProvider).valueOrNull;
    // После отсрочки без оплаты открыты только «Настройки»: там подписка и аккаунт.
    // Сборка без оплаты не блокирует никогда: иначе родителю было бы нечем заплатить.
    final stopped =
        account?.planStatus == PlanStatus.expired && ref.watch(billingProvider).available;
    const tabs = [FamilyTab(), FamilyMapTab(), EventsTab(), SettingsTab()];
    final title = switch (tab) {
      0 => t.navFamily,
      1 => t.navMap,
      2 => t.navEvents,
      _ => t.navSettings,
    };
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: Column(
        children: [
          if (account != null) _PlanBanner(account: account),
          Expanded(
            child: IndexedStack(
              index: tab,
              children: [
                for (final (i, child) in tabs.indexed)
                  stopped && i != tabs.length - 1 ? const _StoppedView() : child,
              ],
            ),
          ),
        ],
      ),
      floatingActionButton: tab == 0 && !stopped ? _addChildButton(context, ref, t) : null,
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab,
        onDestinationSelected: (i) => ref.read(mainTabProvider.notifier).state = i,
        destinations: [
          NavigationDestination(
            icon: const Icon(Icons.family_restroom_outlined),
            selectedIcon: const Icon(Icons.family_restroom),
            label: t.navFamily,
          ),
          NavigationDestination(
            icon: const Icon(Icons.map_outlined),
            selectedIcon: const Icon(Icons.map),
            label: t.navMap,
          ),
          NavigationDestination(
            icon: const Icon(Icons.notifications_outlined),
            selectedIcon: const Icon(Icons.notifications),
            label: t.navEvents,
          ),
          NavigationDestination(
            icon: const Icon(Icons.settings_outlined),
            selectedIcon: const Icon(Icons.settings),
            label: t.navSettings,
          ),
        ],
      ),
    );
  }

  Widget _addChildButton(BuildContext context, WidgetRef ref, AppLocalizations t) =>
      FloatingActionButton.extended(
        onPressed: () async {
          await Navigator.of(context).push(
            MaterialPageRoute<void>(builder: (_) => const AddChildScreen()),
          );
          // Привязка могла добавить устройство — перечитываем список и точки на карте.
          ref
            ..invalidate(devicesProvider)
            ..invalidate(familyLocationsProvider);
        },
        icon: const Icon(Icons.add),
        label: Text(t.homeAddChild),
      );
}

void _openSubscription(BuildContext context) => Navigator.of(context).push(
      MaterialPageRoute<void>(builder: (_) => const SubscriptionScreen()),
    );

/// Предупреждение заранее: за 3 дня до конца пробного периода и всю отсрочку после.
class _PlanBanner extends StatelessWidget {
  const _PlanBanner({required this.account});

  final Account account;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final a = account;
    final text = switch (a.planStatus) {
      PlanStatus.trial when a.trialDaysLeft <= 3 => t.bannerTrialEnds(shortDate(t, a.trialEndsAt)),
      PlanStatus.grace => t.planGrace(shortDate(t, a.graceEndsAt)),
      _ => null,
    };
    if (text == null) return const SizedBox.shrink();
    final colors = Theme.of(context).colorScheme;
    final urgent = a.planStatus == PlanStatus.grace;
    final ink = urgent ? colors.onErrorContainer : colors.onSecondaryContainer;
    return Material(
      color: urgent ? colors.errorContainer : colors.secondaryContainer,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
        child: Row(
          children: [
            Expanded(child: Text(text, style: TextStyle(color: ink))),
            TextButton(
              onPressed: () => _openSubscription(context),
              child: Text(t.subscriptionSubscribe),
            ),
          ],
        ),
      ),
    );
  }
}

/// Вместо «Семьи», карты и событий, пока подписка не оплачена.
class _StoppedView extends StatelessWidget {
  const _StoppedView();

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.lock_outline, size: 56, color: theme.colorScheme.error),
            const SizedBox(height: 16),
            Text(t.subscriptionExpiredTitle, style: theme.textTheme.titleLarge, textAlign: TextAlign.center),
            const SizedBox(height: 8),
            Text(t.subscriptionExpiredBody, textAlign: TextAlign.center),
            const SizedBox(height: 24),
            FilledButton(onPressed: () => _openSubscription(context), child: Text(t.subscriptionSubscribe)),
          ],
        ),
      ),
    );
  }
}
