import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'child_location_screen.dart';
import 'device_repository.dart';
import 'event_label.dart';
import 'last_seen.dart';

/// Журнал событий одного телефона ребёнка.
class DeviceEventsScreen extends ConsumerWidget {
  const DeviceEventsScreen({required this.deviceId, required this.title, super.key});

  final String deviceId;
  final String title;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = AppLocalizations.of(context);
    final activity = ref.watch(deviceActivityProvider(deviceId));
    return Scaffold(
      appBar: AppBar(
        title: Text(title),
        actions: [
          IconButton(
            tooltip: t.locationOpen,
            icon: const Icon(Icons.location_on_outlined),
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => ChildLocationScreen(deviceId: deviceId, title: title),
              ),
            ),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(deviceActivityProvider(deviceId).future),
        child: activity.when(
          data: (data) => _body(context, t, data),
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, _) => _error(context, t, ref, error),
        ),
      ),
    );
  }

  Widget _body(BuildContext context, AppLocalizations t, DeviceActivity data) {
    final header = Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Text(
        lastSeenText(t, data.lastSeenAt),
        style: Theme.of(context).textTheme.bodySmall,
      ),
    );
    if (data.events.isEmpty) {
      return ListView(
        children: [
          header,
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 72, 24, 24),
            child: Text(t.eventsEmpty, textAlign: TextAlign.center),
          ),
        ],
      );
    }
    return ListView.separated(
      itemCount: data.events.length + 1,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (context, i) =>
          i == 0 ? header : _tile(context, t, data.events[i - 1]),
    );
  }

  Widget _tile(BuildContext context, AppLocalizations t, DeviceEvent event) {
    final when = DateFormat.yMMMd().add_Hm().format(event.occurredAt.toLocal());
    return ListTile(
      leading: Icon(_icon(event.type), color: _dangerous(event.type)
          ? Theme.of(context).colorScheme.error
          : null),
      title: Text(eventLabel(t, event)),
      subtitle: Text(when),
      isThreeLine: false,
    );
  }

  Widget _error(BuildContext context, AppLocalizations t, WidgetRef ref, Object error) =>
      ListView(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 96, 24, 16),
            child: Text(errorText(t, error), textAlign: TextAlign.center),
          ),
          Center(
            child: OutlinedButton(
              onPressed: () => ref.invalidate(deviceActivityProvider(deviceId)),
              child: Text(t.commonRetry),
            ),
          ),
        ],
      );
}

bool _dangerous(String type) => switch (type) {
      'admin_disabled' ||
      'protection_disabled' ||
      'password_lockout' ||
      'notifications_off' ||
      'vpn_off' ||
      'private_dns_on' ||
      'site_blocked_filtered' ||
      'site_blocked_gambling' ||
      'search_blocked' ||
      'text_flagged_messenger' ||
      'image_blocked_nudity' ||
      'image_blocked_violence' ||
      'uninstall_attempt_blocked' ||
      'accessibility_off' =>
        true,
      _ => false,
    };

IconData _icon(String type) => switch (type) {
      'admin_disabled' || 'protection_disabled' => Icons.gpp_bad,
      'password_lockout' => Icons.lock_clock,
      'password_recovered' => Icons.lock_reset,
      'notifications_off' => Icons.notifications_off,
      'device_booted' => Icons.power_settings_new,
      'device_shutdown' => Icons.power_off,
      'vpn_off' || 'private_dns_on' => Icons.vpn_key_off,
      'site_blocked_filtered' || 'site_blocked_gambling' => Icons.block,
      'search_blocked' => Icons.search_off,
      'text_flagged_messenger' => Icons.chat_bubble_outline,
      'image_blocked_nudity' || 'image_blocked_violence' => Icons.image_not_supported,
      'uninstall_attempt_blocked' => Icons.delete_forever,
      'accessibility_off' => Icons.visibility_off,
      _ => Icons.info_outline,
    };
