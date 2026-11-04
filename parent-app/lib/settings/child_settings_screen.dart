import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../children/add_child_screen.dart';
import '../children/app_limits_screen.dart';
import '../children/recovery_code_screen.dart';
import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';

/// Настройки одного ребёнка: ограничения времени, код восстановления пароля, новый телефон
/// и удаление ребёнка.
class ChildSettingsScreen extends ConsumerWidget {
  const ChildSettingsScreen({
    required this.deviceId,
    required this.title,
    this.embedded = false,
    super.key,
  });

  final String deviceId;
  final String title;

  /// Внутри вкладки экрана ребёнка: без своей шапки — её даёт экран ребёнка.
  final bool embedded;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = AppLocalizations.of(context);
    return Scaffold(
      appBar: embedded ? null : AppBar(title: Text(title)),
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
          const Divider(height: 1),
          ListTile(
            leading: const Icon(Icons.phonelink_setup),
            title: Text(t.reconnectTitle),
            subtitle: Text(t.reconnectSubtitle),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => AddChildScreen(reconnectDeviceId: deviceId),
              ),
            ),
          ),
          const Divider(height: 1),
          ListTile(
            leading: Icon(Icons.delete_outline, color: Theme.of(context).colorScheme.error),
            title: Text(
              t.deleteChildTitle,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
            subtitle: Text(t.deleteChildSubtitle),
            onTap: () => _delete(context, ref),
          ),
        ],
      ),
    );
  }

  Future<void> _delete(BuildContext context, WidgetRef ref) async {
    final t = AppLocalizations.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(t.deleteChildConfirmTitle(title)),
        content: Text(t.deleteChildConfirmBody),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(t.commonCancel),
          ),
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            style: TextButton.styleFrom(foregroundColor: Theme.of(dialogContext).colorScheme.error),
            child: Text(t.deleteChildConfirm),
          ),
        ],
      ),
    );
    if (confirmed != true || !context.mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    final navigator = Navigator.of(context);
    try {
      await ref.read(deviceRepositoryProvider).delete(deviceId);
      ref.invalidate(devicesProvider);
      // Экрана этого ребёнка больше нет: возвращаемся к списку семьи.
      navigator.popUntil((route) => route.isFirst);
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(errorText(t, e))));
    }
  }
}
