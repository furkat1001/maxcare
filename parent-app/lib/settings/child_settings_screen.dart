import 'package:flutter/material.dart';

import '../children/app_limits_screen.dart';
import '../children/recovery_code_screen.dart';
import '../l10n/app_localizations.dart';

/// Настройки одного ребёнка: ограничения времени и код восстановления пароля.
class ChildSettingsScreen extends StatelessWidget {
  const ChildSettingsScreen({required this.deviceId, required this.title, super.key});

  final String deviceId;
  final String title;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: ListView(
        children: [
          ListTile(
            leading: const Icon(Icons.timer_outlined),
            title: Text(t.reportLimits),
            subtitle: Text(t.reportLimitsSubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => AppLimitsScreen(deviceId: deviceId, title: title),
              ),
            ),
          ),
          const Divider(height: 1),
          ListTile(
            leading: const Icon(Icons.lock_reset),
            title: Text(t.recoveryTitle),
            subtitle: Text(t.recoverySubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => RecoveryCodeScreen(deviceId: deviceId, title: title),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
