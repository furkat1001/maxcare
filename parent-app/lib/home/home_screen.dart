import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../children/add_child_screen.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import '../settings/settings_tab.dart';
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
    final title = switch (tab) {
      0 => t.navFamily,
      1 => t.navMap,
      2 => t.navEvents,
      _ => t.navSettings,
    };
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: IndexedStack(
        index: tab,
        children: const [FamilyTab(), FamilyMapTab(), EventsTab(), SettingsTab()],
      ),
      floatingActionButton: tab == 0 ? _addChildButton(context, ref, t) : null,
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
