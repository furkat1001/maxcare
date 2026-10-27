import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../children/add_child_screen.dart';
import '../children/children_list.dart';
import '../children/report_hub_screen.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import '../settings/settings_tab.dart';
import 'main_tab.dart';

/// Корневой экран родителя: нижние вкладки «Главная», «Отчёты», «Настройки».
class MainShell extends ConsumerStatefulWidget {
  const MainShell({super.key});

  @override
  ConsumerState<MainShell> createState() => _MainShellState();
}

class _MainShellState extends ConsumerState<MainShell> {
  int _tab = 0;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final title = switch (_tab) {
      0 => t.homeTitle,
      1 => t.navReports,
      _ => t.navSettings,
    };
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: IndexedStack(
        index: _tab,
        children: const [MainTab(), _ReportsTab(), SettingsTab()],
      ),
      floatingActionButton: _tab == 0 ? _addChildButton(context, t) : null,
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) => setState(() => _tab = i),
        destinations: [
          NavigationDestination(icon: const Icon(Icons.home_outlined), label: t.navMain),
          NavigationDestination(icon: const Icon(Icons.assessment_outlined), label: t.navReports),
          NavigationDestination(icon: const Icon(Icons.settings_outlined), label: t.navSettings),
        ],
      ),
    );
  }

  Widget _addChildButton(BuildContext context, AppLocalizations t) =>
      FloatingActionButton.extended(
        onPressed: () async {
          await Navigator.of(context).push(
            MaterialPageRoute<void>(builder: (_) => const AddChildScreen()),
          );
          // Привязка могла добавить устройство — перечитываем список.
          ref.invalidate(devicesProvider);
        },
        icon: const Icon(Icons.add),
        label: Text(t.homeAddChild),
      );
}

/// «Отчёты»: выбор ребёнка открывает хаб отчёта — только то, что смотрят; настройки ребёнка
/// (ограничения, код восстановления) — на вкладке «Настройки».
class _ReportsTab extends StatelessWidget {
  const _ReportsTab();

  @override
  Widget build(BuildContext context) => ChildrenList(
        onTap: (context, device) => Navigator.of(context).push(
          MaterialPageRoute<void>(
            builder: (_) => ReportHubScreen(deviceId: device.id, title: device.title),
          ),
        ),
      );
}
