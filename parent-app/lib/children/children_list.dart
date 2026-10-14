import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'device_repository.dart';
import 'last_seen.dart';

/// Список телефонов детей с их статусом. Нажатие на ребёнка задаёт [onTap]:
/// на вкладке «Главная» — журнал, на «Отчётах» — хаб отчёта.
class ChildrenList extends ConsumerWidget {
  const ChildrenList({required this.onTap, super.key});

  final void Function(BuildContext context, Device device) onTap;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = AppLocalizations.of(context);
    final devices = ref.watch(devicesProvider);
    return RefreshIndicator(
      onRefresh: () => ref.refresh(devicesProvider.future),
      child: devices.when(
        data: (list) => _list(context, t, list),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _error(context, t, ref, error),
      ),
    );
  }

  Widget _list(BuildContext context, AppLocalizations t, List<Device> list) {
    if (list.isEmpty) return _empty(t);
    return ListView.separated(
      padding: const EdgeInsets.symmetric(vertical: 8),
      itemCount: list.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (context, i) => _tile(context, t, list[i]),
    );
  }

  Widget _tile(BuildContext context, AppLocalizations t, Device device) => ListTile(
        leading: CircleAvatar(
          child: Icon(
            device.platform == 'ios' ? Icons.phone_iphone : Icons.phone_android,
          ),
        ),
        title: Text(device.title),
        subtitle: Text(lastSeenText(t, device.lastSeenAt)),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => onTap(context, device),
      );

  // ListView, чтобы пустое состояние тоже тянулось для pull-to-refresh.
  Widget _empty(AppLocalizations t) => ListView(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 96, 24, 24),
            child: Text(t.homeEmpty, textAlign: TextAlign.center),
          ),
        ],
      );

  Widget _error(BuildContext context, AppLocalizations t, WidgetRef ref, Object error) =>
      ListView(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(24, 96, 24, 16),
            child: Text(errorText(t, error), textAlign: TextAlign.center),
          ),
          Center(
            child: OutlinedButton(
              onPressed: () => ref.invalidate(devicesProvider),
              child: Text(t.commonRetry),
            ),
          ),
        ],
      );
}
