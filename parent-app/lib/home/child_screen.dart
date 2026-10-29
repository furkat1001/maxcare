import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../children/app_usage_repository.dart';
import '../children/app_usage_screen.dart';
import '../children/child_location_screen.dart';
import '../children/daily_report_screen.dart';
import '../children/device_repository.dart';
import '../children/last_seen.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import '../settings/child_settings_screen.dart';
import 'child_look.dart';
import 'child_status.dart';

/// Всё об одном ребёнке на одном экране: итоги, карта, время в приложениях, его настройки.
/// Сверху — все дети: нажатие переключает ребёнка, открытая вкладка остаётся той же.
class ChildScreen extends ConsumerStatefulWidget {
  const ChildScreen({required this.deviceId, this.initialTab = 0, super.key});

  final String deviceId;

  /// 0 — итоги, 1 — карта, 2 — время, 3 — настройки.
  final int initialTab;

  @override
  ConsumerState<ChildScreen> createState() => _ChildScreenState();
}

class _ChildScreenState extends ConsumerState<ChildScreen> with SingleTickerProviderStateMixin {
  late String _deviceId = widget.deviceId;
  late final TabController _tabs =
      TabController(length: 4, vsync: this, initialIndex: widget.initialTab);

  @override
  void dispose() {
    _tabs.dispose();
    super.dispose();
  }

  void _refresh() {
    ref.invalidate(devicesProvider);
    ref.invalidate(dailyReportProvider);
    ref.invalidate(dayEventsProvider);
    ref.invalidate(deviceTrackProvider);
    ref.invalidate(appUsageProvider);
  }

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final devices = ref.watch(devicesProvider).valueOrNull ?? const <Device>[];
    final device = devices.where((d) => d.id == _deviceId).firstOrNull;
    if (device == null) {
      return Scaffold(
        appBar: AppBar(),
        body: const Center(child: CircularProgressIndicator()),
      );
    }
    final today = ref
        .watch(dailyReportProvider((deviceId: device.id, date: isoDate(DateTime.now()))))
        .valueOrNull;
    final state = childState(device.lastSeenAt, today);
    return Scaffold(
      appBar: AppBar(
        title: devices.length > 1 ? _switcher(devices) : Text(device.title),
        centerTitle: true,
        actions: [
          IconButton(
            tooltip: t.commonRetry,
            icon: const Icon(Icons.refresh),
            onPressed: _refresh,
          ),
        ],
        bottom: TabBar(
          controller: _tabs,
          tabs: [
            Tab(text: t.childTabSummary),
            Tab(text: t.navMap),
            Tab(text: t.childTabTime),
            Tab(text: t.navSettings),
          ],
        ),
      ),
      body: Column(
        children: [
          _StatusBanner(device: device, state: state),
          Expanded(
            // Свайп вкладок выключен: на карте он мешал бы двигать карту.
            child: TabBarView(
              controller: _tabs,
              physics: const NeverScrollableScrollPhysics(),
              children: [
                DailyReportScreen(
                  key: ValueKey('summary-${device.id}'),
                  deviceId: device.id,
                  title: device.title,
                  embedded: true,
                ),
                ChildLocationScreen(
                  key: ValueKey('map-${device.id}'),
                  deviceId: device.id,
                  title: device.title,
                  embedded: true,
                ),
                AppUsageScreen(
                  key: ValueKey('time-${device.id}'),
                  deviceId: device.id,
                  title: device.title,
                  embedded: true,
                ),
                ChildSettingsScreen(
                  key: ValueKey('settings-${device.id}'),
                  deviceId: device.id,
                  title: device.title,
                  embedded: true,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _switcher(List<Device> devices) => Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          for (final d in devices)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 4),
              child: Tooltip(
                message: d.title,
                child: InkResponse(
                  radius: 28,
                  onTap: () => setState(() => _deviceId = d.id),
                  child: Semantics(
                    button: true,
                    selected: d.id == _deviceId,
                    label: d.title,
                    // Выбранный ребёнок крупнее, остальные — поменьше и бледнее.
                    child: AnimatedOpacity(
                      duration: const Duration(milliseconds: 250),
                      opacity: d.id == _deviceId ? 1 : 0.6,
                      child: ChildAvatar(
                        look: lookOf(devices, d.id),
                        initial: initialOf(d),
                        size: d.id == _deviceId ? 44 : 34,
                      ),
                    ),
                  ),
                ),
              ),
            ),
        ],
      );
}

/// Одна строка: что с ребёнком сейчас и почему.
class _StatusBanner extends StatelessWidget {
  const _StatusBanner({required this.device, required this.state});

  final Device device;
  final ChildState state;

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final look = StatusLook.of(Theme.of(context).colorScheme, state);
    final title = switch (state) {
      ChildState.calm => t.homeStatusCalm,
      ChildState.attention => t.homeStatusAttention,
      ChildState.offline => t.homeStatusOffline,
      ChildState.notPaired => t.homeStatusNotPaired,
    };
    final reason = state == ChildState.attention
        ? t.childAttentionReason
        : lastSeenText(t, device.lastSeenAt);
    return AnimatedContainer(
      duration: const Duration(milliseconds: 400),
      margin: const EdgeInsets.fromLTRB(16, 12, 16, 4),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(color: look.bannerBg, borderRadius: BorderRadius.circular(18)),
      child: Row(
        children: [
          Container(
            width: 10,
            height: 10,
            decoration: BoxDecoration(color: look.ring, shape: BoxShape.circle),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${device.title}: $title',
                  style: TextStyle(color: look.bannerInk, fontWeight: FontWeight.w700),
                ),
                Text(reason, style: TextStyle(color: look.bannerInk, fontSize: 13)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
