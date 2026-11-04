import 'package:flutter/material.dart';

import '../l10n/app_localizations.dart';
import 'app_usage_screen.dart';
import 'child_location_screen.dart';
import 'daily_report_screen.dart';
import 'device_events_screen.dart';

/// Хаб отчёта по одному ребёнку — только то, что родитель смотрит: итоги дня, маршрут,
/// время в приложениях, журнал. Что он меняет (ограничения, код восстановления) — в настройках.
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
            leading: const Icon(Icons.insights_outlined),
            title: Text(t.reportDaily),
            subtitle: Text(t.reportDailySubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => DailyReportScreen(deviceId: deviceId, title: title),
              ),
            ),
          ),
          const Divider(height: 1),
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
        ],
      ),
    );
  }
}
