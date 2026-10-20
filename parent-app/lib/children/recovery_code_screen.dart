import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../core/error_text.dart';
import '../core/providers.dart';
import '../l10n/app_localizations.dart';
import 'device_repository.dart';

/// Код восстановления пароля MaxCare Kids: родитель забыл пароль на телефоне ребёнка.
/// Код получается при открытии экрана; новый — по кнопке (старый перестаёт действовать сам).
class RecoveryCodeScreen extends ConsumerStatefulWidget {
  const RecoveryCodeScreen({required this.deviceId, required this.title, super.key});

  final String deviceId;
  final String title;

  @override
  ConsumerState<RecoveryCodeScreen> createState() => _RecoveryCodeScreenState();
}

class _RecoveryCodeScreenState extends ConsumerState<RecoveryCodeScreen> {
  late Future<RecoveryCode> _code;

  @override
  void initState() {
    super.initState();
    _code = _fetch();
  }

  Future<RecoveryCode> _fetch() =>
      ref.read(deviceRepositoryProvider).recoveryCode(widget.deviceId);

  @override
  Widget build(BuildContext context) {
    final t = AppLocalizations.of(context);
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      body: ListView(
        padding: const EdgeInsets.all(24),
        children: [
          Text(t.recoveryTitle, style: theme.textTheme.titleLarge),
          const SizedBox(height: 24),
          FutureBuilder<RecoveryCode>(
            future: _code,
            builder: (context, snapshot) {
              if (snapshot.connectionState != ConnectionState.done) {
                return const Center(child: CircularProgressIndicator());
              }
              if (snapshot.hasError) {
                return Text(
                  errorText(t, snapshot.error!),
                  style: TextStyle(color: theme.colorScheme.error),
                );
              }
              final code = snapshot.requireData;
              final until = DateFormat.Hm().format(code.validUntil.toLocal());
              return Column(
                children: [
                  SelectableText(
                    code.grouped,
                    style: theme.textTheme.displaySmall?.copyWith(letterSpacing: 4),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 8),
                  Text(t.recoveryValidUntil(until), textAlign: TextAlign.center),
                ],
              );
            },
          ),
          const SizedBox(height: 24),
          Text(t.recoveryHowTo),
          const SizedBox(height: 24),
          OutlinedButton(
            onPressed: () => setState(() => _code = _fetch()),
            child: Text(t.recoveryNewCode),
          ),
        ],
      ),
    );
  }
}
