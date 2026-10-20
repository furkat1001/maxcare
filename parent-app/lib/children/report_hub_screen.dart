import 'package:flutter/material.dart';

import '../l10n/app_localizations.dart';
import 'app_limits_screen.dart';
import 'app_usage_screen.dart';
import 'child_location_screen.dart';
import 'device_events_screen.dart';
import 'recovery_code_screen.dart';

/// Хаб отчёта по одному ребёнку: маршрут, время и ограничения в приложениях, журнал событий.
/// Дальнейшие разделы (удалённые звонки/смс) добавятся сюда.
class ReportHubScreen extends StatelessWidget {
  const ReportHubScreen({required this.deviceId, required this.title, super.key});

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
            leading: const Icon(Icons.map_outlined),
            title: Text(t.reportRoute),
            subtitle: Text(t.reportRouteSubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => ChildLocationScreen(deviceId: deviceId, title: title),
              ),
            ),
          ),
          const Divider(height: 1),
          ListTile(
            leading: const Icon(Icons.hourglass_bottom_outlined),
            title: Text(t.reportUsage),
            subtitle: Text(t.reportUsageSubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => AppUsageScreen(deviceId: deviceId, title: title),
              ),
            ),
          ),
          const Divider(height: 1),
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
            leading: const Icon(Icons.list_alt_outlined),
            title: Text(t.reportActivity),
            subtitle: Text(t.reportActivitySubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => DeviceEventsScreen(deviceId: deviceId, title: title),
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
